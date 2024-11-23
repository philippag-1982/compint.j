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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/**
 * Provides "ASCII" digit streaming capabilites,
 * and to/from "scientific" notation routines.
 * Note: the term "ASCII" is used loosely here,
 * it just means unsigned 8-bit bytes.
 */
public class AsciiDigits {

    public interface AsciiDigitSink {

        boolean accept(char b);
    }

    public interface AsciiDigitArraySink {

        boolean accept(byte[] array, int offset, int length);

        static AsciiDigitArraySink of(AsciiDigitSink sink) {
            return (array, offset, length) -> {
                for (int i = offset; i < offset + length; i++) {
                    if (!sink.accept((char) array[i])) {
                        return false;
                    }
                }
                return true;
            };
        }

        static AsciiDigitArraySink of(OutputStream out) {
            return (array, offset, length) -> {
                try {
                    out.write(array, offset, length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return true;
            };
        }
    }

    public interface AsciiDigitStreamable {

        boolean stream(AsciiDigitArraySink sink);

        int countDigits();

        default boolean isNegative() {
            return false;
        }
    }

    public static CharSequence toScientific(AsciiDigitStreamable number, int precision) {
        var sb = new StringBuilder();
        toScientific(sb, number, precision);
        return sb;
    }

    /**
     * Returns true if the formatted number is absolutely precise,
     * i.e. all available digits have been rendered.
     */
    public static boolean toScientific(StringBuilder sb, AsciiDigitStreamable number, int precision) {
        if (number.isNegative()) {
            sb.append('-');
        }
        if (precision >= 0) {
            return toScientificExact(sb, number, precision);
        } else {
            return toScientificNoTrailingZeroes(sb, number, precision == Integer.MIN_VALUE ? Integer.MAX_VALUE : -precision); // beware: MIN_VALUE can't be negated.
        }
    }

    private static boolean toScientificExact(StringBuilder sb, AsciiDigitStreamable number, int precision) {
        assert precision >= 0;

        var sink = new AsciiDigitSink() {

            int index;

            @Override
            public boolean accept(char c) {
                if (index > precision) {
                    return false;
                }
                if (index == 1 && precision > 0) {
                    sb.append('.');
                }
                sb.append(c);
                index++;
                return true;
            }
        };

        boolean result = number.stream(AsciiDigitArraySink.of(sink));

        sb.append("E+");
        sb.append(number.countDigits() - 1);

        return result;
    }

    private static boolean toScientificNoTrailingZeroes(StringBuilder sb, AsciiDigitStreamable number, int precision) {
        assert precision > 0;

        var sink = new AsciiDigitSink() {

            int index;
            int zeroes;
            boolean dot;
            boolean nonZeroes;

            @Override
            public boolean accept(char c) {
                if (index > precision) {
                    return false;
                }
                if (index == 1) {
                    dot = true;
                }

                if (c == '0') {
                    zeroes++;
                } else {
                    nonZeroes = true;
                    if (dot) {
                        dot = false;
                        sb.append('.');
                    }
                    while (zeroes > 0) {
                        zeroes--;
                        sb.append('0');
                    }
                    sb.append(c);
                }

                index++;
                return true;
            }
        };

        boolean result = number.stream(AsciiDigitArraySink.of(sink));

        if (!sink.nonZeroes) { // number is zero
            sb.append('0');
        }
        sb.append("E+");
        sb.append(number.countDigits() - 1);

        return result;
    }

    public static CharSequence fromScientific(CharSequence str) {
        // we actually need this b/c the dot (.) changes indices
        var sb = new StringBuilder();
        int prev = -1;
        boolean subnormal = false; // 0.
        boolean negative = false;
        boolean seenNonZero = false;
        boolean seenDigit = false;
        boolean seenExponent = false;
        boolean seenPeriod = false;
        boolean seenDot = false;
        int exponent = 0;
        int magnitude = 0;
        int periodStart = -1;

        for (int i = 0, len = str.length(); i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '+' -> {
                    if (!(prev == -1 || prev == 'E' || prev == 'e' || prev == 'p' || prev == 'P')) {
                        throw new NumberFormatException("Not a scientific number (invalid positive sign): " + str + " at index " + i);
                    }
                    if (seenPeriod) {
                        periodStart = i + 1;
                    }
                }
                case '-' -> {
                    if (!(prev == -1)) {
                        throw new NumberFormatException("Not a scientific number (invalid negative sign): " + str + " at index " + i);
                    }
                    negative = true;
                }

                case 'E', 'e' -> {
                    if (seenExponent) {
                        throw new NumberFormatException("Not a scientific number (repeated exponent): " + str + " at index " + i);
                    }
                    if (seenPeriod) {
                        throw new NumberFormatException("Not a scientific number (exponent after period): " + str + " at index " + i);
                    }
                    seenExponent = true;
                }
                case 'P', 'p' -> {
                    if (seenPeriod) {
                        throw new NumberFormatException("Not a scientific number (repeated period): " + str + " at index " + i);
                    }
                    seenPeriod = true;
                    periodStart = i + 1;
                }
                case '.' -> {
                    if (seenDot) {
                        throw new NumberFormatException("Not a scientific number (repeated dot): " + str + " at index " + i);
                    }
                    if (seenExponent) {
                        throw new NumberFormatException("Not a scientific number (dot after exponent): " + str + " at index " + i);
                    }
                    if (seenPeriod) {
                        throw new NumberFormatException("Not a scientific number (dot after period): " + str + " at index " + i);
                    }
                    seenDot = true;
                    subnormal = !seenNonZero;
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (seenPeriod) {
                        // nothing to do
                    } else if (seenExponent) {
                        exponent = 10 * exponent + c - '0';
                        if (exponent > 999_999_999) {
                            throw new NumberFormatException("Not a scientific number (exponent overflow): " + str + " at index " + i);
                        }
                    } else {
                        seenDigit = true;
                        if (c == '0') {
                            if (subnormal && !seenNonZero) {
                                magnitude--;
                            }
                        } else {
                            seenNonZero = true;
                        }
                        if (seenNonZero) {
                            sb.append(c);
                            if (!seenDot) {
                                magnitude++;
                            }
                        }
                    }
                }
                default -> {
                    throw new NumberFormatException("Not a scientific number (invalid character): " + str + " at index " + i);
                }
            }
            prev = c;
        }

        if (!seenExponent) {
            throw new NumberFormatException("Not a scientific number (exponent missing): " + str);
        }
        if (!seenDigit) {
            throw new NumberFormatException("Not a scientific number (no digits): " + str);
        }
        if (!seenNonZero) {
            return "0";
        }

        int length = magnitude + exponent;
        int digits = sb.length();
        if (digits > length) {
            throw new NumberFormatException("Not a scientific number (loss of precision for integer): " + str);
        }

        if (seenPeriod) {
            int period = str.length() - periodStart;
            if (period == 0) {
                throw new NumberFormatException("Not a scientific number (empty period part): " + str);
            }
            int start = periodStart;
            if (negative) {
                return new AsciiString(length + 1) {

                    @Override
                    public char charAt(int index) {
                        if (--index == -1) {
                            return '-';
                        }
                        return charAtPeriod0(str, sb, digits, period, start, index);
                    }

                };
            }
            return new AsciiString(length) {

                @Override
                public char charAt(int index) {
                    return charAtPeriod0(str, sb, digits, period, start, index);
                }
            };
        }

        if (negative) {
            return new AsciiString(length + 1) {

                @Override
                public char charAt(int index) {
                    if (--index == -1) {
                        return '-';
                    }
                    return charAt0(sb, digits, index);
                }
            };
        }
        return new AsciiString(length) {

            @Override
            public char charAt(int index) {
                return charAt0(sb, digits, index);
            }
        };
    }

    private static char charAt0(StringBuilder sb, int digits, int index) {
        return index < digits ? sb.charAt(index) : '0';
    }

    private static char charAtPeriod0(CharSequence str, StringBuilder sb, int digits, int period, int start, int index) {
        return index < digits ? sb.charAt(index) : str.charAt(start + (index - digits) % period);
    }

    private static abstract class AsciiString implements CharSequence {

        private final int length;

        AsciiString(int length) {
            this.length = length;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            if (start == 0 && end == length) {
                return this;
            }
            return new AsciiString(end - start) {

                @Override
                public char charAt(int index) {
                    return AsciiString.this.charAt(start + index);
                }
            };
        }

        @SuppressWarnings("deprecation")
        @Override
        public String toString() {
            byte[] chars = new byte[length];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = (byte) charAt(i);
            }
            return new String(chars, 0);
        }
    }
}
