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
        var sb = new StringBuilder(); // contains '-' and digits
        boolean negative = false;
        boolean subnormal = false; // 0.
        boolean seenNonZero = false;
        boolean seenDot = false;
        boolean prevDigit = false;
        int prev = -1;
        int magnitude = 0;
        int exponent = -1;
        int periodStart = -1;

        for (int i = 0, len = str.length(); i < len; i++) {
            char c = str.charAt(i);
            boolean digit = '0' <= c && c <= '9';

            if (digit) {
                if (periodStart != -1) {
                    // nothing to do
                } else if (exponent != -1) {
                    exponent = 10 * exponent + c - '0';
                    if (exponent > 999_999_999) {
                        throw new NumberFormatException("Not a scientific number (exponent overflow): " + str + " at index " + i);
                    }
                } else {
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
            } else {
                switch (c) {
                    case '+' -> {
                        if (prev == -1 || prev == 'E' || prev == 'e') {
                            // just ignore
                        } else if (prev == 'p' || prev == 'P') {
                            periodStart = i + 1;
                        } else {
                            throw new NumberFormatException("Not a scientific number (invalid positive sign): " + str + " at index " + i);
                        }
                    }
                    case '-' -> {
                        if (prev == -1) {
                            negative = true;
                            sb.append('-');
                        } else {
                            throw new NumberFormatException("Not a scientific number (invalid negative sign): " + str + " at index " + i);
                        }
                    }

                    case 'E', 'e' -> {
                        if (exponent != -1) {
                            throw new NumberFormatException("Not a scientific number (repeated exponent): " + str + " at index " + i);
                        }
                        if (periodStart != -1) {
                            throw new NumberFormatException("Not a scientific number (exponent after period): " + str + " at index " + i);
                        }
                        if (!prevDigit) {
                            throw new NumberFormatException("Not a scientific number (exponent after non-digit): " + str + " at index " + i);
                        }
                        exponent = 0;
                    }
                    case 'P', 'p' -> {
                        if (exponent == -1) {
                            throw new NumberFormatException("Not a scientific number (period without exponent): " + str + " at index " + i);
                        }
                        if (periodStart != -1) {
                            throw new NumberFormatException("Not a scientific number (repeated period): " + str + " at index " + i);
                        }
                        if (!prevDigit) {
                            throw new NumberFormatException("Not a scientific number (period after non-digit): " + str + " at index " + i);
                        }
                        periodStart = i + 1;
                    }
                    case '.' -> {
                        if (seenDot) {
                            throw new NumberFormatException("Not a scientific number (repeated dot): " + str + " at index " + i);
                        }
                        if (exponent != -1) {
                            throw new NumberFormatException("Not a scientific number (dot after exponent): " + str + " at index " + i);
                        }
                        if (periodStart != -1) {
                            throw new NumberFormatException("Not a scientific number (dot after period): " + str + " at index " + i);
                        }
                        seenDot = true;
                        subnormal = !seenNonZero;
                    }
                    default -> {
                        throw new NumberFormatException("Not a scientific number (invalid character): " + str + " at index " + i);
                    }
                }
            }

            prev = c;
            prevDigit = digit;
        }

        if (!prevDigit) {
            throw new NumberFormatException("Not a scientific number (ends with non-digit): " + str);
        } else if (!seenNonZero) {
            return "0"; // only zero digits, shorten to '0'
        } else if (exponent == -1 && !seenDot) {
            // a plain number
            return sb;
        }

        int length = magnitude + exponent + (negative ? 1 : 0);
        int digits = sb.length();
        if (digits > length) {
            throw new NumberFormatException("Not a scientific number (loss of precision for integer): " + str);
        }

        if (periodStart != -1) {
            int periodLength = str.length() - periodStart;
            assert periodLength > 0;
            int periodStart0 = periodStart;
            return new AsciiString(length) {

                @Override
                public char charAt(int index) {
                    return index < digits ? sb.charAt(index) : str.charAt(periodStart0 + (index - digits) % periodLength);
                }
            };
        } else {
            return new AsciiString(length) {

                @Override
                public char charAt(int index) {
                    return index < digits ? sb.charAt(index) : '0';
                }
            };
        }
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
