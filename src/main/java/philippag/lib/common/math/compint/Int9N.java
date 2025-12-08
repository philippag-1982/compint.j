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
import java.io.IOException;
import java.nio.file.Files;
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
 * This class lacks the usual random access to bits, and logical operations,
 * but instead offers random access to decimal digits.
 *
 * This class implements some core/hot routines natively:
 * - multiplyCore() - a slight speedup can be observed, esp. single-threaded
 * - experiments with implemeneting charAt() natively showed huge slow down
 *   probably due to JNI/Java context switching overhead
 */
public final class Int9N implements Comparable<Int9N>, AsciiDigitStreamable, CharSequence {

    public static final boolean nativeLibAvailable = NativeLibLoader.loadAsResource(Int9N.class, "int9.so", /*autoExtract*/ true);

    private static final int BASE = 1_000_000_000;
    private static final int HALF_BASE = BASE / 2;
    private static final int DOUBLE_BASE = BASE * 2;
    private static final long BASE1 = BASE;
    private static final long BASE2 = BASE1 * BASE1;
    private static final int SIZE = 9;
    private static final int KARATSUBA_THRESHOLD = 40;

    // never return to user!
    private static final Int9N ZERO     = Constants.ZERO();
    private static final Int9N ONE      = Constants.ONE();
    private static final Int9N INT_MAX  = Constants.INT_MAX();
    private static final Int9N INT_MIN  = Constants.INT_MIN();
    private static final Int9N LONG_MAX = Constants.LONG_MAX();
    private static final Int9N LONG_MIN = Constants.LONG_MIN();

    private static class Constants {

        private static Int9N ZERO()     { return new Int9N(0); }
        private static Int9N ONE()      { return new Int9N(1); }
        private static Int9N INT_MAX()  { return new Int9N(2, 147483647); }
        private static Int9N INT_MIN()  { return new Int9N(2, 147483648).setNegative(true); }
        private static Int9N LONG_MAX() { return new Int9N(9, 223372036, 854775807); }
        private static Int9N LONG_MIN() { return new Int9N(9, 223372036, 854775808).setNegative(true); }
    }

    private boolean negative;
    private byte firstDigitLength; // cached value
    private int[] data; // integers from 000_000_000 to 999_999_999
    private int offset;
    private int length;

    //@VisibleForTesting
    Int9N(int... data) {
        this(data, 0, data.length);
    }

    private Int9N(int[] data, int offset, int length) {
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

    public Int9N setNegative(boolean b) {
        negative = b && !isZero();
        return this;
    }

    @Override
    public boolean isNegative() {
        return negative;
    }

    public Int9N negate() {
        return copy().setNegative(!negative);
    }

    //does not include sign
    @Override
    public int countDigits() {
        return firstDigitLength() + DivMulTable.mul9(length - 1);
    }

    //does not include sign
    @Override
    public boolean stream(AsciiDigitArraySink sink) {
        byte[] dest = new byte[SIZE];
        int first = get(0);
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

        sb.append("Int9N");
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

    public static Int9N fromScientific(CharSequence str) {
        return fromString(AsciiDigits.fromScientific(str));
    }

    public CharSequence toScientific(int precision) {
        return AsciiDigits.toScientific(this, precision);
    }

    private void expandWith(int value) {
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

    private Int9N shiftLeft(int by) {
        if (!isZero()) {
            length += by;
        }
        return this;
    }

    private int get(int idx) {
        assert 0 <= idx && idx < length;
        int index = offset + idx;
        return index < data.length ? data[index] : 0;
    }

    /*
     * This enforces these invariants:
     * - offset must always point to a non-zero number, except for ZERO
     * - no representation of ZERO is ever negative
     */
    private Int9N canonicalize() {
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

    public static Int9N forDigits(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("Zero or negative digit count: " + digits);
        }
        // reserve 1 extra space so "inPlace" methods don't have to resize array by 1 element
        int length = 1 +  Calc.lengthForDigits(digits);
        return new Int9N(new int[length], length - 1, 1);
    }

    public static Int9N fromInt(int value) {
        if (value == Integer.MIN_VALUE) {
            return Constants.INT_MIN();
        }
        boolean negative = false;
        if (value < 0) {
            negative = true;
            value = -value; // doesn't work for Integer.MIN_VALUE
        }
        return new Int9N(fromIntAbs(value)).setNegative(negative);
    }

    private static int[] fromIntAbs(int value) {
        assert value >= 0;
        if (value < BASE) {
            return new int[] { value };
        } else if (value < DOUBLE_BASE) {
            return new int[] { 1, value - BASE };
        } else {
            return new int[] { 2, value - DOUBLE_BASE };
        }
    }

    public static Int9N fromLong(long value) {
        if (value == Long.MIN_VALUE) {
            return Constants.LONG_MIN();
        }
        boolean negative = false;
        if (value < 0) {
            negative = true;
            value = -value; // doesn't work for Long.MIN_VALUE
        }
        return new Int9N(fromLongAbs(value)).setNegative(negative);
    }

    private static int[] fromLongAbs(long value) {
        assert value >= 0;
        return switch (Calc.lengthOf(value)) {
            case 1  -> new int[] { (int)   value };
            case 2  -> new int[] { (int)  (value / BASE1),          (int)  (value % BASE1) };
            case 3  -> new int[] { (int) ((value / BASE2) % BASE1), (int) ((value / BASE1) % BASE1), (int) (value % BASE1) };
            default -> throw new Error("UNREACHABLE");
        };
    }

    public static Int9N fromString(CharSequence str) {
        return fromString(str, 0, str.length());
    }

    public static Int9N fromString(CharSequence str, int fromIndex, int toIndex) {
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
        int[] result = null;

        for (int i = toIndex, j = length - 1; j >= 0; --j) {
            int end = i;
            i = Math.max(fromIndex, i - SIZE);
            int value = IntegerFormat.parse(str, i, end);
            if (value > 0 && result == null) {
                result = new int[j + 1];  // "trailingZeroesForm" storage optimization: alloc only for non-zero digits at the right
            }
            if (result != null) {
                result[j] = value;
            }
        }

        return result == null ? Constants.ZERO() : new Int9N(result, 0, length).setNegative(negative);
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
        assert length <= 2;
        if (length == 1) {
            return get(0);
        } else {
            return BASE * get(0) + get(1);
        }
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
        assert length <= 3;
        if (length == 1) {
            return get(0);
        } else if (length == 2) {
            return BASE1 * get(0) + get(1);
        } else {
            return BASE2 * get(0) + BASE1 * get(1) + get(2);
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
    public int compareTo(Int9N o) {
        int cmp = -Boolean.compare(negative, o.negative);
        return cmp != 0 ? cmp : negative ? -compareToAbs(o) : compareToAbs(o);
    }

    public int compareToAbs(Int9N o) {
        if (this == o) {
            return 0;
        }
        int cmp = Integer.compare(length, o.length);
        if (cmp != 0) {
            return cmp;
        }
        for (int i = 0; i < length; i++) {
            cmp = Integer.compare(get(i), o.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public boolean equals(Int9N o) {
        return 0 == compareTo(o);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Int9N o && equals(o);
    }

    @Override
    public int hashCode() {
        return get(length - 1);
    }

    public void clear() {
        offset = data.length - 1; // make all space at the left available
        length = 1;
        data[offset] = 0;
        negative = false;
        firstDigitLength = 0;
    }

    private void takeValue(int[] rhs) {
        data = rhs;
        offset = 0;
        length = rhs.length;
    }

    public void setValue(Int9N rhs) {
        int newLength = rhs.length;
        if (data.length < newLength) {
            data = new int[newLength];
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
            data = new int[newLength];
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

    private int[] copyFullSizeArray() {
        // undo "trailingZeroesForm" storage optimization
        int[] newData = new int[length];
        System.arraycopy(data, offset, newData, 0, Math.min(length, data.length));
        return newData;
    }

    public Int9N copy() {
        // copy only relevant region
        // does not undo "trailingZeroesForm" storage optimization
        return new Int9N(Arrays.copyOfRange(data, offset, extent()), 0, length).setNegative(negative);
    }

    private Int9N copyFullSize() {
        return new Int9N(copyFullSizeArray()).setNegative(negative);
    }

    private Int9N copyDoubleSize() {
        int newOffset = length << 1;
        int[] newData = new int[newOffset + length];
        System.arraycopy(data, offset, newData, newOffset, Math.min(length, data.length));
        return new Int9N(newData, newOffset, length).setNegative(negative);
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
        int[] newData = new int[data.length + newOffset];
        System.arraycopy(data, offset, newData, newOffset, length);
        offset = newOffset;
        data = newData;
    }

    private void carryRest(int accumulator, int i) {
        int carry = AddWithCarry.carry(accumulator);
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

    public Int9N multiplyInPlace(Int9N rhs) {
        if (rhs.compareToAbs(BASE) < 0) {
            // fast path for numbers < BASE
            return multiplyInPlace(rhs.toInt());
        }
        takeValue(multiplyImpl(data, offset, length, rhs.data, rhs.offset, rhs.length));
        canonicalize();
        return setNegative(multiplySign(negative, rhs.negative));
    }

    public Int9N multiplyInPlace(long rhs) {
        if (rhs == Long.MIN_VALUE) {
            setValue(multiplySimple(this, LONG_MIN));
            return this;
        } else if (rhs == 0) {
            clear();
            return this;
        }

        boolean rhsNegative = rhs < 0;
        if (rhsNegative) {
            rhs = -rhs;
        }
        assert rhs > 0;

        if (rhs < BASE) {
            // fast path - everything in-place
            ensureCapacity(1);
            if (Calc.isPowerOfTwo(rhs)) {
                multiplyInPlaceAbsShift((int) rhs);
            } else {
                multiplyInPlaceAbs((int) rhs);
            }
        } else {
            // quadratic multiplication needs an interim array
            int[] rhsArray = fromLongAbs(rhs);
            takeValue(multiplyImpl(data, offset, length, rhsArray, 0, rhsArray.length));
        }
        canonicalize();
        return setNegative(multiplySign(negative, rhsNegative));
    }

    private void multiplyInPlaceAbsShift(int rhs) {
        assert rhs < BASE;
        assert Calc.isPowerOfTwo(rhs);

        int shift = Calc.bitLength(rhs - 1);
        int carry = 0;

        for (int i = offset + length - 1; i >= offset; --i) {
            long lhsValue = data[i]; // force multiplication in long
            long product = carry + (lhsValue << shift);
            carry =   (int) (product / BASE);
            int sum = (int) (product % BASE);
            if (sum >= BASE) {
                sum -= BASE;
                assert sum < BASE;
                carry++;
            }
            data[i] = sum;
        }

        if (carry > 0) {
            expandWith(carry);
        }
    }

    private void multiplyInPlaceAbs(int rhs) {
        assert rhs < BASE;

        int carry = 0;

        for (int i = offset + length - 1; i >= offset; --i) {
            long lhsValue = data[i]; // force multiplication in long
            long product = carry + lhsValue * rhs;
            carry =   (int) (product / BASE);
            int sum = (int) (product % BASE);
            if (sum >= BASE) {
                sum -= BASE;
                assert sum < BASE;
                carry++;
            }
            data[i] = sum;
        }

        if (carry > 0) {
            expandWith(carry);
        }
    }

    public Int9N addInPlace(Int9N rhs) {
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

    private void addInPlaceAbs(Int9N rhs) {
        if (length >= rhs.length) {
            addInPlaceAbsLongerEqual(rhs);
        } else {
            addInPlaceAbsShorter(rhs);
        }
        canonicalize();
    }

    // beware: caller must call canonicalize()!
    private void addInPlaceAbsShorter(Int9N rhs) {
        int accumulator = 0;
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
    private void addInPlaceAbsLongerEqual(Int9N rhs) {
        addInPlaceAbsLongerEqualCore(rhs.data, rhs.offset, rhs.length);
    }

    private void addInPlaceAbsLongerEqualCore(int[] rhs, int rhsOffset, int rhsLength) {
        assert length >= rhsLength;

        int accumulator = 0;
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

    public Int9N addInPlace(long rhs) {
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
        int accumulator = data[i] + (int) (rhs % BASE);
        data[i] = AddWithCarry.value(accumulator);

        while (rhs >= BASE) {
            rhs /= BASE;
            if (--i >= offset) {
                accumulator = data[i] + (int) (rhs % BASE) + AddWithCarry.carry(accumulator);
                data[i] = AddWithCarry.value(accumulator);
            } else {
                accumulator = (int) (rhs % BASE) + AddWithCarry.carry(accumulator);
                expandWith(AddWithCarry.value(accumulator));
            }
        }

        if (AddWithCarry.carry(accumulator) > 0) {
            carryRest(accumulator, i - 1);
        }
        canonicalize();
    }

    public Int9N subtractInPlace(Int9N rhs) {
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

    private void subtractInPlaceAbsGreaterEqual(Int9N rhs) {
        assert compareToAbs(rhs) >= 0; // we are the bigger number or equal
        subtractInPlaceAbsGreaterEqualCore(rhs.data, rhs.offset, rhs.length);
    }

    private void subtractInPlaceAbsGreaterEqualCore(int[] rhs, int rhsOffset, int rhsLength) {
        int accumulator = 0;
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

    private void subtractInPlaceAbsLessThan(Int9N rhs) {
        assert length <= rhs.length;
        assert compareToAbs(rhs) < 0; // we are the smaller number

        int accumulator = 0;
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

    public Int9N subtractInPlace(long rhs) {
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

        int accumulator = 0;
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

        int accumulator = 0;
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

    public Int9N incrementInPlace() {
        ensureCapacity(0);
        if (negative) {
            decrementInPlaceAbs();
        } else {
            incrementInPlaceAbs();
        }
        return this;
    }

    public Int9N decrementInPlace() {
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

    public Int9N add(Int9N rhs) {
        return add(this, rhs);
    }

    public Int9N subtract(Int9N rhs) {
        return subtract(this, rhs);
    }

    public Int9N divide(int divisor) {
        var copy = copy();
        copy.divideInPlace(divisor);
        return copy;
    }

    public int modulo(int divisor) {
        return copy().divideInPlace(divisor);
    }

    public Int9N multiply(Int9N rhs) {
        var pool = forkJoinPool;
        return pool == null ? multiplyKaratsuba(this, rhs) : parallelMultiplyKaratsuba(this, rhs, pool);
    }

    public Int9N pow(int exponent) {
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

    public static Int9N add(Int9N lhs, Int9N rhs) {
        if (lhs.negative == rhs.negative) { // equal signs => addition
            return addAbs(lhs, rhs).setNegative(lhs.negative);
        } else { // opposite signs => subtraction
            return lhs.negative ? subtractForward(rhs, lhs) : subtractForward(lhs, rhs);
        }
    }

    private static Int9N addAbs(Int9N lhs, Int9N rhs) {
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

    private static Int9N addAbsLongerEqual(Int9N lhs, Int9N rhs) {
        assert lhs.length >= rhs.length;

        int[] result = new int[1 + lhs.length];  // always need one more space for 999_999_999 + 1 case!
        int accumulator = 0;
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

        return new Int9N(result).canonicalize();
    }

    public static Int9N subtract(Int9N lhs, Int9N rhs) {
        int cmp = lhs.compareToAbs(rhs);
        if (lhs.negative == rhs.negative) { // equal signs => subtraction
            return cmp == 0 ? Constants.ZERO() : subtractForward(cmp, lhs, rhs).setNegative(lhs.negative ? cmp > 0 : cmp < 0);
        } else { // opposite signs => addition
            return addAbs(lhs, rhs).setNegative(lhs.negative);
        }
    }

    private static Int9N subtractForward(Int9N lhs, Int9N rhs) {
        int cmp = lhs.compareToAbs(rhs);
        return cmp == 0 ? Constants.ZERO() : subtractForward(cmp, lhs, rhs).setNegative(cmp < 0);
    }

    private static Int9N subtractForward(int cmp, Int9N lhs, Int9N rhs) {
        assert cmp != 0; // callers should rule out ZERO
        return cmp >= 0 ? subtractAbsGreaterEqual(lhs, rhs) : subtractAbsGreaterEqual(rhs, lhs);
    }

    private static Int9N subtractAbsGreaterEqual(Int9N lhs, Int9N rhs) {
        assert lhs.compareToAbs(rhs) >= 0; // lhs >= rhs

        if (rhs.isZero()) {
            return lhs.copy();
        }

        int[] result = new int[lhs.length];
        int accumulator = 0;
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
        return new Int9N(result).canonicalize();
    }

    private Int9N multiplySign(Int9N lhs, Int9N rhs) {
        return setNegative(multiplySign(lhs.negative, rhs.negative));
    }

    private static boolean multiplySign(boolean lhs, boolean rhs) {
        return lhs ^ rhs;
    }

    public static Int9N multiplySimple(Int9N lhs, Int9N rhs) {
        return multiplySimpleForward(lhs, rhs).multiplySign(lhs, rhs);
    }

    private static Int9N multiplySimpleForward(Int9N lhs, Int9N rhs) {
        if (lhs.isZero() || rhs.isZero()) {
            // don't reuse references b/c of mutability!
            return Constants.ZERO();
        }
        int lhsLength = lhs.length;
        int rhsLength = rhs.length;
        if (lhsLength == 1 && rhsLength == 1) {
            long lhsValue = lhs.get(0); // we must multiply in long, not int.
            long rhsValue = rhs.get(0);
            return new Int9N(fromLongAbs(lhsValue * rhsValue));
        } else if (lhsLength > rhsLength) {
            // the algorithm performs better with lhs > rhs
            return multiplyImpl(lhs, rhs);
        } else {
            return multiplyImpl(rhs, lhs);
        }
    }

    //@VisibleForTesting
    static Int9N multiplyImpl(Int9N lhs, Int9N rhs) {
        int[] result = multiplyImpl(lhs.data, lhs.offset, lhs.length, rhs.data, rhs.offset, rhs.length);
        return new Int9N(result).canonicalize();
    }

    private static int[] multiplyImpl(
            int[] lhs, int lhsOffset, int lhsLength,
            int[] rhs, int rhsOffset, int rhsLength) {

        int[] result = new int[lhsLength + rhsLength];
        int lhsSize = lhsOffset + lhsLength;
        int rhsSize = rhsOffset + rhsLength;
        int shift = 1;

        // fix coordinates for "trailingZeroesForm"
        if (lhsSize > lhs.length) {
            shift += lhsSize - lhs.length;
            lhsSize = lhs.length;
        }
        if (rhsSize > rhs.length) {
            shift += rhsSize - rhs.length;
            rhsSize = rhs.length;
        }
        multiplyCore(result, result.length, shift, lhs, lhsOffset, lhsSize - 1, rhs, rhsOffset, rhsSize - 1);
        return result;
    }

    private static native void multiplyCore(
            int[] result, int resultLength, int shift,
            int[] lhs, int lhsOffset, int lhsMax,
            int[] rhs, int rhsOffset, int rhsMax);

    public static Int9N multiplyRussianPeasant(Int9N lhs, Int9N rhs) {
        return multiplyRussianPeasantForward(lhs, rhs).multiplySign(lhs, rhs);
    }

    private static Int9N multiplyRussianPeasantForward(Int9N lhs, Int9N rhs) {
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
    private static Int9N multiplyRussianPeasantImpl(Int9N lhs, Int9N rhs) {
        var sum = new Int9N(new int[lhs.length + rhs.length]);

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
        int carry = 0;
        for (int i = offset, max = offset + length; i < max; i++) {
            int value = data[i];
            int div = value >> 1; // value / 2
            int mod = value & 1;  // value % 2
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
            int value = data[i];
            int product = value << 1; // value * 2, safe inside int
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
    // Note: divisor cannot be long, otherwise the "carry * BASE1" multiplication might overflow.
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
            long value = data[i] + carry * BASE1;
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
            long value = data[i] + carry * BASE1;
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
            long value = data[i] + carry * BASE1;
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

    public static Int9N pow(Int9N base, int exponent) {
        return pow(base, exponent, KARATSUBA_THRESHOLD);
    }

    public static Int9N pow(Int9N base, int exponent, int threshold) {
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

    public static Int9N parallelPow(Int9N base, int exponent, ForkJoinPool pool) {
        return parallelPow(base, exponent, KARATSUBA_THRESHOLD, Calc.maxDepth(pool), pool);
    }

    public static Int9N parallelPow(Int9N base, int exponent, int threshold, int maxDepth, ForkJoinPool pool) {
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
    Int9N leftPart(int n) {
        if ((n >> 1) >= length) {
            return ZERO;
        } else {
            return new Int9N(data, offset, ((n + 1) >> 1) + length - n);
        }
    }

    //@VisibleForTesting
    Int9N rightPart(int n) {
        if ((n >> 1) >= length) {
            return this;
        } else {
            int newOffset = offset + ((n + 1) >> 1) + length - n;
            return newOffset < data.length ? new Int9N(data, newOffset, n >> 1).canonicalize() : ZERO;
        }
    }

    public static Int9N multiplyKaratsuba(Int9N lhs, Int9N rhs) {
        return multiplyKaratsuba(lhs, rhs, KARATSUBA_THRESHOLD);
    }

    public static Int9N multiplyKaratsuba(Int9N lhs, Int9N rhs, int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        return multiplyKaratsubaForward(lhs, rhs, threshold).multiplySign(lhs, rhs);
    }

    private static Int9N multiplyKaratsubaForward(Int9N lhs, Int9N rhs, int threshold) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimpleForward(lhs, rhs);
        } else {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        }
    }

    private static Int9N multiplyKaratsubaImpl(Int9N lhs, Int9N rhs, int threshold) {
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

        /*
         * We use the distributive law here:
         * `middle` is currently `(a + b) * (c + d)`,
         * which distributes to
         * `ac + ad + bc + bd`.
         * Now, we subtract the 2 products `ac` and `bd` (below)
         * so `middle` equals `ad + bc`.
         */
        middle.subtractInPlaceAbsGreaterEqual(ac);
        middle.subtractInPlaceAbsGreaterEqual(bd);
        var result = new Int9N(new int[lhs.length + rhs.length]);
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

    public static Int9N parallelMultiplyKaratsuba(Int9N lhs, Int9N rhs, ForkJoinPool pool) {
        return parallelMultiplyKaratsuba(lhs, rhs, KARATSUBA_THRESHOLD, Calc.maxDepth(pool), pool);
    }

    public static Int9N parallelMultiplyKaratsuba(Int9N lhs, Int9N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Illegal maxDepth: " + maxDepth);
        }
        return parallelMultiplyKaratsubaForward(0, lhs, rhs, threshold, maxDepth, pool).multiplySign(lhs, rhs);
    }

    private static Int9N parallelMultiplyKaratsubaForward(int depth, Int9N lhs, Int9N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimpleForward(lhs, rhs);
        } else if (depth >= maxDepth) {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        } else {
            return parallelMultiplyKaratsubaImpl(depth + 1, lhs, rhs, threshold, maxDepth, pool);
        }
    }

    private static Int9N parallelMultiplyKaratsubaImpl(int depth, Int9N lhs, Int9N rhs, int threshold, int maxDepth, ForkJoinPool pool) {
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

        /*
         * We use the distributive law here:
         * `middle` is currently `(a + b) * (c + d)`,
         * which distributes to
         * `ac + ad + bc + bd`.
         * Now, we subtract the 2 products `ac` and `bd` (below)
         * so `middle` equals `ad + bc`.
         */
        middle.subtractInPlaceAbsGreaterEqual(ac);
        middle.subtractInPlaceAbsGreaterEqual(bd);
        var result = new Int9N(new int[lhs.length + rhs.length]);
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
    private static ForkJoinTask<Int9N> submit(ForkJoinPool pool, Supplier<Int9N> fn) {
        return pool.submit(new RecursiveTask<Int9N>() {

            @Override
            protected Int9N compute() {
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
    // Note: a solution with cached segments of 9-digit-substring was tried
    // and actually performed worse than this...
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

        int div9 = DivMulTable.div9(index);
        int idx = offset + div9;
        return idx < data.length ? IntegerFormat.at(data[idx], DivMulTable.mod9(div9, index)) : '0';
    }

    @Override
    public Int9N subSequence(int start, int end) {
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
            assert w > 0;
            return (w & (w - 1)) == 0;
        }

        static int lengthOf(long n) {
            assert n >= 0;
            return n < BASE1 ? 1 : n < BASE2 ? 2 : 3;
        }

        static int lengthForDigits(int digits) {
            int div9 = DivMulTable.div9(digits);
            int mod9 = DivMulTable.mod9(div9, digits);
            return div9 + (mod9 == 0 ? 0 : 1);
        }

        static int addInPlaceCapacity(int lhs, int rhs) {
            return lhs > rhs ? 1 : 1 + rhs - lhs; // always need one more space for 999_999_999 + 1 case!
        }

        static int subtractInPlaceCapacity(int lhs, int rhs) {
            return lhs > rhs ? 0 : rhs - lhs;
        }
    }

    private static class AddWithCarry {

        static int value(int accumulator) {
            return accumulator < BASE ? accumulator : accumulator - BASE;
        }

        static int carry(int accumulator) {
            return accumulator < BASE ? 0 : 1;
        }
    }

    private static class SubtractWithCarry {

        static int value(int accumulator) {
            return accumulator >= 0 ? accumulator : accumulator + BASE;
        }

        static int carry(int accumulator) {
            return accumulator >= 0 ? 0 : -1;
        }
    }

    private static class SubtractWithCarryComplement {

        static int value(int accumulator) {
            return BASE - (accumulator > 0 ? accumulator : accumulator + BASE);
        }

        static int carry(int accumulator) {
            return accumulator <= 0 ? 0 : 1;
        }
    }

    private static class IntegerFormat {

        static int parse(CharSequence str, int fromIndex, int toIndex) {
            int result = 0;
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
        static char at(int digit, int idx) {
            assert 0 <= idx && idx < SIZE : idx;
            return (char) (DivMulTable.mod10(DivMulTable.divPower10(digit, idx)) + '0');
        }

        static void format(byte[] dest, int n, int left, int right) {
            for (int i = right - 1; i >= left; --i) {
                if (n == 0) {
                    dest[i] = '0';
                } else {
                    int div10 = DivMulTable.div10(n);
                    dest[i] = (byte) (DivMulTable.mod10(div10, n) + '0');
                    n = div10;
                }
            }
        }

        static int length(int n) {
            assert n < BASE;
            return n < 100_000 ?         n <        100 ? n <        10 ? 1 : 2 : n <       1_000 ? 3
                 : n <  10_000 ? 4 : 5 : n < 10_000_000 ? n < 1_000_000 ? 6 : 7 : n < 100_000_000 ? 8 : 9;
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

        private static final int[] MOD_INV = { // MOD_INV[i] == (1 << SHR[i]) / POWERS_OF_TEN[i] + 1; // except for last one
        /*1E8*/ 0x55e63b89,
                0x6b5fca6b,
                0x431bde83,
                0x14f8b589,
                0x68db8bad,
                0x10624dd3,
                0x51eb851f,
                0x66666667,
        /*1E0*/ 1,
        };

        private static final int[] SHR = {
        /*1E8*/ 57,
                54,
                50,
                45,
                44,
                38,
                37,
                34,
        /*1E0*/ 0,
        };

        // equivalent to input / POWERS_OF_TEN[idx] with POWERS_OF_TEN = { 1E8 .. 1E0 }
        // Note: replacing the arrays with a switch seems to perform worse.
        static int divPower10(long input, int idx) {
            assert input >> 31 == 0; // so we don't need the subtraction part
            return (int) ((input * MOD_INV[idx]) >> SHR[idx]);
        }

        static int div10(int input) {
            assert input >> 31 == 0; // so we don't need the subtraction part
            return (int) ((input * 0x66666667L) >> 34);
        }

        static int mod10(int input) {
            return mod10(div10(input), input);
        }

        static int mod10(int div10, int input) {
            return input - mul10(div10);
        }

        static int mul10(int input) {
            return (input + (input << 2)) << 1;
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

        static int div9(int input) {
            assert input >> 31 == 0; // so we don't need the subtraction part
            return (int) ((input * 0x38e38e39L) >> 33);
        }

        static int mod9(int div9, int input) {
            return input - mul9(div9);
        }

        static int mul9(int input) {
            return (input << 3) + input;
        }
    }

    private static class NativeLibLoader {

        private static final String PREFIX_FILE = "file:";
        private static final String PREFIX_JAR = "jar:file:";

        /**
         * Loads the given native library deployed as a class path resource.
         * There's 2 ways to load the given shared object:
         * 1) When classes are loaded from the file system, the .so file can be loaded directly.
         * 2) When classes are loaded from a JAR file, the .so file is expected to be located
         *    next to the JAR file in the file system.
         */
        private static boolean loadAsResource(Class<?> cls, String libName, boolean autoExtract) {
            var url = cls.getResource("/" + libName);
            if (url == null) {
                System.err.printf("ERROR [%s]: Native library '%s' not found in class path\n", cls.getName(), libName);
                return false;
            }

            String location = url.toString();
            String fileName = null;
            resolve: {
                if (location.startsWith(PREFIX_FILE)) {
                    // "file:/opt/gitwork/shared/compint.j/bin/main/int9.so"
                    // =>
                    // "/opt/gitwork/shared/compint.j/bin/main/int9.so"
                    fileName = location.substring(PREFIX_FILE.length());
                } else if (location.startsWith(PREFIX_JAR)) {
                    // "jar:file:/opt/gitwork/shared/compint.j/build/libs/compint.j.jar!/int9.so"
                    // =>
                    // "/opt/gitwork/shared/compint.j/build/libs/int9.so"
                    int i = location.lastIndexOf('!');
                    if (i == -1) break resolve;
                    i = location.lastIndexOf('/', i - 1);
                    if (i == -1) break resolve;
                    fileName = location.substring(PREFIX_JAR.length(), i + 1) + libName;
                    if (!autoExtract) break resolve;
                    var file = new File(fileName);
                    if (!file.exists()) {
                        try (var in = url.openStream()) {
                            Files.copy(in, file.toPath());
                            System.err.printf("INFO [%s]: Extracted native library '%s' to '%s'\n", cls.getName(), libName, fileName);
                        } catch (IOException e) {
                            // Ignore, error logged below
                        }
                    }
                }
            }

            if (fileName == null) {
                System.err.printf("ERROR [%s]: Could not resolve file system path for native library '%s'; class path resource is '%s'\n", cls.getName(), libName, location);
                return false;
            }

            var file = new File(fileName);
            if (file.exists()) {
                System.load(file.getPath());
                return true;
            } else {
                System.err.printf("ERROR [%s]: Expecting native library '%s' at this location: '%s'\n", cls.getName(), libName, file);
                return false;
            }
        }
    }
}
