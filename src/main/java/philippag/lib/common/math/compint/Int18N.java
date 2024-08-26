/*
MIT License

Copyright (c) 2024 Philipp Grasboeck

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package philippag.lib.common.math.compint;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.function.Supplier;

import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitArraySink;
import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitStreamable;

/**
 * Big Integer implementation using base 10^9.
 *
 * The idea of using base 1E9 as base is inspired by Michael Grieco's video series
 * "C/C++ Math Library", episode 7 "Big Integer Generic Bases"
 * https://www.youtube.com/watch?v=_S_iRJ-vHgo&list=PLysLvOneEETMjrK5N-PLIYhZKwmxjGs2-&index=7
 *
 * We can safely perform "999_999_999 + 999_999_999" without int overflow.
 * We can safely perform "999_999_999 * 999_999_999" without long overflow.
 *
 * Each instance returned by any of the methods is always mutable.
 * This is done for performance, to be able to use instances as accumulators.
 * However, this means we can't have reused constants for ZERO, ONE et al.
 *
 * Note: if this class were ported to a language that has int128,
 * we could change one digit to be 1E18 instead 1E9. (`Int18`)
 * I expect quite a bit of performance increase from this,
 * plus we could in theory represent larger numbers, if that language
 * were to support arrays (or char sequences / byte array slices)
 * above 1 << 31.
 *
 * This class lacks the usual random access to bits, and logical operations,
 * but instead offers random access to decimal digits.
 *
 * TODO...
 */
public final class Int18N implements Comparable<Int18N>, AsciiDigitStreamable, CharSequence {

    private static final long BASE = 1_000000000_000000000L; // 1E18
    private static final long HALF_BASE = BASE / 2;
    private static final long DOUBLE_BASE = BASE * 2;
    private static final int SIZE = 18;
    private static final int KARATSUBA_THRESHOLD = 40;

    // never return to user!
    private static final Int18N ZERO     = Constants.ZERO();
    private static final Int18N ONE      = Constants.ONE();
    private static final Int18N INT_MAX  = Constants.INT_MAX();
    private static final Int18N INT_MIN  = Constants.INT_MIN();
    private static final Int18N LONG_MAX = Constants.LONG_MAX();
    private static final Int18N LONG_MIN = Constants.LONG_MIN();

    private static class Constants {

        private static Int18N ZERO()     { return new Int18N(0); }
        private static Int18N ONE()      { return new Int18N(1); }
        private static Int18N INT_MAX()  { return new Int18N(2147483647L); }
        private static Int18N INT_MIN()  { return new Int18N(2147483648L).setNegative(true); }
        private static Int18N LONG_MAX() { return new Int18N(9, 223372036854775807L); }
        private static Int18N LONG_MIN() { return new Int18N(9, 223372036854775808L).setNegative(true); }
    }

    private boolean negative;
    private byte firstDigitLength; // cached value
    private long[] data; // integers from 000_000_000 to 999_999_999
    private int offset;
    private int length;

    //@VisibleForTesting
    Int18N(long... data) {
        this(data, 0, data.length);
    }

    private Int18N(long[] data, int offset, int length) {
        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("zero or negative length: " + length);
        }
        this.data = Objects.requireNonNull(data, "data");
        this.offset = offset;
        this.length = length;
    }

    public boolean isZero() {
        assert data[offset] > 0 || length == 1; // canonical form
        return data[offset] == 0;
    }

    public Int18N setNegative(boolean b) {
        negative = b && !isZero();
        return this;
    }

    @Override
    public boolean isNegative() {
        return negative;
    }

    public Int18N negate() {
        return copy().setNegative(!negative);
    }

    //does not include sign
    @Override
    public int countDigits() {
        return firstDigitLength() + DivMulTable.mul18(length - 1);
    }

    //does not include sign
    @Override
    public boolean stream(AsciiDigitArraySink sink) {
        byte[] dest = new byte[SIZE];
        long first = get(0);
        int m = SIZE - firstDigitLength();

        IntegerFormat.format(dest, first, 0, SIZE);
        if (!sink.accept(dest, m, SIZE - m)) {
            return false;
        }

        for (int i = 1; i < length; i++) {
            IntegerFormat.format(dest, get(i), 0, SIZE);
            if (!sink.accept(dest, 0, SIZE)) {
                return false;
            }
        }
        return true;
    }

    public byte[] toByteArray(boolean includeSign) {
        boolean emitNegativeSign = includeSign && negative;
        byte[] dest = new byte[emitNegativeSign ? 1 + countDigits() : countDigits()];
        int right = dest.length;

        for (int i = length - 1; i >= 0; --i) {
            int left = Math.max(0, right - SIZE);
            IntegerFormat.format(dest, get(i), left, right);
            right = left;
        }
        if (emitNegativeSign) {
            dest[0] = '-';
        }

        return dest;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        return new String(toByteArray(/*includeSign*/ true), 0);
    }

    public String toDebugString() {
        var sb = new StringBuilder();

        sb.append("Int16N");
        sb.append(" {digits=").append(countDigits());
        sb.append(", negative=").append(negative);
        sb.append(", offset=").append(offset);
        sb.append(", length=").append(length);
        sb.append(", capacity=").append(data.length);
        sb.append(", data=").append(toArrayString());
        sb.append("}");

        return sb.toString();
    }

    public String toArrayString() {
        var sb = new StringBuilder();

        sb.append("[");
        String sep = "";
        for (int i = offset, max = extent(); i < max; i++) {
            sb.append(sep).append(data[i]);
            sep = ", ";
        }
        sb.append("]");

        return sb.toString();
    }

    private int extent() {
        return Math.min(offset + length, data.length);
    }

    private boolean trailingZeroesForm() {
        return offset + length > data.length;
    }

    public static Int18N fromScientific(CharSequence str) {
        return fromString(AsciiDigits.fromScientific(str));
    }

    public CharSequence toScientific(int precision) {
        return AsciiDigits.toScientific(this, precision);
    }

    private void expandWith(long value) {
        assert offset > 0;
        data[--offset] = value;
        length++;
    }

    private void expandBy(int by) {
        assert by >= 0;
        if (by == 0) {
            return;
        }
        offset -= by;
        assert offset >= 0;
        length += by;
    }

    private Int18N shiftLeft(int by) {
        if (!isZero()) {
            length += by;
        }
        return this;
    }

    private long get(int idx) {
        assert 0 <= idx && idx < length;
        int index = offset + idx;
        return index < data.length ? data[index] : 0;
    }

    /*
     * This enforces these invariants:
     * - offset must always point to a non-zero number, except for ZERO
     * - no representation of ZERO is ever negative
     */
    private Int18N canonicalize() {
        int i = offset;
        int max = extent() - 1;
        while (i < max && data[i] == 0) {
            i++;
        }

        length = length - i + offset;
        offset = i;
        firstDigitLength = 0;

        if (isZero()) {
            negative = false; // can happen e.g. -1.addInPlace(1) => 0
        }
        return this;
    }

    private int firstDigitLength() {
        int result = firstDigitLength;
        return result == 0 ? firstDigitLength = (byte) IntegerFormat.length(data[offset]) : result;
    }

    /* ===============
     * factory methods
     * ===============
     */

    public static Int18N forDigits(int digits) {
        int length = Calc.lengthForDigits(digits);
        return new Int18N(new long[length], length - 1, 1);
    }

    public static Int18N fromInt(int value) {
        if (value == Integer.MIN_VALUE) {
            return Constants.INT_MIN();
        }
        boolean negative = false;
        if (value < 0) {
            negative = true;
            value = -value; // doesn't work for Integer.MIN_VALUE
        }
        return fromIntAbs(value).setNegative(negative);
    }

    private static Int18N fromIntAbs(int value) {
        assert value >= 0;
        assert value < BASE;
        return new Int18N(value);
//        if (value < BASE) {
//        } else if (value < DOUBLE_BASE) {
//            return new Int16N(1, value - BASE);
//        } else {
//            return new Int16N(2, value - DOUBLE_BASE);
//        }
    }

    public static Int18N fromLong(long value) {
        if (value == Long.MIN_VALUE) {
            return Constants.LONG_MIN();
        }
        boolean negative = false;
        if (value < 0) {
            negative = true;
            value = -value; // doesn't work for Long.MIN_VALUE
        }
        return fromLongAbs(value).setNegative(negative);
    }

    private static Int18N fromLongAbs(long value) {
        assert value >= 0 : value;
        return switch (Calc.lengthOf(value)) {
            case 1  -> new Int18N(value);
            case 2  -> new Int18N(value / BASE, value % BASE);
            default -> throw new Error("UNREACHABLE:"+Calc.lengthOf(value));
        };
    }

    public static Int18N fromString(CharSequence str) {
        return fromString(str, 0, str.length());
    }

    public static Int18N fromString(CharSequence str, int fromIndex, int toIndex) {
        if (fromIndex >= toIndex) {
            throw new NumberFormatException("fromIndex >= toIndex");
        }

        char c = str.charAt(fromIndex);
        boolean negative = false;
        if (c == '-') {
            negative = true;
            fromIndex++;
        } else if (c == '+') {
            fromIndex++;
        }

        while (fromIndex < toIndex - 1 && str.charAt(fromIndex) == '0') {
            fromIndex++;
        }

        int digits = toIndex - fromIndex;
        if (digits == 0) {
            throw new NumberFormatException("No digits in input string");
        }
        int length = Calc.lengthForDigits(digits);
        long[] result = null;

        if(1==1) result = new long[length]; //XXX

        for (int i = toIndex, j = length - 1; j >= 0; --j) {
            int end = i;
            i = Math.max(fromIndex, i - SIZE);
            long value = IntegerFormat.parse(str, i, end);
            if (value > 0 && result == null) {
                result = new long[j + 1];  // "trailingZeroesForm" storage optimization: alloc only for non-zero digits at the right
            }
            if (result != null) {
                result[j] = value;
            }
        }

        return result == null ? Constants.ZERO() : new Int18N(result, 0, length).setNegative(negative);
    }

    public boolean isInt() {
        return compareToAbs(negative ? INT_MIN : INT_MAX) <= 0;
    }

    public int toInt() {
        if (compareToAbs(INT_MAX) > 0) {
            // we use MIN_VALUE to represent unmappable values
            return Integer.MIN_VALUE;
        }
        return negative ? -toIntAbs() : toIntAbs();
    }

    private int toIntAbs() {
        assert length == 1;
        return (int) get(0);
    }

    public boolean isLong() {
        return compareToAbs(negative ? LONG_MIN : LONG_MAX) <= 0;
    }

    public long toLong() {
        if (compareToAbs(LONG_MAX) > 0) {
            // we use MIN_VALUE to represent unmappable values
            return Long.MIN_VALUE;
        }
        return negative ? -toLongAbs() : toLongAbs();
    }

    private long toLongAbs() {
        assert length <= 2;
        if (length == 1) {
            return get(0);
        } else {
//            return BASE * get(0) + get(1);
            return Math.multiplyExact(BASE, get(0)) + get(1);//XXX
        }
    }

    public int compareTo(long o) {
        int cmp = -Boolean.compare(negative, o < 0);
        return cmp != 0 ? cmp : negative ? -compareToAbs(o) : compareToAbs(o);
    }

    public int compareToAbs(long o) {
        return o == Long.MIN_VALUE ? compareToAbs(LONG_MIN) : compareToAbsImpl(Math.abs(o));
    }

    private int compareToAbsImpl(long o) {
        assert o >= 0;
        return compareToAbs(LONG_MAX) <= 0 ? Long.compare(toLongAbs(), o) : 1; // we're automatically bigger when out of positive long range
    }

    @Override
    public int compareTo(Int18N o) {
        int cmp = -Boolean.compare(negative, o.negative);
        return cmp != 0 ? cmp : negative ? -compareToAbs(o) : compareToAbs(o);
    }

    public int compareToAbs(Int18N o) {
        if (this == o) {
            return 0;
        }
        int cmp = Integer.compare(length, o.length);
        if (cmp != 0) {
            return cmp;
        }
        for (int i = 0; i < length; i++) {
            cmp = Long.compare(get(i), o.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public boolean equals(Int18N o) {
        return 0 == compareTo(o);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Int18N o && equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode(); // that's ok
    }

    public void clear() {
        offset = data.length - 1; // make all space at the left available
        length = 1;
        data[offset] = 0;
        negative = false;
        firstDigitLength = 0;
    }

    public void setValue(Int18N rhs) {
        int newLength = rhs.length;
        if (data.length < newLength) {
            data = new long[newLength];
        }
        int newOffset = data.length - newLength;
        int copyLength = rhs.extent() - rhs.offset;
        System.arraycopy(rhs.data, rhs.offset, data, newOffset, copyLength);
        Arrays.fill(data, newOffset + copyLength, data.length, 0);

        offset = newOffset;
        length = newLength;
        negative = rhs.negative;
        firstDigitLength = 0;
    }

    public void setValue(long rhs) {
        if (rhs == Long.MIN_VALUE) {
            setValue(LONG_MIN);
            return;
        } else if (rhs == 0) {
            clear();
            return;
        }

        if (negative = rhs < 0) {
            rhs = -rhs;
        }
        assert rhs > 0;

        int newLength = Calc.lengthOf(rhs);
        if (newLength > data.length) {
            data = new long[newLength];
        }

        offset = data.length - newLength;
        length = newLength;
        firstDigitLength = 0;

        int i = offset + length - 1;
        data[i] = (int) (rhs % BASE);

        while (rhs >= BASE) {
            rhs /= BASE;
            data[--i] = (int) (rhs % BASE);
        }
    }

    private long[] copyFullSizeArray() {
        // undo "trailingZeroesForm" storage optimization
        long[] newData = new long[length];
        System.arraycopy(data, offset, newData, 0, Math.min(length, data.length));
        return newData;
    }

    public Int18N copy() {
        // copy only relevant region
        // does not undo "trailingZeroesForm" storage optimization
        return new Int18N(Arrays.copyOfRange(data, offset, extent()), 0, length).setNegative(negative);
    }

    private Int18N copyFullSize() {
        return new Int18N(copyFullSizeArray()).setNegative(negative);
    }

    private Int18N copyDoubleSize() {
        int newOffset = length << 1;
        long[] newData = new long[newOffset + length];
        System.arraycopy(data, offset, newData, newOffset, Math.min(length, data.length));
        return new Int18N(newData, newOffset, length).setNegative(negative);
    }

    private void ensureCapacity(int minOffset) {
        // Note: this might do 2 resizes, we could collapse them into one,
        // though both resizes happening is not very likely
        if (trailingZeroesForm()) {
            data = copyFullSizeArray();
        }
        if (offset < minOffset) {
            resize(minOffset);
        }
    }

    private void resize(int newOffset) {
        long[] newData = new long[data.length + newOffset];
        System.arraycopy(data, offset, newData, newOffset, length);
        offset = newOffset;
        data = newData;
    }

    private void carryRest(long accumulator, int i) {
        long carry = AddWithCarry.carry(accumulator);
        assert carry > 0;

        for (; i >= offset && carry > 0; --i) {
            accumulator = data[i] + carry;
            data[i] = AddWithCarry.value(accumulator);
            carry = AddWithCarry.carry(accumulator);
        }

        if (carry > 0) {
            expandWith(carry);
        }
    }

    /* =================
     * in-place mutators
     * =================
     */

    public Int18N addInPlace(Int18N rhs) {
        if (rhs.isZero()) {
            return this;
        }
        if (negative == rhs.negative) {
            // a1: (-3) + (-4) = -7
            // a2: (+3) + (+4) = +7
            // b1: (-4) + (-3) = -7
            // b2: (+4) + (+3) = +7
            ensureCapacity(Calc.addInPlaceCapacity(length, rhs.length));
            addInPlaceAbs(rhs);
        } else {
            // a1: (-3) + (+4) = +1
            // a2: (+3) + (-4) = -1
            // b1: (-4) + (+3) = -1
            // b2: (+4) + (-3) = +1
            int cmp = compareToAbs(rhs);
            ensureCapacity(Calc.subtractInPlaceCapacity(length, rhs.length));
            if (cmp < 0) { // a1, a2
                subtractInPlaceAbsLessThan(rhs);
                setNegative(!negative);
            } else if (cmp > 0) { // b1, b2
                subtractInPlaceAbsGreaterEqual(rhs);
            } else {
                clear();
            }
        }
        return this;
    }

    private void addInPlaceAbs(Int18N rhs) {
        if (length >= rhs.length) {
            addInPlaceAbsLongerEqual(rhs);
        } else {
            addInPlaceAbsShorter(rhs);
        }
        canonicalize();
    }

    // beware: caller must call canonicalize()!
    private void addInPlaceAbsShorter(Int18N rhs) {
        long accumulator = 0;
        int i = offset + length - 1;
        int j = rhs.length - 1;

        for (; i >= offset; --j, --i) {
            accumulator = data[i] + rhs.get(j) + AddWithCarry.carry(accumulator);
            data[i] = AddWithCarry.value(accumulator);
        }
        expandBy(j + 1);
        for (; j >= 0; --j, --i) {
            accumulator = rhs.get(j) + AddWithCarry.carry(accumulator);
            data[i] = AddWithCarry.value(accumulator);
        }

        if (AddWithCarry.carry(accumulator) > 0) {
            carryRest(accumulator, i);
        }
    }

    // beware: caller must call canonicalize()!
    private void addInPlaceAbsLongerEqual(Int18N rhs) {
        addInPlaceAbsLongerEqualCore(rhs.data, rhs.offset, rhs.length);
    }

    private void addInPlaceAbsLongerEqualCore(long[] rhs, int rhsOffset, int rhsLength) {
        assert length >= rhsLength;

        long accumulator = 0;
        int rhsMax = rhsOffset + rhsLength;
        int i = offset + length - 1;

        // fix coordinates for "trailingZeroesForm"
        if (rhsMax > rhs.length) {
            i -= rhsMax - rhs.length;
            assert i >= 0;
            rhsMax = rhs.length;
        }

        for (int j = rhsMax - 1; j >= rhsOffset; --j, --i) {
            accumulator = data[i] + rhs[j] + AddWithCarry.carry(accumulator);
            data[i] = AddWithCarry.value(accumulator);
        }

        if (AddWithCarry.carry(accumulator) > 0) {
            carryRest(accumulator, i);
        }
    }

    public Int18N addInPlace(long rhs) {
        if (rhs == Long.MIN_VALUE) {
            // we can't do absolute (i.e. positive) arithmetic with LONG_MIN
            addInPlace(LONG_MIN);
            return this;
        }
        if (rhs == 0) {
            return this;
        }
        boolean rhsNegative = rhs < 0;
        rhs = Math.abs(rhs);
        assert rhs > 0;

        if (negative == rhsNegative) {
            // a1: (-3) + (-4) = -7
            // a2: (+3) + (+4) = +7
            // b1: (-4) + (-3) = -7
            // b2: (+4) + (+3) = +7
            ensureCapacity(Calc.addInPlaceCapacity(length, Calc.lengthOf(rhs)));
            addInPlaceAbs(rhs);
        } else {
            // a1: (-3) + (+4) = +1
            // a2: (+3) + (-4) = -1
            // b1: (-4) + (+3) = -1
            // b2: (+4) + (-3) = +1
            int cmp = compareToAbsImpl(rhs);
            ensureCapacity(Calc.subtractInPlaceCapacity(length, Calc.lengthOf(rhs)));
            if (cmp < 0) { // a1, a2
                subtractInPlaceAbsLessThan(rhs);
                setNegative(!negative);
            } else if (cmp > 0) { // b1, b2
                subtractInPlaceAbsGreaterEqual(rhs);
            } else {
                clear();
            }
        }
        return this;
    }

    private void addInPlaceAbs(long rhs) {
        assert rhs > 0;

        int i = offset + length - 1;
        long accumulator = data[i] + rhs % BASE;
        data[i] = AddWithCarry.value(accumulator);

        while (rhs >= BASE) {
            rhs /= BASE;
            if (--i >= offset) {
                accumulator = data[i] + rhs % BASE + AddWithCarry.carry(accumulator);
                data[i] = AddWithCarry.value(accumulator);
            } else {
                accumulator = rhs % BASE + AddWithCarry.carry(accumulator);
                expandWith(AddWithCarry.value(accumulator));
            }
        }

        if (AddWithCarry.carry(accumulator) > 0) {
            carryRest(accumulator, i - 1);
        }
        canonicalize();
    }

    public Int18N subtractInPlace(Int18N rhs) {
        if (rhs.isZero()) {
            return this;
        }
        if (negative == rhs.negative) {
            // a1: (-3) - (-4) = +1
            // a2: (+3) - (+4) = -1
            // b1: (-4) - (-3) = -1
            // b2: (+4) - (+3) = +1
            int cmp = compareToAbs(rhs);
            ensureCapacity(Calc.subtractInPlaceCapacity(length, rhs.length));
            if (cmp < 0) { // a1, a2
                subtractInPlaceAbsLessThan(rhs);
                setNegative(!negative);
            } else if (cmp > 0) { // b1, b2
                subtractInPlaceAbsGreaterEqual(rhs);
            } else {
                clear();
            }
        } else {
            // a1: (-3) - (+4) = -7
            // a2: (+3) - (-4) = +7
            // b1: (-4) - (+3) = -7
            // b2: (+4) - (-3) = +7
            ensureCapacity(Calc.addInPlaceCapacity(length, rhs.length));
            addInPlaceAbs(rhs);
        }
        return this;
    }

    private void subtractInPlaceAbsGreaterEqual(Int18N rhs) {
        assert compareToAbs(rhs) >= 0; // we are the bigger number or equal
        subtractInPlaceAbsGreaterEqualCore(rhs.data, rhs.offset, rhs.length);
    }

    private void subtractInPlaceAbsGreaterEqualCore(long[] rhs, int rhsOffset, int rhsLength) {
        long accumulator = 0;
        int rhsMax = rhsOffset + rhsLength;
        int i = offset + length - 1;

        // fix coordinates for "trailingZeroesForm"
        if (rhsMax > rhs.length) {
            i -= rhsMax - rhs.length;
            assert i >= 0;
            rhsMax = rhs.length;
        }

        for (int j = rhsMax - 1; j >= rhsOffset; --i, --j) {
            accumulator = data[i] - rhs[j] + SubtractWithCarry.carry(accumulator);
            data[i] = SubtractWithCarry.value(accumulator);
        }
        for (; i >= offset; --i) {
            accumulator = data[i] + SubtractWithCarry.carry(accumulator);
            data[i] = SubtractWithCarry.value(accumulator);
        }

        assert SubtractWithCarry.carry(accumulator) == 0;
        canonicalize();
    }

    private void subtractInPlaceAbsLessThan(Int18N rhs) {
        assert length <= rhs.length;
        assert compareToAbs(rhs) < 0; // we are the smaller number

        long accumulator = 0;
        int i = offset + length - 1;
        int j = rhs.length - 1;

        for (; i >= offset; --j, --i) {
            accumulator = data[i] - rhs.get(j) + SubtractWithCarryComplement.carry(accumulator);
            data[i] = SubtractWithCarryComplement.value(accumulator);
        }
        expandBy(j + 1);
        for (; j >= 0; --j, --i) {
            accumulator = -rhs.get(j) + SubtractWithCarryComplement.carry(accumulator);
            data[i] = SubtractWithCarryComplement.value(accumulator);
        }

        assert SubtractWithCarryComplement.carry(accumulator) == 0;
        canonicalize();
    }

    public Int18N subtractInPlace(long rhs) {
        if (rhs == Long.MIN_VALUE) {
            // we can't do absolute (i.e. positive) arithmetic with LONG_MIN
            subtractInPlace(LONG_MIN);
            return this;
        } else if (rhs == 0) {
            return this;
        }
        boolean rhsNegative = rhs < 0;
        rhs = Math.abs(rhs);
        assert rhs > 0;

        if (negative == rhsNegative) {
            // a1: (-3) + (-4) = -7
            // a2: (+3) + (+4) = +7
            // b1: (-4) + (-3) = -7
            // b2: (+4) + (+3) = +7
            int cmp = compareToAbsImpl(rhs);
            ensureCapacity(Calc.subtractInPlaceCapacity(length, Calc.lengthOf(rhs)));
            if (cmp < 0) { // a1, a2
                subtractInPlaceAbsLessThan(rhs);
                setNegative(!negative);
            } else if (cmp > 0) { // b1, b2
                subtractInPlaceAbsGreaterEqual(rhs);
            } else {
                clear();
            }
        } else {
            // a1: (-3) - (+4) = -7
            // a2: (+3) - (-4) = +7
            // b1: (-4) - (+3) = -7
            // b2: (+4) - (-3) = +7
            ensureCapacity(Calc.addInPlaceCapacity(length, Calc.lengthOf(rhs)));
            addInPlaceAbs(rhs);
        }
        return this;
    }

    private void subtractInPlaceAbsGreaterEqual(long rhs) {
        assert rhs > 0;
        assert compareToAbsImpl(rhs) >= 0; // we are the bigger number or equal

        long accumulator = 0;
        int i = offset + length - 1;

        for (; rhs > 0; --i) {
            accumulator = data[i] - (int) (rhs % BASE) + SubtractWithCarry.carry(accumulator);
            data[i] = SubtractWithCarry.value(accumulator);
            rhs /= BASE;
        }

        for (; i >= offset; --i) {
            accumulator = data[i] + SubtractWithCarry.carry(accumulator);
            data[i] = SubtractWithCarry.value(accumulator);
        }

        assert SubtractWithCarry.carry(accumulator) == 0;
        canonicalize();
    }

    private void subtractInPlaceAbsLessThan(long rhs) {
        assert rhs > 0;
        assert compareToAbsImpl(rhs) < 0; // we are the smaller number

        long accumulator = 0;
        int i = offset + length - 1;

        for (; i >= offset; --i) {
            accumulator = data[i] - (int) (rhs % BASE) + SubtractWithCarryComplement.carry(accumulator);
            data[i] = SubtractWithCarryComplement.value(accumulator);
            rhs /= BASE;
        }

        while (rhs > 0) {
            accumulator =  -(int) (rhs % BASE) + SubtractWithCarryComplement.carry(accumulator);
            expandWith(SubtractWithCarryComplement.value(accumulator));
            rhs /= BASE;
        }

        assert SubtractWithCarryComplement.carry(accumulator) == 0;
        canonicalize();
    }

    public Int18N incrementInPlace() {
        ensureCapacity(0);
        if (negative) {
            decrementInPlaceAbs();
        } else {
            incrementInPlaceAbs();
        }
        return this;
    }

    public Int18N decrementInPlace() {
        ensureCapacity(0);
        if (negative) {
            incrementInPlaceAbs();
        } else {
            decrementInPlaceAbs();
        }
        return this;
    }

    private void incrementInPlaceAbs() {
        int idx = offset + length - 1;
        if (data[idx] + 1 == BASE ) { // carry case
            ensureCapacity(Calc.addInPlaceCapacity(length, 1));
            addInPlaceAbs(1);
        } else { // no carry
            data[idx]++;
        }
        canonicalize();
    }

    private void decrementInPlaceAbs() {
        int idx = offset + length - 1;
        if (data[idx] == 0) { // carry case
            if (length == 1) { // we are zero
                data[idx] = 1;
                negative = true;
            } else {
                // every other number is greater equal to one (in abs)
                subtractInPlaceAbsGreaterEqual(1);
            }
        } else { // no carry
            assert data[idx] > 0;
            data[idx]--;
        }
        canonicalize();
    }

    /* =======================================
     * convenience functional instance methods
     * =======================================
     */

    public Int18N add(Int18N rhs) {
        return add(this, rhs);
    }

    public Int18N subtract(Int18N rhs) {
        return subtract(this, rhs);
    }

    public Int18N divide(int divisor) {
        var copy = copy();
        copy.divideInPlace(divisor);
        return copy;
    }

    public int modulo(int divisor) {
        return copy().divideInPlace(divisor);
    }

    public Int18N multiply(Int18N rhs) {
        var pool = forkJoinPool;
        return pool == null ? multiplyKaratsuba(this, rhs) : parallelMultiplyKaratsuba(this, rhs, pool);
    }

    public Int18N pow(int exponent) {
        var pool = forkJoinPool;
        return pool == null ? pow(this, exponent) : parallelPow(this, exponent, pool);
    }

    private static ForkJoinPool forkJoinPool;

    public static void setForkJoinPool(ForkJoinPool pool) {
        forkJoinPool = pool;
    }

    /* ============================
     * static functional arithmetic
     * ============================
     */

    public static Int18N add(Int18N lhs, Int18N rhs) {
        if (lhs.negative == rhs.negative) { // equal signs => addition
            return addAbs(lhs, rhs).setNegative(lhs.negative);
        } else { // opposite signs => subtraction
            return lhs.negative ? subtractForward(rhs, lhs) : subtractForward(lhs, rhs);
        }
    }

    private static Int18N addAbs(Int18N lhs, Int18N rhs) {
        if (lhs.isZero()) {
            return rhs.copy();
        } else if (rhs.isZero()) {
            return lhs.copy();
        } else if (lhs.length >= rhs.length) {
            return addAbsLongerEqual(lhs, rhs);
        } else {
            return addAbsLongerEqual(rhs, lhs);
        }
    }

    private static Int18N addAbsLongerEqual(Int18N lhs, Int18N rhs) {
        assert lhs.length >= rhs.length;

        long[] result = new long[1 + lhs.length];  // always need one more space for 999_999_999 + 1 case!
        long accumulator = 0;
        int i = lhs.length - 1;

        for (int j = rhs.length - 1; j >= 0; --i, --j) {
            accumulator = lhs.get(i) + rhs.get(j) + AddWithCarry.carry(accumulator);
            result[1 + i] = AddWithCarry.value(accumulator);
        }
        for (; i >= 0; --i) {
            accumulator = lhs.get(i) + AddWithCarry.carry(accumulator);
            result[1 + i] = AddWithCarry.value(accumulator);
        }
        result[0] = AddWithCarry.carry(accumulator);

        return new Int18N(result).canonicalize();
    }

    public static Int18N subtract(Int18N lhs, Int18N rhs) {
        int cmp = lhs.compareToAbs(rhs);
        if (lhs.negative == rhs.negative) { // equal signs => subtraction
            return cmp == 0 ? Constants.ZERO() : subtractForward(cmp, lhs, rhs).setNegative(lhs.negative ? cmp > 0 : cmp < 0);
        } else { // opposite signs => addition
            return addAbs(lhs, rhs).setNegative(lhs.negative);
        }
    }

    private static Int18N subtractForward(Int18N lhs, Int18N rhs) {
        int cmp = lhs.compareToAbs(rhs);
        return cmp == 0 ? Constants.ZERO() : subtractForward(cmp, lhs, rhs).setNegative(cmp < 0);
    }

    private static Int18N subtractForward(int cmp, Int18N lhs, Int18N rhs) {
        assert cmp != 0; // callers should rule out ZERO
        return cmp >= 0 ? subtractAbsGreaterEqual(lhs, rhs) : subtractAbsGreaterEqual(rhs, lhs);
    }

    private static Int18N subtractAbsGreaterEqual(Int18N lhs, Int18N rhs) {
        assert lhs.compareToAbs(rhs) >= 0; // lhs >= rhs

        if (rhs.isZero()) {
            return lhs.copy();
        }

        long[] result = new long[lhs.length];
        long accumulator = 0;
        int i = result.length - 1;

        for (int j = rhs.length - 1; j >= 0; --i, --j) {
            accumulator = lhs.get(i) - rhs.get(j) + SubtractWithCarry.carry(accumulator);
            result[i] = SubtractWithCarry.value(accumulator);
        }
        for (; i >= 0; --i) {
            accumulator = lhs.get(i) + SubtractWithCarry.carry(accumulator);
            result[i] = SubtractWithCarry.value(accumulator);
        }

        assert SubtractWithCarry.carry(accumulator) == 0;
        return new Int18N(result).canonicalize();
    }

    private Int18N multiplySign(Int18N lhs, Int18N rhs) {
        return setNegative(multiplySign(lhs.negative, rhs.negative));
    }

    private static boolean multiplySign(boolean lhs, boolean rhs) {
        return lhs ^ rhs;
    }

    public static Int18N multiplySimple(Int18N lhs, Int18N rhs) {
        return multiplySimpleForward(lhs, rhs).multiplySign(lhs, rhs);
    }

    private static Int18N multiplySimpleForward(Int18N lhs, Int18N rhs) {
        if (lhs.isZero() || rhs.isZero()) {
            // don't reuse references b/c of mutability!
            return Constants.ZERO();
        }
        int lhsLength = lhs.length;
        int rhsLength = rhs.length;
        if (false&&lhsLength == 1 && rhsLength == 1) {
            long lhsValue = lhs.get(0); // we must multiply in long, not int.
            long rhsValue = rhs.get(0);
            return fromLongAbs(lhsValue * rhsValue);
        } else if (lhsLength > rhsLength) {
            // the algorithm performs better with lhs > rhs
            return multiplyImpl(lhs, rhs);
        } else {
            return multiplyImpl(rhs, lhs);
        }
    }

    //@VisibleForTesting
    static Int18N multiplyImpl(Int18N lhs, Int18N rhs) {
        long[] result = multiplyCore(lhs.data, lhs.offset, lhs.length, rhs.data, rhs.offset, rhs.length);
        return new Int18N(result).canonicalize();
    }

    //@VisibleForTesting
    static boolean nativeLibAvailable;

    static {
        File lib = new File("build/native/multiply-core-18.so"); // on Windows, this is a DLL
        if (nativeLibAvailable = lib.exists()) {
            System.load(lib.getAbsolutePath());
        }
    }

    private static native void multiplyCore(
            long[] result,
            long[] lhs, int lhsOffset, int lhsLength,
            long[] rhs, int rhsOffset, int rhsLength);

    // "gradle school" multiplication algorithm aka "long multiplication"
    private static long[] multiplyCore(
            long[] lhs, int lhsOffset, int lhsLength,
            long[] rhs, int rhsOffset, int rhsLength) {

        long[] result = new long[lhsLength + rhsLength];
        multiplyCore(result, lhs, lhsOffset, lhsLength, rhs, rhsOffset, rhsLength);
        return result;
    }

    public static Int18N multiplyRussianPeasant(Int18N lhs, Int18N rhs) {
        return multiplyRussianPeasantForward(lhs, rhs).multiplySign(lhs, rhs);
    }

    private static Int18N multiplyRussianPeasantForward(Int18N lhs, Int18N rhs) {
        if (lhs.isZero() || rhs.isZero()) {
            // don't reuse references b/c of mutability!
            return Constants.ZERO();
        } else if (lhs.length <= rhs.length) {
            // the algorithm performs better with lhs <= rhs
            // it actually relies on lhs being smaller, that way
            // the rhs will need exactly 2N space after all doubles.
            return multiplyRussianPeasantImpl(lhs, rhs);
        } else {
            return multiplyRussianPeasantImpl(rhs, lhs);
        }
    }

    /*
     * I really like how short this method is,
     * but it performs ~10 times worse than naive multiplication
     * for similarly-sized terms.
     */
    private static Int18N multiplyRussianPeasantImpl(Int18N lhs, Int18N rhs) {
        var sum = new Int18N(new long[lhs.length + rhs.length]);

        // we must copy b/c adding and halving is destructive.
        // we preallocate the rhs with double it's size
        // since after all doubling is done, it will need that much space anyway.
        lhs = lhs.copyFullSize();
        rhs = rhs.copyDoubleSize();

        boolean proceed;
        do {
            proceed = lhs.compareToAbs(ONE) > 0;
            if (!lhs.isEven()) {
                sum.addInPlaceAbsLongerEqual(rhs);
            }
            lhs.halfInPlaceImpl();
            rhs.doubleInPlaceImpl();
        } while (proceed);

        return sum.canonicalize();
    }

    // returns true if original number was odd, i.e. a carry of 1 remains
    public boolean halfInPlace() {
        ensureCapacity(0);
        return halfInPlaceImpl();
    }

    private boolean halfInPlaceImpl() {
        long carry = 0;
        for (int i = offset, max = offset + length; i < max; i++) {
            long value = data[i];
            long div = value >> 1; // value / 2
            long mod = value & 1;  // value % 2
            data[i] = div + carry;
            carry = mod == 0 ? 0 : HALF_BASE;
        }
        canonicalize();
        return carry > 0;
    }

    // returns true if number had to grow by one place
    public void doubleInPlace() {
        ensureCapacity(1); // one extra space might be needed
        doubleInPlaceImpl();
    }

    private void doubleInPlaceImpl() {
        int carry = 0;
        for (int i = offset + length - 1; i >= offset; --i) {
            long value = data[i];
            long product = value << 1; // value * 2, safe inside long
            if (product >= BASE) {
                product -= BASE;
                assert product < BASE;
                data[i] = carry + product;
                carry = 1;
            } else {
                data[i] = carry + product;
                carry = 0;
            }
        }

        if (carry > 0) {
            expandWith(carry);
        }
    }

    // divides in-place
    // returns remainder
    // Note: divisor cannot be long, otherwise the "carry * BASE" multiplication might overflow.
    public int divideInPlace(int divisorArg) {
        if (divisorArg == 0) {
            throw new ArithmeticException("Division by zero");
        }
        if (isZero()) {
            return 0; // nothing to do
        }
        long divisor = divisorArg;
        boolean divNegative = divisor < 0;
        if (divNegative) {
            divisor = -divisor; // long can represent -(Integer.MIN_VALUE)
        }
        assert divisor > 0;
        boolean wasNegative = negative;
        setNegative(multiplySign(wasNegative, divNegative));
        if (divisor == 1) {
            return 0; // nothing to do
        }

        ensureCapacity(0);

        long carry;
        if (Calc.isPowerOfTwo(divisor)) {
            carry = divideInPlaceAbsShiftAnd(divisor);
        } else if (divisor == 3) {
            carry = divideInPlaceAbsBy3();
        } else {
            carry = divideInPlaceAbsDivMod(divisor);
        }

        canonicalize();
        assert carry >= 0;
        if (wasNegative) {
            carry = -carry;
        }
        assert carry == (int) carry;
        return (int) carry;
    }

    private long divideInPlaceAbsShiftAnd(long divisor) {
        assert Calc.isPowerOfTwo(divisor);
        long mask = divisor - 1;
        int shift = Calc.bitLength(mask);
        long carry = 0;
        for (int i = offset, max = offset + length; i < max; i++) {
//            long value = data[i] + carry * BASE;
            long value = data[i] + Math.multiplyExact(carry, BASE);//XXX
            long quot = value >> shift;
            carry     = value & mask;
            assert quot == (int) quot;
            data[i] = (int) quot;
        }
        return carry;
    }

    private long divideInPlaceAbsBy3() {
        long carry = 0;
        for (int i = offset, max = offset + length; i < max; i++) {
//            long value = data[i] + carry * BASE;
            long value = data[i] + Math.multiplyExact(carry, BASE);//XXX
            long quot = DivMulTable.div3(value);
            carry     = DivMulTable.mod3(quot, value);
            assert quot == (int) quot;
            data[i] = (int) quot;
        }
        return carry;
    }

    private long divideInPlaceAbsDivMod(long divisor) {
        long carry = 0;
        for (int i = offset, max = offset + length; i < max; i++) {
//            long value = data[i] + carry * BASE;
            long value = data[i] + Math.multiplyExact(carry, BASE);//XXX
            long quot = value / divisor;
            carry     = value % divisor;
            assert quot == (int) quot;
            data[i] = (int) quot;
        }
        return carry;
    }

    public boolean isEven() {
        return (get(length - 1) & 1) == 0;
    }

    public static Int18N pow(Int18N base, int exponent) {
        return pow(base, exponent, KARATSUBA_THRESHOLD);
    }

    public static Int18N pow(Int18N base, int exponent, int threshold) {
        var result = Constants.ONE();

        while (exponent > 0) {
            if ((exponent & 1) != 0) {
                result = multiplyKaratsuba(result, base, threshold);
                exponent--;
            }

            exponent >>= 1;
            base = multiplyKaratsuba(base, base, threshold); // square
        }

        return result;
    }

    public static Int18N parallelPow(Int18N base, int exponent, ForkJoinPool pool) {
        return parallelPow(base, exponent, KARATSUBA_THRESHOLD, Calc.maxDepth(pool), pool);
    }

    public static Int18N parallelPow(Int18N base, int exponent, int threshold, int maxDepth, ForkJoinPool pool) {
        var result = Constants.ONE();

        while (exponent > 0) {
            if ((exponent & 1) != 0) {
                result = parallelMultiplyKaratsuba(result, base, threshold, maxDepth, pool);
                exponent--;
            }

            exponent >>= 1;
            base = parallelMultiplyKaratsuba(base, base, threshold, maxDepth, pool); // square
        }

        return result;
    }

    //@VisibleForTesting
    Int18N leftPart(int n) {
        if ((n >> 1) >= length) {
            return ZERO;
        } else {
            return new Int18N(data, offset, ((n + 1) >> 1) + length - n);
        }
    }

    //@VisibleForTesting
    Int18N rightPart(int n) {
        if ((n >> 1) >= length) {
            return this;
        } else {
            int newOffset = offset + ((n + 1) >> 1) + length - n;
            return newOffset < data.length ? new Int18N(data, newOffset, n >> 1).canonicalize() : ZERO;
        }
    }

    public static Int18N multiplyKaratsuba(Int18N lhs, Int18N rhs) {
        return multiplyKaratsuba(lhs, rhs, KARATSUBA_THRESHOLD);
    }

    public static Int18N multiplyKaratsuba(Int18N lhs, Int18N rhs, int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        return multiplyKaratsubaForward(lhs, rhs, threshold).multiplySign(lhs, rhs);
    }

    private static Int18N multiplyKaratsubaForward(Int18N lhs, Int18N rhs, int threshold) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimpleForward(lhs, rhs);
        } else {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        }
    }

    private static Int18N multiplyKaratsubaImpl(Int18N lhs, Int18N rhs, int threshold) {
        assert threshold >= 1;
        assert !(lhs.length == 1 && rhs.length == 1); // too low threshold

        int n = Math.max(lhs.length, rhs.length);
        var a = lhs.leftPart(n);
        var b = lhs.rightPart(n);
        var c = rhs.leftPart(n);
        var d = rhs.rightPart(n);
        var ac = multiplyKaratsubaForward(a, c, threshold);
        var bd = multiplyKaratsubaForward(b, d, threshold);
        var middle = multiplyKaratsubaForward(
                addAbs(a, b),
                addAbs(c, d),
                threshold
        );

        // `middle -= (ac + bd)` => `ad + bc`
        middle.subtractInPlaceAbsGreaterEqual(ac);
        middle.subtractInPlaceAbsGreaterEqual(bd);

        //XXX
//        var tmp = middle;
//        tmp = subtractAbsGreaterEqual(tmp, ac);
//        tmp = subtractAbsGreaterEqual(tmp, bd);
//        middle = tmp;

        var result = new Int18N(new long[lhs.length + rhs.length]);
        /*
         * add up the left (biggest) part `ac` expanded to the full power,
         * with the middle part `middle`, expanded to the half power,
         * and the right (smallest) part `bd` (expanded to zero power).
         */
        int half = n >> 1;
        result.addInPlaceAbsLongerEqual(ac.shiftLeft(half << 1));
        result.addInPlaceAbsLongerEqual(middle.shiftLeft(half));
        result.addInPlaceAbsLongerEqual(bd);
        return result.canonicalize();
    }

    public static Int18N parallelMultiplyKaratsuba(Int18N lhs, Int18N rhs, ForkJoinPool pool) {
        return parallelMultiplyKaratsuba(lhs, rhs, KARATSUBA_THRESHOLD, Calc.maxDepth(pool), pool);
    }

    public static Int18N parallelMultiplyKaratsuba(Int18N lhs, Int18N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Illegal maxDepth: " + maxDepth);
        }
        return parallelMultiplyKaratsubaForward(0, lhs, rhs, threshold, maxDepth, pool).multiplySign(lhs, rhs);
    }

    private static Int18N parallelMultiplyKaratsubaForward(int depth, Int18N lhs, Int18N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimpleForward(lhs, rhs);
        } else if (depth >= maxDepth) {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        } else {
            return parallelMultiplyKaratsubaImpl(depth + 1, lhs, rhs, threshold, maxDepth, pool);
        }
    }

    private static Int18N parallelMultiplyKaratsubaImpl(int depth, Int18N lhs, Int18N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        assert threshold >= 1;
        assert !(lhs.length == 1 && rhs.length == 1); // too low threshold
        assert maxDepth >= 1;
        assert depth <= maxDepth;

        int n = Math.max(lhs.length, rhs.length);
        var a = lhs.leftPart(n);
        var b = lhs.rightPart(n);
        var c = rhs.leftPart(n);
        var d = rhs.rightPart(n);

        var _ac = submit(pool, () -> parallelMultiplyKaratsubaForward(depth, a, c, threshold, maxDepth, pool));
        var _bd = submit(pool, () -> parallelMultiplyKaratsubaForward(depth, b, d, threshold, maxDepth, pool));
        var _middle = submit(pool, () -> parallelMultiplyKaratsubaForward(
                depth,
                addAbs(a, b),
                addAbs(c, d),
                threshold,
                maxDepth,
                pool
        ));
        var ac = _ac.join();
        var bd = _bd.join();
        var middle = _middle.join();

        // `middle -= (ac + bd)` => `ad + bc`
        middle.subtractInPlaceAbsGreaterEqual(ac);
        middle.subtractInPlaceAbsGreaterEqual(bd);
        var result = new Int18N(new long[lhs.length + rhs.length]);
        /*
         * add up the left (biggest) part `ac` expanded to the full power,
         * with the middle part `middle`, expanded to the half power,
         * and the right (smallest) part `bd` (expanded to zero power).
         */
        int half = n >> 1;
        result.addInPlaceAbsLongerEqual(ac.shiftLeft(half << 1));
        result.addInPlaceAbsLongerEqual(middle.shiftLeft(half));
        result.addInPlaceAbsLongerEqual(bd);
        return result.canonicalize();
    }

    @SuppressWarnings("serial")
    private static ForkJoinTask<Int18N> submit(ForkJoinPool pool, Supplier<Int18N> fn) {
        return pool.submit(new RecursiveTask<Int18N>() {

            @Override
            protected Int18N compute() {
                return fn.get();
            }
        });
    }

    /*
     * CharSequence API
     */

    @Override
    public int length() {
        return (negative ? 1 : 0) + countDigits();
    }

    // random access to a digit w/o needing to materialize a string
    // this is optimized to not have any divisions.
    @Override
    public char charAt(int index) {
        if (negative) {
            if (index == 0) {
                return '-';
            }
            index--;
        }
        // correct for variable length of first element in data[]
        index += SIZE - firstDigitLength();

        int div18 = DivMulTable.div18(index);
        int idx = offset + div18;
        return idx < data.length ? IntegerFormat.at(data[idx], DivMulTable.mod18(div18, index)) : '0';
    }

    @Override
    public Int18N subSequence(int start, int end) {
        // we must not return `this` b/c of mutability
        return fromString(this, start, end);
    }

    private static class Calc {

        static int maxDepth(ForkJoinPool pool) {
            return maxDepth(pool.getParallelism());
        }

        static int maxDepth(int parallelism) {
            return bitLength(parallelism) << 1; // e.g. 4=>6, 8=>8, 16=>10
        }

        static int bitLength(long w) {
            return 64 - Long.numberOfLeadingZeros(w);
        }

        static boolean isPowerOfTwo(long w) {
            return (w & (w - 1)) == 0;
        }

        static int lengthOf(long n) {
            assert n >= 0;
            return n < BASE ? 1 : 2;
        }

        static int lengthForDigits(int digits) {
            int div18 = DivMulTable.div18(digits);
            int mod18 = DivMulTable.mod18(div18, digits);
            return div18 + (mod18 == 0 ? 0 : 1);
        }

        static int addInPlaceCapacity(int lhs, int rhs) {
            return lhs > rhs ? 1 : 1 + rhs - lhs; // always need one more space for 999_999_999 + 1 case!
        }

        static int subtractInPlaceCapacity(int lhs, int rhs) {
            return lhs > rhs ? 0 : rhs - lhs;
        }
    }

    private static class AddWithCarry {

        static long value(long accumulator) {
            return accumulator < BASE ? accumulator : accumulator - BASE;
        }

        static long carry(long accumulator) {
            return accumulator < BASE ? 0 : 1;
        }
    }

    private static class SubtractWithCarry {

        static long value(long accumulator) {
            return accumulator >= 0 ? accumulator : accumulator + BASE;
        }

        static long carry(long accumulator) {
            return accumulator >= 0 ? 0 : -1;
        }
    }

    private static class SubtractWithCarryComplement {

        static long value(long accumulator) {
            return BASE - (accumulator > 0 ? accumulator : accumulator + BASE);
        }

        static long carry(long accumulator) {
            return accumulator <= 0 ? 0 : 1;
        }
    }

    private static class IntegerFormat {

        static long parse(CharSequence str, int fromIndex, int toIndex) {
            long result = 0;
            assert fromIndex < toIndex;
            assert toIndex - fromIndex <= SIZE; // need no overflow protection if this holds

            for (int i = fromIndex; i < toIndex; i++) {
                char c = str.charAt(i);
                if (!('0' <= c && c <= '9')) {
                    throw new NumberFormatException("Non-digit character '" + c + "' at index " + i);
                }
                result = DivMulTable.mul10(result) + c - '0';
            }

            return result;
        }

        // equivalent to String.valueOf(digit).charAt(idx)
        static char at(long digit, int idx) {
            assert 0 <= idx && idx < SIZE : idx;
            return (char) (DivMulTable.mod10(DivMulTable.divPower10(digit, idx)) + '0');
        }

        static void format(byte[] dest, long n, int left, int right) {
            for (int i = right - 1; i >= left; --i) {
                if (n == 0) {
                    dest[i] = '0';
                } else {
                    long div10 = DivMulTable.div10(n);
                    dest[i] = (byte) (DivMulTable.mod10(div10, n) + '0');
                    n = div10;
                }
            }
        }

        static int length(long n) {
            assert n < BASE;
            return (""+n).length(); ///XXX this is dumb
//            return n < 100_000 ?         n <        100 ? n <        10 ? 1 : 2 : n <       1_000 ? 3
//                 : n <  10_000 ? 4 : 5 : n < 10_000_000 ? n < 1_000_000 ? 6 : 7 : n < 100_000_000 ? 8 : 9;
        }
    }

    /**
     * Collection of "magic numbers" to replace divisions with multiplications.
     * It's quite surprising that this actually helps performance, i.e.
     * the JIT doesn't seem to do this, or do it well.
     * The "magic numbers" are actually -(modular inverses)- reciprocal numbers.
     * They were found using Compiler Explorer.
     */
    private static class DivMulTable {

//        private static final long[] MOD_INV = { // MOD_INV[i] == (1 << SHR[i]) / POWERS_OF_TEN[i] + 1; // except for last one
//        /*1E8*/ 0x55e63b89,
//                0x6b5fca6b,
//                0x431bde83,
//                0x14f8b589,
//                0x68db8bad,
//                0x10624dd3,
//                0x51eb851f,
//                0x66666667,
//        /*1E0*/ 1,
//        };
//
//        private static final long[] SHR = {
//        /*1E8*/ 57,
//                54,
//                50,
//                45,
//                44,
//                38,
//                37,
//                34,
//        /*1E0*/ 0,
//        };

        private static final long[] P10 = {
        /*1E15*/ 1000000000000000L,
                 100000000000000L,
                 10000000000000L,
                 1000000000000L,
                 100000000000L,
                 10000000000L,
                 1000000000L,
                 100000000L,
                 10000000L,
                 1000000L,
                 100000L,
                 10000L,
                 1000L,
                 100L,
                 10L,
        /*1E0*/  1L,

        };


        // equivalent to input / POWERS_OF_TEN[idx] with POWERS_OF_TEN = { 1E15 .. 1E0 }
        // Note: replacing the arrays with a switch seems to perform worse.
        static int divPower10(long input, int idx) {
//            assert input >> 31 == 0; // so we don't need the subtraction part
//            return (int) ((input * MOD_INV[idx]) >> SHR[idx]);
            return (int) (input / P10[idx]);
        }

        static long div10(long input) {
            return input / 10;
//            assert input >> 31 == 0; // so we don't need the subtraction part
//            return (int) ((input * 0x66666667L) >> 34);
        }

        static long mod10(long input) {
            return input % 10;
//            return mod10(div10(input), input);
        }

        static long mod10(long div10, long input) {
            return input % 10;
        }

        static long mul10(long input) {
            return input * 10;//XXX
//            return (input + (input << 2)) << 1;
        }

        static long div3(long input) {
            return (input * 0xAAAAAAABL) >> 33;
        }

        static long mod3(long div3, long input) {
            return input - mul3(div3);
        }

        static long mul3(long input) {
            return input + (input << 1);
        }

        static int div18(int input) {
            return input / 18; //XXX;
        }

        static int mod18(int div9, int input) {
            return input % 18; //XXX;
        }

        static int mul18(int input) {
            return input * 18; //XXX;
        }
    }
}
