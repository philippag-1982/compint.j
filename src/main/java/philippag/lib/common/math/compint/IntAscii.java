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

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.function.Supplier;

import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitArraySink;
import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitStreamable;

/**
 * Big Integer implementation using base 10 (or any other base out of [2..256]).
 * Values are stored as ASCII bytes/strings.
 * This makes to/from string conversion trivial and extremely fast,
 * as well as random char access,
 * at the cost of calculation performance.
 * We can even do native math in non-base 10. Not very fast, though,
 * but w/o conversion overhead between decimal and non-decimal.
 *
 * Each instance returned by any of the methods is always mutable.
 * This is done for performance, to be able to use instances as accumulators.
 * However, this means we can't have reused constants for ZERO, ONE et al.
 */
public final class IntAscii implements Comparable<IntAscii>, AsciiDigitStreamable, CharSequence {

    private static final IntAscii ZERO = new IntAscii(StandardBaseConversions.DECIMAL, new byte[1]); // never pass to user!
    private static final int KARATSUBA_THRESHOLD = 80;

    private final BaseConversion base;
    private byte[] data;
    private int offset;
    private int length;

    private IntAscii(BaseConversion base, byte[] data) {
        this(base, data, 0, data.length);
    }

    private IntAscii(BaseConversion base, byte[] data, int offset, int length) {
        if (offset < 0 || offset >= data.length) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("zero or negative length: " + length);
        }
        this.base = Objects.requireNonNull(base, "base");
        this.data = Objects.requireNonNull(data, "data");
        this.offset = offset;
        this.length = length;
    }

    public boolean isZero() {
        assert data[offset] > 0 || length == 1; // canonical form
        return data[offset] == 0 || data[offset] == base.ZERO;
    }

    @Override
    public boolean stream(AsciiDigitArraySink sink) {
        return sink.accept(data, offset, length);
    }

    @Override
    public int countDigits() {
        return length;
    }

    public String toDebugString() {
        var sb = new StringBuilder();

        sb.append(getClass().getSimpleName());
        sb.append(" {digits=").append(countDigits());
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
        for (int i = offset, max = offset + length; i < max; i++) {
            sb.append(sep).append(data[i] & 0xff);
            sep = ", ";
        }
        sb.append("]");

        return sb.toString();
    }

    public byte[] toHexByteArray() {
        byte[] dest = new byte[Calc.checkArraySize((long) length << 1)];

        for (int i = offset, j = 0; i < offset + length; i++, j += 2) {
            IntegerFormat.hex(dest, data[i] & 0xff, j);
        }

        return dest;
    }

    @SuppressWarnings("deprecation")
    public String toHexString() {
        return new String(toHexByteArray(), 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        return new String(data, 0, offset, length);
    }

    public int getBase() {
        return base.BASE;
    }

    public CharSequence toScientific(int precision) {
        return AsciiDigits.toScientific(this, precision);
    }

    /* ===============
     * factory methods
     * ===============
     */

    public static IntAscii fromInt(int value) {
        return fromInt(StandardBaseConversions.DECIMAL, value);
    }

    public static IntAscii fromInt(BaseConversion base, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative values are not supported");
        }
        byte[] data = new byte[IntegerFormat.length(base, value)];
        IntegerFormat.format(base, data, value);
        return new IntAscii(base, data);
    }

    public static IntAscii fromLong(long value) {
        return fromLong(StandardBaseConversions.DECIMAL, value);
    }

    public static IntAscii fromLong(BaseConversion base, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative values are not supported");
        }
        byte[] data = new byte[IntegerFormat.length(base, value)];
        IntegerFormat.format(base, data, value);
        return new IntAscii(base, data);
    }

    public static IntAscii fromInt9(Int9 value) {
        return fromInt9(StandardBaseConversions.DECIMAL, value);
    }

    public static IntAscii fromInt9(BaseConversion base, Int9 value) {
        if (value.isNegative()) {
            throw new IllegalArgumentException("Negative values are not supported");
        }
        if (base == StandardBaseConversions.DECIMAL) {
            // fast path - we can just copy the digits
            return new IntAscii(base, value.toByteArray(/*includeSign*/ false));
        }

        byte[] data = new byte[Calc.checkArraySize(IntegerFormat.length(base, value))];
        int offset = IntegerFormat.format(base, data, value.copy()); // divideInPlace() is destructive
        return new IntAscii(base, data, offset, data.length - offset);
    }

    public static IntAscii fromString(String str) {
        return fromString(StandardBaseConversions.DECIMAL, str);
    }

    public static IntAscii fromString(BaseConversion base, String str) {
        return fromString(base, str, 0, str.length());
    }

    public static IntAscii fromString(String str, int fromIndex, int toIndex) {
        return fromString(StandardBaseConversions.DECIMAL, str, fromIndex, toIndex);
    }

    @SuppressWarnings("deprecation")
    public static IntAscii fromString(BaseConversion base, String str, int fromIndex, int toIndex) {
        while (fromIndex < toIndex - 1 && str.charAt(fromIndex) == '0') {
            fromIndex++;
        }
        int length = toIndex - fromIndex;
        if (length <= 0) {
            throw new NumberFormatException("Empty input string");
        }
        byte[] bytes = new byte[length];
        str.getBytes(fromIndex, toIndex, bytes, 0);
        return new IntAscii(base, bytes);
    }

    public static IntAscii fromBytes(byte[] b) {
        return fromBytes(StandardBaseConversions.DECIMAL, b);
    }

    public static IntAscii fromBytes(BaseConversion base, byte[] b) {
        return fromBytes(b, 0, b.length);
    }

    public static IntAscii fromBytes(byte[] b, int offset, int length) {
        return fromBytes(StandardBaseConversions.DECIMAL, b, offset, length);
    }

    public static IntAscii fromBytes(BaseConversion base, byte[] b, int offset, int length) {
        return new IntAscii(base, b, offset, length).canonicalize();
    }

    public IntAscii copy() {
        // copy only our current region
        return new IntAscii(base, Arrays.copyOfRange(data, offset, offset + length));
    }

    /*
     * This enforces these invariants:
     * - offset must always point to a non-zero number, except for ZERO
     */
    private IntAscii canonicalize() {
        int i = offset;
        int max = offset + length - 1;
        int ZERO = base.ZERO;
        while (i < max && (data[i] == 0 || data[i] == ZERO)) {
            i++;
        }

        length = length - i + offset;
        offset = i;
        return this;
    }

    public void clear() {
        offset = data.length - 1; // make all space at the left available
        length = 1;
        data[offset] = (byte) base.ZERO;
    }

    private int get0(int idx) {
        return idx < 0 ? 0 : get(idx);
    }

    private int get(int idx) {
        assert 0 <= idx && idx < length;
        int index = offset + idx;
        return index < data.length ? base.value(data[index] & 0xff) : 0;
    }

    private void set(int idx, int value) {
        data[offset + idx] = (byte) base.digit(value);
    }

    private void expand(int value) {
        assert offset > 0;
        data[--offset] = (byte) base.digit(value);
        length++;
    }

    private void setOrExpand(int i, int value) {
        if (i < 0) {
            expand(value);
        } else {
            set(i, value);
        }
    }

    private IntAscii shiftLeft(int by) {
        if (!isZero()) {
            length += by;
        }
        return this;
    }

    public boolean isInt() {
        return compareTo(base.maxInt()) <= 0;
    }

    public int toInt() {
        if (!isInt()) {
            return Integer.MIN_VALUE;
        }
        int result = 0;
        int BASE = base.BASE;

        for (int i = 0; i < length; i++) {
            result = BASE * result + get(i);
        }

        return result;
    }

    public boolean isLong() {
        return compareTo(base.maxLong()) <= 0;
    }

    public long toLong() {
        if (!isLong()) {
            return Long.MIN_VALUE;
        }
        long result = 0;
        int BASE = base.BASE;

        for (int i = 0; i < length; i++) {
            result = result * BASE + get(i);
        }

        return result;
    }

    public Int9 toInt9() {
        if (base == StandardBaseConversions.DECIMAL) {
            // fast path with decimal digits
            return Int9.fromString(this);
        }
        Int9 result = Int9.fromInt(0);
        Int9 BASE = Int9.fromInt(base.BASE);

        for (int i = 0; i < length; i++) {
            result = result.multiply(BASE).addInPlace(get(i));
        }

        return result;
    }

    /* =======================================
     * convenience functional instance methods
     * =======================================
     */

    public IntAscii add(IntAscii rhs) {
        return add(this, rhs);
    }

    public IntAscii subtract(IntAscii rhs) {
        return subtract(this, rhs);
    }

    public IntAscii multiply(IntAscii rhs) {
        var pool = forkJoinPool;
        return pool == null ? multiplyKaratsuba(this, rhs) : parallelMultiplyKaratsuba(this, rhs, pool);
    }

    private static ForkJoinPool forkJoinPool;

    public static void setForkJoinPool(ForkJoinPool pool) {
        forkJoinPool = pool;
    }

    /* ============================
     * static functional arithmetic
     * ============================
     */

    public static IntAscii add(IntAscii lhs, IntAscii rhs) {
        var base = Calc.baseOf(lhs, rhs);
        int BASE = base.BASE;
        int length = 1 + Math.max(lhs.length, rhs.length);
        var result = new IntAscii(base, new byte[length]);
        int accumulator = 0;

        for (int i = length - 1, j = lhs.length - 1, k = rhs.length - 1; i >= 0; --i, --j, --k) {
            accumulator = lhs.get0(j) + rhs.get0(k) + AddWithCarry.carry(accumulator, BASE);
            result.set(i, AddWithCarry.value(accumulator, BASE));
        }

        assert AddWithCarry.carry(accumulator, BASE) == 0;

        return result.canonicalize();
    }

    // Note: returns absolute value of difference - we have no sign
    public static IntAscii subtract(IntAscii lhs, IntAscii rhs) {
        int cmp = lhs.compareTo(rhs);
        return cmp >= 0 ? subtractGreaterEqual(lhs, rhs) : subtractGreaterEqual(rhs, lhs);
    }

    private static IntAscii subtractGreaterEqual(IntAscii lhs, IntAscii rhs) {
        assert lhs.compareTo(rhs) >= 0; // lhs >= rhs

        var base = Calc.baseOf(lhs, rhs);
        int BASE = base.BASE;
        int length = lhs.length;
        var result = new IntAscii(base, new byte[length]);
        int accumulator = 0;

        for (int i = length - 1, j = rhs.length - 1; i >= 0; --i, --j) {
            accumulator = lhs.get(i) - rhs.get0(j) + SubtractWithCarry.carry(accumulator, BASE);
            result.set(i, SubtractWithCarry.value(accumulator, BASE));
        }

        assert SubtractWithCarry.carry(accumulator, BASE) == 0;
        return result.canonicalize();
    }

    public boolean subtractInPlace(IntAscii rhs) {
        int cmp = compareTo(rhs);
        if (cmp <= 0) {
            // can't represent negative, treat it like zero.
            clear();
            return false;
        }
        subtractInPlaceGreaterEqual(rhs);
        return true;
    }

    private void subtractInPlaceGreaterEqual(IntAscii rhs) {
        assert compareTo(rhs) >= 0; // we are the bigger number or equal

        var base = Calc.baseOf(this, rhs);
        int BASE = base.BASE;
        int accumulator = 0;

        for (int i = length - 1, j = rhs.length - 1; i >= 0; --i, --j) {
            accumulator = get(i) - rhs.get0(j) + SubtractWithCarry.carry(accumulator, BASE);
            set(i, SubtractWithCarry.value(accumulator, BASE));
        }

        assert SubtractWithCarry.carry(accumulator, BASE) == 0;
        canonicalize();
    }

    public static IntAscii multiplySimple(IntAscii lhs, IntAscii rhs) {
        int lhsLength = lhs.length;
        int rhsLength = rhs.length;
        if (lhsLength == 1 && rhsLength == 1) {
            return fromInt(Calc.baseOf(lhs, rhs), lhs.get(0) * rhs.get(0));
        } else if (lhsLength > rhsLength) {
            // the algorithm performs better with lhs > rhs
            return multiplyImpl(lhs, rhs);
        } else {
            return multiplyImpl(rhs, lhs);
        }
    }

    //@VisibleForTesting
    // "gradle school" multiplication algorithm aka "long multiplication"
    static IntAscii multiplyImpl(IntAscii lhs, IntAscii rhs) {
        var base = Calc.baseOf(lhs, rhs);
        int BASE = base.BASE;
        var div = base.divTable;

        byte[] result = new byte[lhs.length + rhs.length];
        int carry = 0;
        int shift = 1;

        for (int i = rhs.length - 1; i >= 0; --i, ++shift) {
            int rhsValue = rhs.get(i);
            int k = result.length - shift;

            for (int j = lhs.length - 1; j >= 0; --j, --k) {
                int lhsValue = lhs.get(j);
                int product = carry + lhsValue * rhsValue;          // a LUT for this didn't perform well
                carry =                           div.div(product); // a LUT for div and mod did perform
                int sum = base.value(result[k]) + div.mod(product);
                if (sum >= BASE) {
                    sum -= BASE;
                    assert sum < BASE;
                    carry++;
                }
                result[k] = (byte) base.digit(sum);
            }

            if (carry > 0) {
                assert result[k] == base.ZERO || result[k] == 0;
                result[k] = (byte) base.digit(carry);
                carry = 0;
            }
        }

        assert carry == 0;
        return new IntAscii(base, result).canonicalize();
    }

    //@VisibleForTesting
    IntAscii leftPart(int n) {
        if ((n >> 1) >= length) {
            return ZERO;
        } else {
            return new IntAscii(base, data, offset, ((n + 1) >> 1) + length - n);
        }
    }

    //@VisibleForTesting
    IntAscii rightPart(int n) {
        if ((n >> 1) >= length) {
            return this;
        } else {
            int newOffset = offset + ((n + 1) >> 1) + length - n;
            return newOffset < data.length ? new IntAscii(base, data, newOffset, n >> 1).canonicalize() : ZERO;
        }
    }

    public static IntAscii multiplyKaratsuba(IntAscii lhs, IntAscii rhs) {
        return multiplyKaratsuba(lhs, rhs, KARATSUBA_THRESHOLD);
    }

    public static IntAscii multiplyKaratsuba(IntAscii lhs, IntAscii rhs, int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        return multiplyKaratsubaForward(lhs, rhs, threshold);
    }

    private static IntAscii multiplyKaratsubaForward(IntAscii lhs, IntAscii rhs, int threshold) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimple(lhs, rhs);
        } else {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        }
    }

    private static IntAscii multiplyKaratsubaImpl(IntAscii lhs, IntAscii rhs, int threshold) {
        assert threshold >= 1;
        assert !(lhs.length == 1 && rhs.length == 1); // too low threshold

        var base = Calc.baseOf(lhs, rhs);
        int n = Math.max(lhs.length, rhs.length);
        var a = lhs.leftPart(n);
        var b = lhs.rightPart(n);
        var c = rhs.leftPart(n);
        var d = rhs.rightPart(n);
        var ac = multiplyKaratsubaForward(a, c, threshold);
        var bd = multiplyKaratsubaForward(b, d, threshold);
        var middle = multiplyKaratsubaForward(
                add(a, b),
                add(c, d),
                threshold
        );

        // `middle -= (ac + bd)` => `ad + bc`
        middle.subtractInPlaceGreaterEqual(ac);
        middle.subtractInPlaceGreaterEqual(bd);
        var result = new IntAscii(base, new byte[lhs.length + rhs.length]);
        /*
         * add up the left (biggest) part `ac` expanded to the full power,
         * with the middle part `middle`, expanded to the half power,
         * and the right (smallest) part `bd` (expanded to zero power).
         */
        int half = n >> 1;
        result.addInPlaceImpl(ac.shiftLeft(half << 1));
        result.addInPlaceImpl(middle.shiftLeft(half));
        result.addInPlaceImpl(bd);
        return result;
    }

    public static IntAscii parallelMultiplyKaratsuba(IntAscii lhs, IntAscii rhs, ForkJoinPool pool) {
        return parallelMultiplyKaratsubaForward(0, lhs, rhs, KARATSUBA_THRESHOLD, Calc.maxDepth(pool), pool);
    }

    public static IntAscii parallelMultiplyKaratsuba(IntAscii lhs, IntAscii rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (threshold < 1) {
            throw new IllegalArgumentException("Illegal threshold: " + threshold);
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Illegal maxDepth: " + maxDepth);
        }
        return parallelMultiplyKaratsubaForward(0, lhs, rhs, threshold, maxDepth, pool);
    }

    private static IntAscii parallelMultiplyKaratsubaForward(int depth, IntAscii lhs, IntAscii rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        if (lhs.length <= threshold || rhs.length <= threshold) {
            return multiplySimple(lhs, rhs);
        } else if (depth >= maxDepth) {
            return multiplyKaratsubaImpl(lhs, rhs, threshold);
        } else {
            return parallelMultiplyKaratsubaImpl(depth + 1, lhs, rhs, threshold, maxDepth, pool);
        }
    }

    private static IntAscii parallelMultiplyKaratsubaImpl(int depth, IntAscii lhs, IntAscii rhs, int threshold, int maxDepth, ForkJoinPool pool) {
        assert threshold >= 1;
        assert !(lhs.length == 1 && rhs.length == 1); // too low threshold
        assert maxDepth >= 1;
        assert depth <= maxDepth;

        var base = Calc.baseOf(lhs, rhs);
        int n = Math.max(lhs.length, rhs.length);
        var a = lhs.leftPart(n);
        var b = lhs.rightPart(n);
        var c = rhs.leftPart(n);
        var d = rhs.rightPart(n);

        var _ac = submit(pool, () -> parallelMultiplyKaratsubaForward(depth, a, c, threshold, maxDepth, pool));
        var _bd = submit(pool, () -> parallelMultiplyKaratsubaForward(depth, b, d, threshold, maxDepth, pool));
        var _middle = submit(pool, () -> parallelMultiplyKaratsubaForward(
                depth,
                add(a, b),
                add(c, d),
                threshold,
                maxDepth,
                pool
        ));
        var ac = _ac.join();
        var bd = _bd.join();
        var middle = _middle.join();

        // `middle -= (ac + bd)` => `ad + bc`
        middle.subtractInPlaceGreaterEqual(ac);
        middle.subtractInPlaceGreaterEqual(bd);
        var result = new IntAscii(base, new byte[lhs.length + rhs.length]);
        /*
         * add up the left (biggest) part `ac` expanded to the full power,
         * with the middle part `middle`, expanded to the half power,
         * and the right (smallest) part `bd` (expanded to zero power).
         */
        int half = n >> 1;
        result.addInPlaceImpl(ac.shiftLeft(half << 1));
        result.addInPlaceImpl(middle.shiftLeft(half));
        result.addInPlaceImpl(bd);
        return result;
    }

    @SuppressWarnings("serial")
    private static ForkJoinTask<IntAscii> submit(ForkJoinPool pool, Supplier<IntAscii> fn) {
        return pool.submit(new RecursiveTask<IntAscii>() {

            @Override
            protected IntAscii compute() {
                return fn.get();
            }
        });
    }

    /* =================
     * in-place mutators
     * =================
     */

    private void ensureAddInPlaceCapacity(IntAscii rhs) {
        int minLength = 1 + Math.max(length, rhs.length); // pessimistic
        int minOffset = minLength - length;

        if (offset >= minOffset) {
            return; // enough space preallocated
        }

        int newOffset = minOffset;
        byte[] newData = new byte[data.length + newOffset];
        System.arraycopy(data, offset, newData, newOffset, length);
        offset = newOffset;
        data = newData;
    }

    public IntAscii addInPlace(IntAscii rhs) {
        ensureAddInPlaceCapacity(rhs);
        addInPlaceImpl(rhs);
        return this;
    }

    private void addInPlaceImpl(IntAscii rhs) {
        int accumulator = 0;
        int i = length - 1;
        int BASE = Calc.baseOf(this, rhs).BASE;

        for (int j = rhs.length - 1; j >= 0; j--, i--) {
            accumulator = get0(i) + rhs.get0(j) + AddWithCarry.carry(accumulator, BASE);
            setOrExpand(i, AddWithCarry.value(accumulator, BASE));
        }

        if (AddWithCarry.carry(accumulator, BASE) > 0) {
            carryRest(accumulator, i);
        }
        canonicalize();
    }

    private void carryRest(int accumulator, int i) {
        int BASE = base.BASE;
        assert AddWithCarry.carry(accumulator, BASE) > 0;
        do {
            accumulator = get0(i) + AddWithCarry.carry(accumulator, BASE);
            setOrExpand(i, AddWithCarry.value(accumulator, BASE));
            i--;
        } while (AddWithCarry.carry(accumulator, BASE) > 0);
    }

    @Override
    public int compareTo(IntAscii o) {
        if (this == o) {
            return 0;
        }
        Calc.baseOf(this, o); // throws when bases are not the same
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

    public boolean equals(IntAscii o) {
        return base == o.base && 0 == compareTo(o);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IntAscii o && equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode(); // that's ok
    }

    /*
     * CharSequence API
     */

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(int index) {
        return (char) (data[offset + index] & 0xff);
    }

    @Override
    public IntAscii subSequence(int start, int end) {
        // we must not return `this` b/c of mutability
        return new IntAscii(base, Arrays.copyOfRange(data, offset + start, offset + end));
    }

    private static class Calc {

        static int maxDepth(ForkJoinPool pool) {
            return maxDepth(pool.getParallelism());
        }

        static int maxDepth(int parallelism) {
            return bitLength(parallelism) << 1; // e.g. 4=>6, 8=>8, 16=>10
        }

        private static int bitLength(int w) {
            return 32 - Integer.numberOfLeadingZeros(w);
        }

        static BaseConversion baseOf(IntAscii lhs, IntAscii rhs) {
            if (lhs.base != rhs.base) {
                throw new UnsupportedOperationException("Arithmetic between different bases");
            }
            return lhs.base;
        }

        static int checkArraySize(long length) {
            if (length > 0x7FFF_0000) { // Integer.MAX_VALUE - 64K
                throw new IllegalStateException("Requested array size exceeds maximum: " + length);
            }
            return (int) length;
        }
    }

    private static class AddWithCarry {

        static int value(int accumulator, int BASE) {
            return accumulator < BASE ? accumulator : accumulator - BASE;
        }

        static int carry(int accumulator, int BASE) {
            return accumulator < BASE ? 0 : 1;
        }
    }

    private static class SubtractWithCarry {

        static int value(int accumulator, int BASE) {
            return accumulator >= 0 ? accumulator : accumulator + BASE;
        }

        static int carry(int accumulator, int BASE) {
            return accumulator >= 0 ? 0 : -1;
        }
    }

    public static abstract class BaseConversion {

        protected final int ZERO;
        protected final int BASE;
        protected final DivTable divTable;

        protected BaseConversion(int ZERO, int BASE) {
            if (!(0 <= ZERO && ZERO < 256)) {
                throw new IllegalArgumentException("Illegal value for ZERO: " + ZERO);
            }
            if (!(2 <= BASE && BASE <= 256)) {
                throw new IllegalArgumentException("Illegal value for BASE: " + BASE);
            }
            this.ZERO = ZERO;
            this.BASE = BASE;
            this.divTable = DivTable.build(BASE);
        }

        protected abstract int value(int character);

        protected abstract int digit(int value);

        private IntAscii maxLong;

        final IntAscii maxLong() {
            if (maxLong == null) {
                maxLong = fromLong(this, Long.MAX_VALUE);
            }
            return maxLong;
        }

        private IntAscii maxInt;

        final IntAscii maxInt() {
            if (maxInt == null) {
                maxInt = fromInt(this, Integer.MAX_VALUE);
            }
            return maxInt;
        }

        public static BaseConversion forBase(int BASE) {
            if (!(2 <= BASE && BASE <= 10)) {
                throw new IllegalArgumentException("This method only supports base up to 10, had " + BASE);
            }
            return new BaseConversion('0', BASE) {

                @Override
                protected int value(int character) {
                    int value = character - ZERO;
                    // unmappable characters are simply considered zero.
                    return 0 <= value && value < BASE ? value : 0;
                }

                @Override
                protected int digit(int value) {
                    return value + ZERO;
                }
            };
        }
    }

    public static final class StandardBaseConversions {

        public static final BaseConversion BINARY = BaseConversion.forBase(2);
        public static final BaseConversion OCTAL = BaseConversion.forBase(8);
        public static final BaseConversion DECIMAL = BaseConversion.forBase(10);
        public static final BaseConversion HEX = TableBaseConversion.build('0',
                "0123456789ABCDEF",
                "0123456789abcdef"
        );
    }

    public static final class TableBaseConversion extends BaseConversion {

        private final byte[] values;
        private final byte[] digits;

        private TableBaseConversion(int ZERO, int BASE, byte[] values, byte[] digits) {
            super(ZERO, BASE);
            this.values = Objects.requireNonNull(values, "values");
            this.digits = Objects.requireNonNull(digits, "digits");
        }

        public static BaseConversion build(int ZERO, CharSequence... digitsOverlays) {
            int BASE = digitsOverlays[0].length();
            byte[] digits = new byte[BASE];
            byte[] values = new byte[256];

            for (CharSequence digitOverlay : digitsOverlays) {
                for (int i = 0; i < digits.length; i++) {
                    char c = digitOverlay.charAt(i);
                    values[c] = (byte) i;
                    digits[i] = (byte) c;
                }
            }

            return new TableBaseConversion(ZERO, BASE, values, digits);
        }

        @Override
        protected int value(int character) {
            return values[character];
        }

        @Override
        protected int digit(int value) {
            return digits[value];
        }
    }

    public static final class SpecialBaseConversions {

        public static final BaseConversion BASE_36() {
            return TableBaseConversion.build('0',
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                "0123456789abcdefghijklmnopqrstuvwxyz"
            );
        }

        public static final BaseConversion BASE_32() {
            return TableBaseConversion.build(0,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
                "abcdefghijklmnopqrstuvwxyz234567"
            );
        }

        public static final BaseConversion BASE_64() {
            return TableBaseConversion.build(0,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            );
        }

        public static BaseConversion BASE_256() {
            return  new BaseConversion(0, 256) { // creates a giant LUT!

                @Override
                protected int value(int character) {
                    return character;
                }

                @Override
                protected int digit(int value) {
                    return value;
                }
            };
        }
    }

    private static class IntegerFormat {

        static int length(int n) {
            return n <    100_000 ? n <       100 ? n <                  10 ? 1 : 2 : n <     1_000 ? 3 : n < 10_000 ? 4 : 5
                 : n < 10_000_000 ? n < 1_000_000 ? 6 : 7 : n < 100_000_000 ? 8 : n < 1_000_000_000 ? 9 : 10;
        }

        static int length(BaseConversion base, int n) {
            return base == StandardBaseConversions.DECIMAL ? length(n) : length(base, (long) n);
        }

        static int length(BaseConversion base, long n) {
            assert n >= 0;
            int BASE = base.BASE;

            if (n < BASE) {
                return 1;
            }
            int i = 0;
            while (n > 0) {
                n /= BASE;
                i++;
            }
            return i;
        }

        static void format(BaseConversion base, byte[] dest, long n) {
            assert n >= 0;
            int BASE = base.BASE;
            assert BASE > 1;
            for (int i = dest.length - 1; i >= 0; i--) {
                dest[i] = (byte) base.digit((int) (n % BASE));
                n /= BASE;
            }
        }

        private static final double LOG_10 = Math.log(10);

        static long length(BaseConversion base, Int9 value) {
            double ratio = LOG_10 / Math.log(base.BASE);
            return Math.round(Math.ceil(value.countDigits() * ratio));
        }

        static int format(BaseConversion base, byte[] dest, Int9 n) {
            int BASE = base.BASE;
            for (int i = dest.length - 1; ; i--) {
                int mod = n.divideInPlace(BASE);
                dest[i] = (byte) base.digit(mod);
                if (n.isZero()) {
                    return i;
                }
            }
        }

        static void hex(byte[] dest, int n, int pos) {
            dest[pos    ] = HEX[(n >> 4) & 0xf];
            dest[pos + 1] = HEX[(n     ) & 0xf];
        }

        private static final byte[] HEX = {
                '0', '1', '2', '3', '4', '5', '6', '7'
              , '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };
    }

    /**
     * Caches values for X / BASE and X % BASE
     */
    private static final class DivTable {

        private final byte[] div;
        private final byte[] mod;

        DivTable(byte[] div, byte[] mod) {
            this.div = div;
            this.mod = mod;
        }

        static DivTable build(int base) {
            int length = base * base;
            byte[] div = new byte[length];
            byte[] mod = new byte[length];

            for (int i = 0; i < length; i++) {
                assert i / base < 256 && i % base < 256;
                div[i] = (byte) (i / base);
                mod[i] = (byte) (i % base);
            }

            return new DivTable(div, mod);
        }

        int div(int value) {
            return div[value] & 0xff;
        }

        int mod(int value) {
            return mod[value] & 0xff;
        }
    }
}
