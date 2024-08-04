package philippag.lib.common.math.compint;

import java.math.BigDecimal;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;
import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitStreamable;

public class AsciiDigitsTest extends CommonTestBase {

    @Test
    public void fromScientific() {
        checkFromScientific("0", "0E0", "0E3", "000E5", "0.0000E777");
        checkFromScientific("1", "1E0");
        checkFromScientific("100000", "1E5");
        checkFromScientific("3", "+3E0");

        checkFromScientific("11000000000", "1.1E10", "+1.1E10", "1.1E+10", "+1.1E+10", "+0.0011E13");
        checkFromScientific("110000000000", "11E10");
        checkFromScientific("110000000000", "11E000000000000000000000000000010");

        checkFromScientific("888888888888888888888888888888888888888800", "8888888888888888888888888888888888888888E2", "8888888888888888888888888888888888888888.0E2");

        checkFromScientific("33333333333333333333333333333333300000000000000000000000000000000000000000000000000", "333333333333333333333333333333333E50");
        checkFromScientific("333333333333333333333333333333333000000000000000000", "3.33333333333333333333333333333333E50");

        checkFromScientific("500", "0.5E3", "5E2", "0.05E4", "500E0");
        checkFromScientific("500", "0.5E3", "5E2", "0.05E4", "500E0", "50E1", "0.0005E6", "0.0000005E9");

        checkFromScientific("700340000000", "70.034E10");
        checkFromScientific("700340", "70.034E4");
        checkFromScientific("70034", "70.034E3");

        checkFromScientific("800000000", "0.000000000000000000000000000000080E40");
    }

    @Test
    public void fromScientificPeriodic() {
        boolean p = true;
        checkFromScientific(p, "1000", "1E3", "1E3P0", "1E3P00", "1E3P00000");
        checkFromScientific(p, "1123123", "1E6P123");

        checkFromScientific(p, "155555555555555555555555555555555555555555555555555", "1E50P5", "1e+50p+5");
        checkFromScientific(p, "151051051051051051051051051051051051051051051051051", "1E50P510");
        checkFromScientific(p, "1568989898989898989898989898989", "1.56E30P89", "1.56e+30p+89");

        checkFromScientific(p, "13333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333", "1E100P3", "1e100p3");
        checkFromScientific(p, "27333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333333", "2.7E100P3");

        checkFromScientific(p, "0", "0E0", "0E0P456");
        checkFromScientific(p, "1", "1E0", "1E0P456");

        checkFromScientific(p, "77111111111", "0.077E12P1", "0.077E12P+1");
    }

    private static void checkFromScientific(String expected, String... inputs) {
        checkFromScientific(/*period*/ false, expected, inputs);
    }

    private static void checkFromScientific(boolean period, String expected, String... inputs) {
        for (String input : inputs) {
            checkFromScientific0(period, expected, input);
        }
    }

    private static void checkFromScientific0(boolean period, String expected, String input) {
        CharSequence actual = AsciiDigits.fromScientific(input);
        Assert.assertEquals(expected, actual.toString());
        Assert.assertTrue(stringsEqual(expected, actual));
        if (!period) {
            Assert.assertEquals(expected, new BigDecimal(input).toPlainString());
        }
        int length = actual.length();
        if (length >= 2) {
            var subActual = actual.subSequence(1, length);
            var subExpected = expected.subSequence(1, length);

            Assert.assertEquals(subExpected.toString(), subActual.toString());
            Assert.assertTrue(stringsEqual(subExpected, subActual));

            subActual = subActual.subSequence(0, length - 1);
            subExpected = subExpected.subSequence(0, length - 1);

            Assert.assertEquals(subExpected.toString(), subActual.toString());
            Assert.assertTrue(stringsEqual(subExpected, subActual));
        }
    }

    @Test
    public void fromScientificErrors() {
        checkFromScientificError("70.034E2"); // not an integer
        checkFromScientificError("x");
        checkFromScientificError(".");
        checkFromScientificError("+");
        checkFromScientificError("1");
        checkFromScientificError("E");
        checkFromScientificError("1E1000000000");
        checkFromScientificError("1E9999999999");
        checkFromScientificError("1E99999999999");
        checkFromScientificError("23E65+");
        checkFromScientificError("2+3E65");
        checkFromScientificError("24E3E6");
        checkFromScientificError("1.5.4E1");
        checkFromScientificError("E5");
        checkFromScientificError("1E4.5");
        checkFromScientificError("1P5");
        checkFromScientificError("1P5E1");
        checkFromScientificError("1PE1");
        checkFromScientificError("1E5P");
        checkFromScientificError("1EEE100");
        checkFromScientificError("1E++100");
        checkFromScientificError("5E60P++7");
    }

    private static void checkFromScientificError(String input) {
        try {
            var x = AsciiDigits.fromScientific(input);
            System.out.println(x);
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
    }

    @Test
    public void scientificInfinitePrecision() {
        int precision = Integer.MIN_VALUE;

        checkScientific("1E+0", "1", precision);
        checkScientific("0E+0", "0", precision);

        checkScientific("1.5E+4", "15000", precision);
        checkScientific("1.5E+9", "1500000000", precision);
        checkScientific("1.501E+9", "1501000000", precision);
        checkScientific("1.50100001E+9", "1501000010", precision);
        checkScientific("1.501000015E+9", "1501000015", precision);
    }

    @Test
    public void scientificNoTralingZeroes() {
        checkScientific("0E+0", "0", -1);
        checkScientific("0E+0", "0", -534);

        checkScientific("1.5E+4", "15000", 1);
        checkScientific("1.50E+4", "15000", 2);
        checkScientific("1.500E+4", "15000", 3);
        checkScientific("1.5E+4", "15000", -1);
        checkScientific("1.5E+4", "15000", -2);
        checkScientific("1.5E+4", "15000", -3);
        checkScientific("1.5E+4", "15000", -100);
        checkScientific("1.5E+4", "15000", Integer.MIN_VALUE);
    }

    @Test
    public void scientific() {
        checkScientific("0E+0", "0", 0);
        checkScientific("0E+0", "0", 21);

        checkScientific("1E+0", "1", 0);
        checkScientific("1E+0", "1", 1);
        checkScientific("1E+0", "1", 10);

        checkScientific("1E+1", "10", 0);
        checkScientific("1.0E+1", "10", 5);

        checkScientific("1E+3", "1000", 0);
        checkScientific("1.0E+3", "1000", 1);
        checkScientific("1.000E+3", "1000", 10000);

        checkScientific("1E+4", "15001", 0);
        checkScientific("1.5E+4", "15001", 1);
        checkScientific("1.50E+4", "15001", 2);
        checkScientific("1.500E+4", "15001", 3);

        checkScientific("1E+1", "15", 0);
        checkScientific("1.5E+1", "15", 1);
        checkScientific("1.5E+1", "15", 5);

        checkScientific("1E+2", "123", 0);
        checkScientific("1.2E+2", "123", 1);
        checkScientific("1.23E+2", "123", 2);

        checkScientific("5E+300", "5".repeat(301), 0);
        checkScientific("5.55E+300", "5".repeat(301), 2);

        checkScientific("1.23E+10002", "123" + "8".repeat(10_000), 2);
        checkScientific("1.238E+10002", "123" + "8".repeat(10_000), 3);
        checkScientific("1.2388888888E+10002", "123" + "8".repeat(10_000), 10);

        checkScientific("6E+10000002", "678" + "3".repeat(10_000_000), 0);
        checkScientific("6.7E+10000002", "678" + "3".repeat(10_000_000), 1);
        checkScientific("6.78333333333333333333E+10000002", "678" + "3".repeat(10_000_000), 20);
    }

    private static void checkScientific(String expected, String input, int precision) {
        checkScientific(expected, input, precision, Int9::fromString);
        checkScientific(expected, input, precision, IntAscii::fromString);
    }

    private static <T extends AsciiDigitStreamable> void checkScientific(String expected, String input, int precision, Function<String, T> factory) {
        var number = factory.apply(input);

        var sb = new StringBuilder();
        boolean precise = AsciiDigits.toScientific(sb, number, precision);
        String actual = sb.toString();

        Assert.assertEquals(expected, actual);

        String expectedFullString = new BigDecimal(actual).toPlainString();
        String actualFullString = number.toString();
        if (precise) {
            Assert.assertEquals(expectedFullString, actualFullString);
        }

        Assert.assertEquals(number.countDigits(), actualFullString.length());
    }
}
