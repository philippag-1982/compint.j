package philippag.lib.common.math.compint;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;
import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitArraySink;

public class Int9Test extends CommonTestBase {

    private static final Int9 ZERO         = Int9.fromInt(0);
    private static final Int9 ONE          = Int9.fromInt(1);
    private static final Int9 NEGATIVE_ONE = Int9.fromInt(-1);
    private static final Int9 INT_MAX      = Int9.fromInt(Integer.MAX_VALUE);
    private static final Int9 INT_MIN      = Int9.fromInt(Integer.MIN_VALUE);
    private static final Int9 LONG_MAX     = Int9.fromLong(Long.MAX_VALUE);
    private static final Int9 LONG_MIN     = Int9.fromLong(Long.MIN_VALUE);

    private static boolean canAddInPlace(Int9 lhs, Int9 rhs) {
        return true;
    }

    private static boolean canSubtractInPlace(Int9 lhs, Int9 rhs) {
        return true;
    }

    @BeforeClass
    public static void constantsBefore() {
        checkConstants();
    }

    @AfterClass
    public static void constantsAfter() {
        checkConstants();
    }

    private static void checkConstants() {
        // self-test this test didn't modify it's own constants
        Assert.assertEquals("0", ZERO.toString());
        Assert.assertEquals("1", ONE.toString());
        Assert.assertEquals("-1", NEGATIVE_ONE.toString());
        Assert.assertEquals(""+Integer.MAX_VALUE, INT_MAX.toString());
        Assert.assertEquals(""+Long.MAX_VALUE, LONG_MAX.toString());
        Assert.assertEquals(""+Integer.MIN_VALUE, INT_MIN.toString());
        Assert.assertEquals(""+Long.MIN_VALUE, LONG_MIN.toString());
    }

    @Test
    public void addMulNoWriteThrough() {
        {
            var a = Int9.fromLong(5);
            var b = Int9.fromLong(0);
            var c = Int9.add(a, b);
            checkValue(5, c);
            a.incrementInPlace();
            checkValue(5, c);
            checkValue(6, a);
        }
        {
            var a = Int9.fromLong(0);
            var b = Int9.fromLong(6);
            var c = Int9.add(a, b);
            checkValue(6, c);
            b.incrementInPlace();
            checkValue(6, c);
            checkValue(7, b);
        }
        {
            var a = Int9.fromLong(10);
            var b = Int9.fromLong(0);
            var c = Int9.subtract(a, b);
            checkValue(10, c);
            a.incrementInPlace();
            checkValue(10, c);
            checkValue(11, a);
        }
        {
            var a = Int9.fromLong(20);
            var b = Int9.fromLong(0);
            var c = Int9.multiplySimple(a, b);
            checkValue(0, c);
            b.incrementInPlace();
            checkValue(0, c);
            checkValue(1, b);
        }
        {
            var a = Int9.fromLong(20);
            var b = Int9.fromLong(1);
            var c = Int9.multiplySimple(a, b);
            checkValue(20, c);
            a.incrementInPlace();
            checkValue(20, c);
            checkValue(21, a);
        }
        {
            var a = Int9.fromLong(1L << 60);
            var b = Int9.fromLong(1);
            var c = Int9.multiplyKaratsuba(a, b, 1);
            checkValue(1L << 60, c);
            a.incrementInPlace();
            checkValue(1L << 60, c);
            checkValue((1L << 60) + 1, a);
        }
        {
            var a = Int9.fromLong(1L << 60);
            var b = Int9.fromLong(0);
            var c = Int9.multiplySimple(a, b);
            checkValue(0, c);
            b.incrementInPlace();
            checkValue(0, c);
            checkValue(1, b);
        }
        {
            var a = Int9.fromLong(1L << 60);
            var b = Int9.fromLong(0);
            var c = Int9.multiplyKaratsuba(a, b, 1);
            checkValue(0, c);
            b.incrementInPlace();
            checkValue(0, c);
            checkValue(1, b);
        }
    }

    @Test
    public void divideInPlaceSigned() {
        checkDivideInPlace(1, 1);
        checkDivideInPlace(1, -1);
        checkDivideInPlace(-1, 1);
        checkDivideInPlace(-1, -1);
        checkDivideInPlace(-100, -50);
        checkDivideInPlace(-100, -51);
        checkDivideInPlace(100, -50);
        checkDivideInPlace(100, -51);
        checkDivideInPlace(-100, 50);
        checkDivideInPlace(-100, 51);
        checkDivideInPlace(1, 10);
        checkDivideInPlace(1, Integer.MAX_VALUE);
        checkDivideInPlace(0, 1);
        checkDivideInPlace(0, -1);
        checkDivideInPlace(0, 10);
        checkDivideInPlace(0, -10);
        checkDivideInPlace(1, Integer.MIN_VALUE);

        try {
            Int9.fromInt(10).divideInPlace(0);
            Assert.fail("Expecting ArithmeticException");
        } catch (ArithmeticException e) {
            System.out.println(e);
        }
    }

    @Test
    public void divideInPlaceBy3Exact() {
        for (int i = 0; i < 10_000_000; i++) {
            checkDivideInPlace(i, 3);
            checkDivideInPlaceBy3Exact(i);
        }

        for (long i = Long.MAX_VALUE - 10_000; i < Long.MAX_VALUE; i++) {
            checkDivideInPlace(i, 3);
            checkDivideInPlaceBy3Exact(i);
        }
    }

    @Test
    public void divideInPlace() {
        checkDivideInPlace(1_000_000_000_000_000_000L, 3);
        checkDivideInPlace(1_000_000_000_000L, 3);
        checkDivideInPlace(1_000, 3);

        checkDivideInPlace(9_000_000_000_000_000_123L, 3);
        checkDivideInPlace(9_000_000_000_000_000_000L, 3);
        checkDivideInPlace(9_000_000_000_000_000_001L, 3);

        checkDivideInPlace(1_000, 2);
        checkDivideInPlace(1_000, 4);
        checkDivideInPlace(1_000, 8);
        checkDivideInPlace(1_000, 16);
        checkDivideInPlace(1_000, 32);

        checkDivideInPlace(1_000, 10);
        checkDivideInPlace(1_000, 100);
        checkDivideInPlace(1_000, 100);

        checkDivideInPlace(1_000_000_000_000L, 100);
        checkDivideInPlace(1_000_000_000_000L, 1_000);
        checkDivideInPlace(1_000_000_000_000L, 100_000);
        checkDivideInPlace(1_000_000_000_000L, 200_000);
        checkDivideInPlace(9_000_000_000_000_000_000L, 200_000);
        checkDivideInPlace(9_000_000_000_000_000_123L, 200_000);
        checkDivideInPlace(1_234_567_890_123_456_789L, 200_000);
        checkDivideInPlace(1_234_567_890_123_456_789L, 3);
        checkDivideInPlace(1_234_567_890_123_456_789L, 7);
        checkDivideInPlace(1_234_567_890_123_456_789L, 131);
        checkDivideInPlace(1_234_567_890_123_456_789L, 1);
        checkDivideInPlace(1_234_567_890_123_456_789L, 231213);
    }

    @Test
    public void divideInPlaceBig() {
        checkDivideInPlace("11111122222233333334444445555555666666677777778888888", "12345");
        checkDivideInPlace("9".repeat(100), "12345");
        checkDivideInPlace("9".repeat(100), "123456789");
        checkDivideInPlace("9".repeat(10_000), "12345");
        checkDivideInPlace("9".repeat(100_000), "123456789");
        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", "1234567890");
        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", ""+Integer.MAX_VALUE);

        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", "2");
        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", "4");
        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", "64");

        checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", ""+Integer.MIN_VALUE);
        checkDivideInPlace("-11111122222233333334444445555555666666677777778888888999999111111122222333", ""+Integer.MIN_VALUE);

        checkDivideInPlace("5" + "0".repeat(1_000), "666666666");
        checkDivideInPlace("5" + "0".repeat(10_000), "3333");
    }

    @Test
    @Ignore
    public void divideInPlaceBigRandomExhaustive() {
        int REPEATS = 1;
        var rnd = new Random();

        while (REPEATS-- > 0) {
            long dividend = random(rnd, 1, Integer.MAX_VALUE-1);
            checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", ""+dividend);

            for (int i = Integer.MAX_VALUE; i >= 0; i -= random(rnd, 1, 500)) {
                checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", ""+i);
                checkDivideInPlace(randomNumericString(rnd, 1, 1_000), ""+i);
                checkDivideInPlace("11111122222233333334444445555555666666677777778888888999999111111122222333", "-"+i);
                checkDivideInPlace(randomNumericString(rnd, 1, 1_000), "-"+i);
                if (i%10000==0) {
                    System.out.println(i);
                }
            }
        }
    }

    private static void checkDivideInPlaceBy3Exact(long divisor) {
        checkDivideInPlaceBy3Exact(""+divisor);
    }

    private static void checkDivideInPlaceBy3Exact(String divisorStr) {
        var divisor = Int9.fromString(divisorStr).multiply(Int9.fromInt(3));
        int remainder = divisor.divideInPlace(3);
        Assert.assertEquals(divisorStr, divisor.toString());
        Assert.assertEquals(0, remainder);
    }

    private static void checkDivideInPlace(long divisor, int dividend) {
        long quotient  = divisor / dividend;
        long remainder = divisor % dividend;
        var x = Int9.fromLong(divisor);
        long mod = x.divideInPlace(dividend);
        Assert.assertEquals(""+quotient, x.toString());
        Assert.assertEquals(quotient, x.toLong());
        Assert.assertEquals(remainder, mod);

        checkDivideInPlace(""+dividend, ""+dividend);
    }

    private static void checkDivideInPlace(String divisor, String dividend) {
        var divideInPlace = new BigInteger(divisor).divideAndRemainder(new BigInteger(dividend));
        var quotient = divideInPlace[0];
        var remainder = divideInPlace[1];
        var x = Int9.fromString(divisor);
        long mod = x.divideInPlace(Integer.parseInt(dividend));
        Assert.assertEquals(quotient.toString(), x.toString());
        Assert.assertEquals(remainder.longValueExact(), mod);
    }

    @Test
    public void subSeqNoWriteThrough() {
        String original = "123456789012345678901234567890123456789012345678901234567890";
        var x = Int9.fromString(original);
        var y = x.subSequence(1, 11);
        Assert.assertEquals("2345678901", y.toString());

        y.addInPlace(Int9.fromInt(10));

        Assert.assertEquals("2345678911", y.toString());
        Assert.assertEquals(original, x.toString());

        y.addInPlace(Int9.fromString("5".repeat(200)));
        Assert.assertEquals("55555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555557901234466", y.toString());
        Assert.assertEquals(original, x.toString());
    }

    @Test
    public void subSequence() {
        checkSubSequence("123", "123", 0, 3);
        checkSubSequence("12", "123", 0, 2);
        checkSubSequence("23", "123", 1, 3);
        checkSubSequence("2", "123", 1, 2);

        checkSubSequence("1234567890", "1234567890", 0, 10);
        checkSubSequence("123456789", "1234567890", 0, 9);
        checkSubSequence("789", "1234567890", 6, 9);

        checkSubSequence("111222333444555000666777888999000111", "111222333444555000666777888999000111");
        checkSubSequence("111222333444555000666777888999000111", "111222333444555000666777888999000111", 0, 36);
        checkSubSequence("5000666777888999000111", "111222333444555000666777888999000111", 14, 36);
        checkSubSequence("666777888999000111", "111222333444555000666777888999000111", 15, 36);
        checkSubSequence("666777888999000111", "111222333444555000666777888999000111", 16, 36);
        checkSubSequence("666777888999000111", "111222333444555000666777888999000111", 17, 36);
        checkSubSequence("666777888999000111", "111222333444555000666777888999000111", 18, 36);
        checkSubSequence("66777888999000111", "111222333444555000666777888999000111", 19, 36);
        checkSubSequence("66777888999000", "111222333444555000666777888999000111", 19, 33);
    }

    private static void checkSubSequence(String expected, String input) {
        checkSubSequence(expected, input, 0, input.length());
    }

    private static void checkSubSequence(String expected, String input, int start, int end) {
        Assert.assertTrue(stringsEqual(expected, Int9.fromString(input).subSequence(start, end)));
        Assert.assertTrue(stringsEqual(expected, Int9.fromString(input, start, end)));
    }

    @Test
    public void charAt() {
        checkCharAt("1");
        checkCharAt("12");
        checkCharAt("123");
        checkCharAt("1234");
        checkCharAt("12345");
        checkCharAt("123456");
        checkCharAt("1234567");
        checkCharAt("12345678");
        checkCharAt("123456789");
        checkCharAt("1234567899");
        checkCharAt("12345678998");
        checkCharAt("123456789987");
        checkCharAt("1234567899876");
        checkCharAt("12345678998765");
        checkCharAt("123456789987654");
        checkCharAt("1234567899876543");
        checkCharAt("12345678998765432");
        checkCharAt("123456789987654321");
        checkCharAt("1234567899876543210");
        checkCharAt("12345678998765432100");
        checkCharAt("9223372036854775808");
    }

    private static void checkCharAt(String str) {
        checkCharAt0(str);
        checkCharAt0("-" + str);
    }

    private static void checkCharAt0(String str) {
        var x = Int9.fromString(str);
        Assert.assertEquals(str.length(), x.length());
        for (int i = 0; i < str.length(); i++) {
            Assert.assertEquals("#" + i, str.charAt(i), x.charAt(i));
        }

        Assert.assertTrue(stringsEqual(str, x));
    }

    @Test
    public void rightPartRegression() {
        var x = Int9.fromScientific("1E30");
        for (int n = 1; n < 8; n++) {
            Assert.assertEquals("0", x.rightPart(n).toString());
        }
        for (int n = 8; n < 10; n++) {
            Assert.assertSame(x, x.rightPart(n));
        }
    }

    @Test
    public void leftRightPart() {
        checkLeftRightPart("[1, 2]", "[3, 4]", "[0]", "[5, 6]", new int[] {1,2,3,4}, new int[] {5,6});
        checkLeftRightPart("[1]", "[2, 3]", "[5, 6, 7]", "[8, 9]", new int[] {1,2,3}, new int[] {5,6,7,8,9});
        checkLeftRightPart("[0]", "[1, 2]", "[1, 2, 3]", "[4, 5, 6]", new int[] {1,2}, new int[] {1,2,3,4,5,6});
        checkLeftRightPart("[0]", "[8]", "[1, 2]", "[3, 4]", new int[] {8}, new int[] {1,2,3,4});
        checkLeftRightPart("[1, 2]", "[3, 4]", "[0]", "[8]", new int[] {1,2,3,4}, new int[] {8});
        checkLeftRightPart("[1]", "[2, 3]", "[1, 2, 3]", "[4, 5]", new int[] {1,2,3}, new int[] {1,2,3,4,5});
        checkLeftRightPart("[0]", "[1, 2, 3]", "[1, 2, 3, 4]", "[5, 6, 7, 8]", new int[] {1,2,3}, new int[] {1,2,3,4,5,6,7,8});
        checkLeftRightPart("[0]", "[1, 2, 3]", "[1, 2, 3, 4, 5]", "[6, 7, 8, 9]", new int[] {1,2,3}, new int[] {1,2,3,4,5,6,7,8,9});
        checkLeftRightPart("[1, 2, 0]", "[6]", "[4, 5, 6]", "[6]", new int[] {1,2,0,0,0,6}, new int[] {4,5,6,0,0,6});
    }

    private static void checkLeftRightPart(String aExp, String bExp, String cExp, String dExp, int[] lhsValues, int[] rhsValues) {
        checkLeftRightPart0(aExp, bExp, cExp, dExp, lhsValues, rhsValues);
        checkLeftRightPart0(cExp, dExp, aExp, bExp, rhsValues, lhsValues);
    }

    private static void checkLeftRightPart0(String aExp, String bExp, String cExp, String dExp, int[] lhsValues, int[] rhsValues) {
        var lhs = new Int9(lhsValues);
        var rhs = new Int9(rhsValues);

        int n = Math.max(lhsValues.length, rhsValues.length);

        var a = lhs.leftPart(n);
        var b = lhs.rightPart(n);
        var c = rhs.leftPart(n);
        var d = rhs.rightPart(n);

        Assert.assertEquals(aExp, a.toArrayString());
        Assert.assertEquals(bExp, b.toArrayString());
        Assert.assertEquals(cExp, c.toArrayString());
        Assert.assertEquals(dExp, d.toArrayString());
    }

    @Test
    @Ignore
    public void anatolyDemo() {
        long x = 999412;
        long y = 12349933L;
        long z = Karatsuba.alexeyevich(x, y);
        System.out.printf("%d\n", z);
        System.out.printf("%d\n", x * y);

        var lhs = new Int9(9,9,9,4,1,2);
        var rhs = new Int9(1,2,3,4,9,9,3,3);

        var res = Int9.multiplyKaratsuba(lhs, rhs);
        System.out.println(res);
    }

    @Test
    public void anatoly() {
        Random rnd = new Random();
        int repeats = 100_000;
        while (repeats-- > 0) {
            long a = random(rnd, 0L, Integer.MAX_VALUE);
            long b = random(rnd, 0L, Integer.MAX_VALUE);
            long c = Karatsuba.alexeyevich(a, b);
            Assert.assertEquals(a * b, c);
        }
    }

    @Test
    public void errors() {
        try {
            Int9.fromString("");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("  ");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("-");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("+");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("5X3");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("5X");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            Int9.fromString("99999999X");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
    }

    private static void eq(Int9 lhs, Int9 rhs) {
        Assert.assertTrue(0 == lhs.compareTo(rhs));
        Assert.assertTrue(0 == rhs.compareTo(lhs));
        Assert.assertTrue(lhs.equals(rhs));
        Assert.assertTrue(rhs.equals(lhs));
    }

    @Test
    public void toInt() {
        eq(Int9.fromString(""+Integer.MAX_VALUE), INT_MAX);
        eq(Int9.fromString(""+Integer.MIN_VALUE), INT_MIN);

        int unmappable = Integer.MIN_VALUE;

        checkInt(0);
        checkInt(5634534);
        checkInt(Integer.MAX_VALUE - 1);
        checkInt(Integer.MAX_VALUE);
        checkInt(/*mappable*/ false, unmappable, "2147483648");
        checkInt(/*mappable*/ false, unmappable, "2147483649");
        checkInt(/*mappable*/ false, unmappable, "2147483649645");
        checkInt(Integer.MIN_VALUE);
        checkInt(Integer.MIN_VALUE + 1);
        checkInt(Integer.MIN_VALUE + 10);

        checkInt(-654);
    }

    private static void checkInt(int expected) {
        checkInt(/*mappable*/ true, expected, ""+expected);
    }

    private static void checkInt(boolean mappable, int expected, String s) {
        var i = Int9.fromString(s);
        Assert.assertEquals(expected, i.toInt());
        Assert.assertEquals(mappable, i.isInt());
    }

    @Test
    public void toLong() {
        eq(Int9.fromString(""+Long.MAX_VALUE), LONG_MAX);
        eq(Int9.fromString(""+Long.MIN_VALUE), LONG_MIN);

        long unmappable = Long.MIN_VALUE;

        checkLong(0);
        checkLong(5634534);
        checkLong(Integer.MAX_VALUE);
        checkLong(0xFFFF_FFFFL);
        checkLong(Long.MAX_VALUE - 1);
        checkLong(Long.MAX_VALUE);
        checkLong(/*mappable*/ false, unmappable, "9223372036854775808");
        checkLong(/*mappable*/ false, unmappable, "9223372036854775832");

        checkLong(/*mappable*/ true, -5, "-5");

        checkLong(/*mappable*/ true, -5, "-005");
        checkLong(/*mappable*/ true, -5, "-000000000000000000000000000005");
        checkLong(-3333333333333333L);

        checkLong(Long.MIN_VALUE / 10);
        checkLong(Long.MIN_VALUE + 1);

        checkLong(Long.MIN_VALUE);
    }

    private static void checkLong(long expected) {
        checkLong(/*mappable*/ true, expected, ""+expected);
    }

    private static void checkLong(boolean mappable, long expected, String s) {
        var i = Int9.fromString(s);
        Assert.assertEquals(expected, i.toLong());
        Assert.assertEquals(mappable, i.isLong());
    }

    @Test
    public void forModification() {
        var x = Int9.forDigits(1_000_000);
        x.addInPlace(Int9.fromString(AsciiDigits.fromScientific("2.5E500000")));
        Assert.assertEquals("2.5E+500000", x.toScientific(1).toString());
        Assert.assertEquals(500001, x.countDigits());

        x.addInPlace(Int9.fromString(AsciiDigits.fromScientific("5.4E500002")));
        Assert.assertEquals("5.425E+500002", x.toScientific(Integer.MIN_VALUE).toString());

        x.addInPlace(Int9.fromString(AsciiDigits.fromScientific("1.1E500002")));
        Assert.assertEquals("6.525E+500002", x.toScientific(Integer.MIN_VALUE).toString());

        x.addInPlace(Int9.fromString(AsciiDigits.fromScientific("9.8E500002")));
        Assert.assertEquals("1.6325E+500003", x.toScientific(Integer.MIN_VALUE).toString());

        x.addInPlace(ZERO);
        Assert.assertEquals("1.6325E+500003", x.toScientific(Integer.MIN_VALUE).toString());

        x.addInPlace(ONE);
        Assert.assertEquals("1.6325" + "0".repeat(500003 - 4 - 1) + "1E+500003", x.toScientific(Integer.MIN_VALUE).toString());
    }

    @Test
    public void inPlaceLong() {
        checkInPlaceLong(() -> Int9.fromInt(0));
        checkInPlaceLong(() -> Int9.forDigits(1));
    }

    private static void checkInPlaceLong(Supplier<Int9> x) {
        checkInPlaceLong(x.get());
        checkIncrementResize(x.get());
        checkDecrementResize(x.get());
        checkDecrementResize2(x.get());
    }

    private static void checkInPlaceLong(Int9 x) {
        x.addInPlace(5);
        checkValue(5, x);

        x.addInPlace(999_999_999);
        checkValue(999_999_999 + 5, x);

        x.addInPlace(Integer.MAX_VALUE);
        checkValue(999_999_999L + 5 + Integer.MAX_VALUE, x);

        x.clear();
        x.addInPlace(Integer.MAX_VALUE);
        checkValue(Integer.MAX_VALUE, x);

        x.addInPlace(Integer.MAX_VALUE);
        checkValue(Integer.MAX_VALUE * 2L, x);

        x.addInPlace(Integer.MAX_VALUE);
        checkValue(Integer.MAX_VALUE * 3L, x);

        x.clear();
        x.setValue(Int9.fromInt(1995306191));
        x.addInPlace(2046997207);
        checkValue(1995306191L + 2046997207, x);

        x.clear();
        x.setValue(Int9.fromLong(999_999_999_999_999_999L));
        x.addInPlace(Integer.MAX_VALUE);
        checkValue(Math.addExact(999_999_999_999_999_999L, Integer.MAX_VALUE), x);

        x.clear();
        x.setValue(Int9.fromLong(999_999_999_999_999_999L));
        x.addInPlace(1);
        checkValue(1_000_000_000_000_000_000L, x);

        x.clear();
        x.incrementInPlace();
        checkValue(1, x);

        x.setValue(Int9.fromLong(999_999_999_999_999_999L));
        x.incrementInPlace();
        checkValue(1_000_000_000_000_000_000L, x);

        x.setValue(NEGATIVE_ONE);
        x.addInPlace(ONE);
        checkValue(0, x);

        x.setValue(NEGATIVE_ONE);
        x.incrementInPlace();
        checkValue(0, x);

        x.setValue(Int9.fromInt(-10));
        x.incrementInPlace();
        checkValue(-9, x);

        x.setValue(Int9.fromInt(10));
        x.decrementInPlace();
        checkValue(9, x);

        x.setValue(Int9.fromInt(-10));
        x.decrementInPlace();
        checkValue(-11, x);
    }

    private static void checkIncrementResize(Int9 x) {
        x.setValue(Int9.fromLong(1_000_000_000 - 2));
        Assert.assertEquals("Int9 {digits=9, negative=false, offset=0, length=1, capacity=1, data=[999999998]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=9, negative=false, offset=0, length=1, capacity=1, data=[999999999]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=10, negative=false, offset=0, length=2, capacity=2, data=[1, 0]}", x.toDebugString());

        x.setValue(Int9.fromLong(1_000_000_000L*1_000_000_000L - 2));
        Assert.assertEquals("Int9 {digits=18, negative=false, offset=0, length=2, capacity=2, data=[999999999, 999999998]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=18, negative=false, offset=0, length=2, capacity=2, data=[999999999, 999999999]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=19, negative=false, offset=0, length=3, capacity=3, data=[1, 0, 0]}", x.toDebugString());
    }

    private static void checkDecrementResize(Int9 x) {
        x.decrementInPlace();
        checkValue(-1, x);
        x.decrementInPlace();
        checkValue(-2, x);
        x.incrementInPlace();
        checkValue(-1, x);
        x.incrementInPlace();
        checkValue(0, x);
        x.incrementInPlace();
        checkValue(1, x);

        x.setValue(Int9.fromInt(1_000_000_000));
        x.decrementInPlace();
        checkValue(999_999_999, x);
    }

    private static void checkDecrementResize2(Int9 x) {
        x.setValue(Int9.fromInt(-999_999_999));
        Assert.assertEquals("Int9 {digits=9, negative=true, offset=0, length=1, capacity=1, data=[999999999]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=9, negative=true, offset=0, length=1, capacity=1, data=[999999998]}", x.toDebugString());
        x.decrementInPlace();
        Assert.assertEquals("Int9 {digits=9, negative=true, offset=0, length=1, capacity=1, data=[999999999]}", x.toDebugString());
        x.decrementInPlace();
        Assert.assertEquals("Int9 {digits=10, negative=true, offset=0, length=2, capacity=2, data=[1, 0]}", x.toDebugString());
    }

    @Test
    public void inPlace() {
        checkInPlace(() -> Int9.fromInt(0));
        checkInPlace(() -> Int9.forDigits(1));
    }

    private static void checkInPlace(Supplier<Int9> x) {
        checkInPlace(x.get());
    }

    private static void checkInPlace(Int9 x) {
        int n = 100;
        for (int i = 0; i < n; i++) {
            x.addInPlace(ZERO);
        }
        checkValue(0, x);

        for (int i = 0; i < n; i++) {
            x.addInPlace(ONE);
        }
        checkValue(n, x);

        for (int i = 0; i < 100_000; i++) {
            x.addInPlace(ONE);
        }
        checkValue(100_000 + n, x);

        var y = Int9.fromLong(1_000_000_000_000_000L);
        for (int i = 0; i < 10_000_000; i++) {
            x.addInPlace(y);
        }
        checkValue("10000000000000000100100", x);

        var z = x.copy();

        for (int i = 0; i < 10_000_000; i++) {
            x.addInPlace(z);
        }
        checkValue("100000010000000001001000100100", x);

        x.clear();
        checkValue(0, x);

        x.addInPlace(Int9.fromInt(5000));
        x.subtractInPlace(Int9.fromInt(4999));
        checkValue(1, x);

        x.subtractInPlace(ONE);
        checkValue(0, x);
        x.subtractInPlace(ONE);
        checkValue(-1, x);
        x.subtractInPlace(Int9.fromInt(4999));
        checkValue(-5000, x);
        x.subtractInPlace(ZERO);
        checkValue(-5000, x);

        x.clear();
        x.addInPlace(Int9.fromInt(1_000_000));
        while (x.compareTo(ZERO) > 0) {
            x.subtractInPlace(ONE);
        }
        checkValue(0, x);

        x.addInPlace(x);
        checkValue(0, x);

        x.addInPlace(Int9.fromString("1000000000000000000000000"));
        checkValue("1000000000000000000000000", x);
        x.addInPlace(x);
        checkValue("2000000000000000000000000", x);

        x.subtractInPlace(x);
        checkValue(0, x);

        x.subtractInPlace(ONE);
        checkValue(-1, x);
        x.subtractInPlace(x);
        checkValue(0, x);

        x.addInPlace(Int9.fromInt(-1000));
        checkValue(-1000, x);
        x.addInPlace(x);
        checkValue(-2000, x);
    }

    @Test
    public void setValue() {
        checkSetValue(() -> Int9.fromInt(0));
        checkSetValue(() -> Int9.forDigits(1));
    }

    private static void checkSetValue(Supplier<Int9> x) {
        checkSetValue(x.get());
        checkSetValueLong(x.get());
    }

    private static void checkSetValue(Int9 x) {
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[0]}", x.toDebugString());
        x.setValue(Int9.fromInt(2));
        checkValue(2, x);
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[2]}", x.toDebugString());

        x.setValue(Int9.fromString("200000000000000000000000000000000000000000000001"));
        checkValue("200000000000000000000000000000000000000000000001", x);
        Assert.assertEquals("Int9 {digits=48, negative=false, offset=0, length=6, capacity=6, data=[200, 0, 0, 0, 0, 1]}", x.toDebugString());

        x.setValue(Int9.fromInt(2));
        checkValue(2, x);
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=5, length=1, capacity=6, data=[2]}", x.toDebugString());

        x.setValue(Int9.fromString("20000000000000000000000000000000000000000000000"));
        checkValue("20000000000000000000000000000000000000000000000", x);
        Assert.assertEquals("Int9 {digits=47, negative=false, offset=0, length=6, capacity=6, data=[20, 0, 0, 0, 0, 0]}", x.toDebugString());

        x.setValue(Int9.fromInt(2));
        checkValue(2, x);
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=5, length=1, capacity=6, data=[2]}", x.toDebugString());

        x.setValue(Int9.fromLong(-5555555555555555L));
        checkValue(-5555555555555555L, x);
        Assert.assertEquals("Int9 {digits=16, negative=true, offset=4, length=2, capacity=6, data=[5555555, 555555555]}", x.toDebugString());

        x.setValue(Int9.fromString(AsciiDigits.fromScientific("1E100")));
        checkValue(AsciiDigits.fromScientific("1E100").toString(), x);
        Assert.assertEquals("Int9 {digits=101, negative=false, offset=0, length=12, capacity=12, data=[10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]}", x.toDebugString());

        var y = Int9.forDigits(100);
        y.setValue(Int9.fromInt(1000));
        Assert.assertEquals("Int9 {digits=4, negative=false, offset=11, length=1, capacity=12, data=[1000]}", y.toDebugString());

        x.setValue(y);
        Assert.assertEquals("Int9 {digits=4, negative=false, offset=11, length=1, capacity=12, data=[1000]}", x.toDebugString());

        y = Int9.forDigits(50);
        y.setValue(Int9.fromInt(2000));
        Assert.assertEquals("Int9 {digits=4, negative=false, offset=5, length=1, capacity=6, data=[2000]}", y.toDebugString());

        x.setValue(y);
        Assert.assertEquals("Int9 {digits=4, negative=false, offset=11, length=1, capacity=12, data=[2000]}", x.toDebugString());
    }

    private static void checkSetValueLong(Int9 x) {
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[0]}", x.toDebugString());
        x.setValue(500);
        Assert.assertEquals("Int9 {digits=3, negative=false, offset=0, length=1, capacity=1, data=[500]}", x.toDebugString());
        x.setValue(-500);
        Assert.assertEquals("Int9 {digits=3, negative=true, offset=0, length=1, capacity=1, data=[500]}", x.toDebugString());
        x.setValue(1_000_000_000 - 1);
        Assert.assertEquals("Int9 {digits=9, negative=false, offset=0, length=1, capacity=1, data=[999999999]}", x.toDebugString());
        x.setValue(1_000_000_000);
        Assert.assertEquals("Int9 {digits=10, negative=false, offset=0, length=2, capacity=2, data=[1, 0]}", x.toDebugString());
        x.setValue(1_000_000_000 - 1);
        Assert.assertEquals("Int9 {digits=9, negative=false, offset=1, length=1, capacity=2, data=[999999999]}", x.toDebugString());

        x.setValue(Long.MAX_VALUE);
        Assert.assertEquals("Int9 {digits=19, negative=false, offset=0, length=3, capacity=3, data=[9, 223372036, 854775807]}", x.toDebugString());
        x.setValue(Long.MIN_VALUE);
        Assert.assertEquals("Int9 {digits=19, negative=true, offset=0, length=3, capacity=3, data=[9, 223372036, 854775808]}", x.toDebugString());

        x.setValue(NEGATIVE_ONE);
        Assert.assertEquals("Int9 {digits=1, negative=true, offset=2, length=1, capacity=3, data=[1]}", x.toDebugString());
    }

    private static void checkValue(long expected, Int9 value) {
        Assert.assertEquals(expected, value.toLong());
        Assert.assertEquals(""+expected, value.toString());
    }

    private static void checkValue(String expected, Int9 value) {
        Assert.assertEquals(expected, value.toString());
    }

    @Test
    public void addStr() {
        checkAddStr("0", ""+Long.MIN_VALUE);
        checkAddStr("0", ""+Long.MAX_VALUE);
        checkAddStr("-1", "1");
        checkAddStr("-1", "0");
        checkAddStr("1", "0");

        checkAddStr("10", "9999999990");
        checkAddStr("6456456456456456", "54354353454356546");
        checkAddStr("0", "0");
        checkAddStr("10", "20");
        checkAddStr("12345", "56789");
        checkAddStr("1234567890123", "1234567890456");
        checkAddStr("99999999", "99999999");
        checkAddStr("999999999", "999999999");
        checkAddStr("6234567890123", "8234567890456");
        checkAddStr("5435345435455", "7657567657657");
    }

    @Test
    public void addStrNeg() {
        checkAddStr("1", "-2");

        checkAddStr("-100", "20");
        checkAddStr("-100", "200");
        checkAddStr("-10000000000000", "200");
        checkAddStr("-100", "2000000000000");
        checkAddStr("-100", "100");

        checkAddStr("100", "-10");
        checkAddStr("100", "-200");
        checkAddStr("10000000000000", "-2000000000000");
        checkAddStr("100", "-200");
        checkAddStr("200", "-200");

        checkAddStr("-100", "-30");
        checkAddStr("-100", "-300");
        checkAddStr("-10000000000000", "-30");
        checkAddStr("-100", "-2000000000000");
        checkAddStr("-2000000000000", "-2000000000000");
    }

    @Test
    public void compareTo() {
        checkCompareTo(0, 0);
        checkCompareTo(0, 54);
        checkCompareTo(0, -57);
        checkCompareTo(1, 1);
        checkCompareTo(-1, 1);
        checkCompareTo(1, -1);
        checkCompareTo(-1, -1);
        checkCompareTo(1, 10);
        checkCompareTo(-1, 100);
        checkCompareTo(1, -1000);
        checkCompareTo(-1, -10000);
        checkCompareTo(10, 1);
        checkCompareTo(-100, 1);
        checkCompareTo(1000, -1);
        checkCompareTo(-1000, -1);

        checkCompareTo(Long.MIN_VALUE - 1, Long.MIN_VALUE);
        checkCompareTo(Long.MIN_VALUE, Long.MIN_VALUE - 1);
        checkCompareTo(Long.MIN_VALUE / 50, Long.MIN_VALUE - 1);

        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE).compareTo(Long.MIN_VALUE));
        Assert.assertEquals(1, Int9.fromLong(Long.MIN_VALUE - 1).compareTo(Long.MIN_VALUE));
        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE - 1).compareTo(Long.MIN_VALUE - 1));

        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE).compareTo(LONG_MIN));
        Assert.assertEquals(1, Int9.fromLong(Long.MIN_VALUE - 1).compareTo(LONG_MIN));
        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE - 1).compareTo(Int9.fromLong(Long.MIN_VALUE - 1)));

        Assert.assertEquals(-1, LONG_MIN.compareTo(0));
        Assert.assertEquals(-1, LONG_MIN.compareTo(3));
        Assert.assertEquals(0, LONG_MIN.compareTo(LONG_MIN));

        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE).compareToAbs(Long.MIN_VALUE));
        Assert.assertEquals(-1, Int9.fromLong(Long.MIN_VALUE - 1).compareToAbs(Long.MIN_VALUE));
        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE - 1).compareToAbs(Long.MIN_VALUE - 1));

        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE).compareToAbs(LONG_MIN));
        Assert.assertEquals(-1, Int9.fromLong(Long.MIN_VALUE - 1).compareToAbs(LONG_MIN));
        Assert.assertEquals(0, Int9.fromLong(Long.MIN_VALUE - 1).compareToAbs(Int9.fromLong(Long.MIN_VALUE - 1)));

        Assert.assertEquals(1, LONG_MIN.compareToAbs(0));
        Assert.assertEquals(1, LONG_MIN.compareToAbs(3));
        Assert.assertEquals(0, LONG_MIN.compareToAbs(LONG_MIN));
    }

    private static void checkCompareTo(long lhsValue, long rhsValue) {
        int expected = Long.compare(lhsValue, rhsValue);
        var lhs = Int9.fromLong(lhsValue);
        var rhs = Int9.fromLong(rhsValue);
        Assert.assertEquals(0, lhs.compareTo(lhs));
        Assert.assertEquals(0, lhs.compareTo(lhsValue));
        Assert.assertEquals(expected, lhs.compareTo(rhs));
        Assert.assertEquals(expected, lhs.compareTo(rhsValue));
        Assert.assertEquals(-expected, rhs.compareTo(lhs));

        if (lhsValue == Long.MIN_VALUE || rhsValue == Long.MIN_VALUE) {
            return;
        }

        int expectedAbs = Long.compare(Math.abs(lhsValue), Math.abs(rhsValue));
        Assert.assertEquals(0, lhs.compareToAbs(lhs));
        Assert.assertEquals(0, lhs.compareToAbs(lhsValue));
        Assert.assertEquals(expectedAbs, lhs.compareToAbs(rhs));
        Assert.assertEquals(expectedAbs, lhs.compareToAbs(rhsValue));
        Assert.assertEquals(-expectedAbs, rhs.compareToAbs(lhs));
    }

    @Test
    public void subtractEdgeCases() {
        int[] values = { 0, 1, -1, 100, -500000000} ;
        for (int value : values) {
            checkSubtract(value, Long.MIN_VALUE);
            checkSubtract(value, Long.MIN_VALUE + 1);
            checkSubtract(value, Long.MIN_VALUE + 5);
            checkSubtract(value, Long.MIN_VALUE + 50);
            checkSubtract(value, Long.MIN_VALUE + 100);
            checkSubtract(value, Long.MIN_VALUE);
            checkSubtract(value, Long.MIN_VALUE - 1);
            checkSubtract(value, Long.MIN_VALUE - 5);
            checkSubtract(value, Long.MIN_VALUE - 50);
            checkSubtract(value, Long.MIN_VALUE - 100);
        }
    }

    private static void checkSubtract(long lhsValue, long rhsValue) {
        checkSubtract0(lhsValue, rhsValue);
        checkSubtract0(rhsValue, lhsValue);
    }

    private static void checkSubtract0(long lhsValue, long rhsValue) {
        var lhs = Int9.fromLong(lhsValue).subtractInPlace(rhsValue);
        var expected = BigInteger.valueOf(lhsValue).subtract(BigInteger.valueOf(rhsValue));
        Assert.assertEquals(expected.toString(), lhs.toString());
    }

    @Test
    public void subtractStr() {
        checkSubtractStr("0", ""+Long.MAX_VALUE);
        checkSubtractStr(""+Long.MIN_VALUE, "0");

        checkSubtractStr("0", "0");
        checkSubtractStr("1", "0");
        checkSubtractStr("-1", "0");
        checkSubtractStr("0", "-1");
        checkSubtractStr("0", "1");

        checkSubtractStr("10", "10");
        checkSubtractStr("8888888888888", "8888888888888");
        checkSubtractStr("10", "20");
        checkSubtractStr("1234653534553", "12345");
        checkSubtractStr("12345", "1234653534553");
        checkSubtractStr("20", "10000000000");
        checkSubtractStr("0", "0");
        checkSubtractStr("10", "5");
        checkSubtractStr("534534543543543", "5");
        checkSubtractStr("534534543543543", "5423");
        checkSubtractStr("534534543543543", "5423543");

        checkSubtractStr("999999999999999999", "999999999999999999");
        checkSubtractStr("999999999999999999", "9999999999999999");
        checkSubtractStr("999999999999999999", "999999999999");
        checkSubtractStr("999999999999999999", "9999999");
        checkSubtractStr("999999999999999999", "9999999");
        checkSubtractStr("100000000000000", "12345");

        checkSubtractStr("-10", "-20");
        checkSubtractStr("-100", "-20");
        checkSubtractStr("-10312", "-20645642345");
        checkSubtractStr("-4324223423", "-4");

        checkSubtractStr("-10", "4");
        checkSubtractStr("-10", "20");
        checkSubtractStr("-10", "201111111111");
        checkSubtractStr("-10666666666", "20111111111111");

        checkSubtractStr("104324", "-20");
        checkSubtractStr("10", "-20");
        checkSubtractStr("10", "-2044444444");
        checkSubtractStr("-1", "-1");
        checkSubtractStr("1935517214", "1990477718");

        checkSubtractStr("-100", "20");
        checkSubtractStr("-100", "200");
        checkSubtractStr("-10000000000000", "200");
        checkSubtractStr("-100", "2000000000000");
        checkSubtractStr("-100", "100");

        checkSubtractStr("100", "-10");
        checkSubtractStr("100", "-200");
        checkSubtractStr("10000000000000", "-2000000000000");
        checkSubtractStr("100", "-200");
        checkSubtractStr("200", "-200");

        checkSubtractStr("-100", "-30");
        checkSubtractStr("-100", "-300");
        checkSubtractStr("-10000000000000", "-30");
        checkSubtractStr("-100", "-2000000000000");
        checkSubtractStr("-2000000000000", "-2000000000000");
    }

    @Test
    public void isIntLong() {
        Assert.assertTrue(LONG_MIN.isLong());
        Assert.assertTrue(LONG_MAX.isLong());
        Assert.assertFalse(LONG_MIN.isInt());
        Assert.assertFalse(LONG_MAX.isInt());
        Assert.assertTrue(INT_MIN.isInt());
        Assert.assertTrue(INT_MAX.isInt());
    }

    @Test
    public void addBig() {
        checkAddBig("1000000000000000000000005", "1000000000000000000000001", "4");
        checkAddBig("1000000000000000000000005", "1000000000000000000000000", "5");
        checkAddBig("-9223372036854775808", "0", ""+Long.MIN_VALUE);
        checkAddBig("-9223372036854775807", ""+Long.MIN_VALUE, "1");
        checkAddBig("-9223372036854775807", "1", ""+Long.MIN_VALUE);
        checkAddBig("9223372036854775807", "0", ""+Long.MAX_VALUE);
        checkAddBig("9223372036854775808", "1", ""+Long.MAX_VALUE);

        checkAddBig("5748943328234817249409158314469318287168769602732",
                "5748937584375873489574398579483574389574389758943",
                "5743858943759834759734985743897594379843789"
        );

        checkAddBig("5743858943759834759734985743897594379843789555555555555555555555555555555555555555555555555573489578493758943759843763187891127724848475309386177831168769431829616886",
                "5748937584375873489574398579483574389574389758943",
                "5743858943759834759734985743897594379843789555555555555555555555555555555555555555555555555573489578493758943759843757438953543348974985734987598347594379857439857943"
        );

        checkAddBig("580934850934890584390584358438543859034850834058034854830584309580438504380584305843098504385043890543809584309580943850348504830958340583485934853485084305834908503485094385094385843580438504380584305843850348058430580493850348548309580943859043809584310154891",
                "580934850934890584390584358438543859034850834058034854830584309580438504380584305843098504385043890543809584309580943850348504830958340583485934853485084305834908503485094385094385843580438504380584305843850348058430580493850348548309580943859043809584309580493",
                "574398"
        );
        checkAddBig("5809348509348905843905843584385438590348508340580348548305843095804385043805843058430985043850438905438095843095809438503485629244434340725443739119209281602208119885685001885798689020113965482310223642744346579088690848829047295067405390382440786600674059889501263712631262862083612684059040649940649941399135993948825028750288294792502874802716072570770531803163859043809584310154891",
                "580934850934890584390584358438543859034850834058034854830584309580438504380584305843098504385043890543809584309580943850348504830958340583485934853485084305834908503485094385094385843580438504380584305843850348058430580493850348548309580943859043809584310154891",
                "5809348509348905843905843584385438590348508340580348548305843095804385043805843058430985043850438905438095843095809438503485048309583405834859348534850843058349085034850943850943858435804385043805843058438503480584305804938503485483095809438590438095843101548917777777777777777777777775555555555555555555555555555444444444444444444444444444222222222222222222222220000000000000000000000"
        );

        checkAddBig("1000000000",
                "999999999",
                "1"
        );
        checkAddBig("1000000000000000000",
                "999999999999999999",
                "1"
        );
        checkAddBig("1999999999999999998",
                "999999999999999999",
                "999999999999999999"
        );

        /*
         * negative numbers
         */

        checkAddBig("-59345809438509348331449196587292208514118424",
                "-59345809438509348905834985934890583409853409",
                "574385789347598374895734985"
        );
        checkAddBig("-1",
                "1",
                "-2"
        );
        checkAddBig("347593485789347598304301924",
                "347593485789347598347589347",
                "-43287423"
        );
    }

    @Test
    public void subtractBig() {
        checkSubtractBig("9223372036854775808", "0", ""+Long.MIN_VALUE);
        checkSubtractBig("-9223372036854775807", "0", ""+Long.MAX_VALUE);
        checkSubtractBig("9223372036854775809", "1", ""+Long.MIN_VALUE);
        checkSubtractBig("-9223372036854775806", "1", ""+Long.MAX_VALUE);

        checkSubtractBig("53485438758934758934789999679149412245412236411887763598446",
                "53485438758934758934795734574938759834759834759873498574389",
                "5734895789347589347598347985734975943"
        );
        checkSubtractBig("5843758943758934789573497598437847543777777772333333333333332",
                "5843758943758934789573497598437847543777777772333333333333333",
                "1"
        );
        checkSubtractBig("8888888888888850934859348095834583408509348503480958430958034985043849984160896082603749603965502904145595110108920041432525058651908708366063196499495298200",
                "8888888888888850934859348095834583408509348503480958430958034985043850438058430958438509438543850438905438905843905834908509438509348584309859043858439058034",
                "453897534875834759834578347534759843795734985793475984379857439875943795847358943759834"
        );
        checkSubtractBig("-423497239847239874982378492378947185007330263924359413899363383542",
                "53940593485908439058490385943890",
                "423497239847239874982378492378947238947923749832798472389749327432"
        );
        checkSubtractBig("-46872364236874623846823648723648732678426387468237647754782601438816664216880037747777377373894738515928730866360700190655331195428199498069800",
                "68904860945806984506804586094580968409580945860945858903485093480598340958093485034805834",
                "46872364236874623846823648723648732678426387468237647823687462384623648723684623842358345783475684376874589769845793671253672153521684532875634"
        );
        checkSubtractBig("-5735473135084172713770149209",
                "-5734895789347598347895783475",
                "577345736574365874365734"
        );
        checkSubtractBig("34958349859488201958833366953891",
                "34958349859434354385347583478543",
                "-53847573485783475348"
        );
        checkSubtractBig("45372675634765784365873012608904182415612108927838379986389",
                "-453267534574367856347856738465783465",
                "-45372675634765784365873465876438756783468456784576845769854"
        );
        checkSubtractBig("-5834798574389759834757349574357893457975487055254698059815054",
                "-5834798574389759834757349574357893457984375943789573498573487",
                "-8888888534875438758433"
        );
        checkSubtractBig("999999999",
                "1000000000",
                "1"
        );
        checkSubtractBig("999999999999999999",
                "1000000000000000000",
                "1"
        );
    }

    @Test
    public void random() {
        int repeats = 100_000;
        var rnd = new Random();
        long min = 0;
        long mid = 0x7FFFFFFFL;
        long max = Long.MAX_VALUE / 10; // prevent long overflow in test code path
        long lhs, rhs;

        while (repeats-- > 0) {
            lhs = random(rnd, min, max);
            rhs = random(rnd, min, max);
            checkHalfAndDouble(lhs);
            checkAddStr(""+lhs, ""+rhs);
            checkAddStr(""+-lhs, ""+rhs);
            checkAddStr(""+lhs, ""+-rhs);
            checkAddStr(""+-lhs, ""+-rhs);
            checkSubtractStr(""+lhs, ""+rhs);
            checkSubtractStr(""+-lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+-rhs);
            checkSubtractStr(""+-lhs, ""+-rhs);

            lhs = random(rnd, mid, max);
            rhs = random(rnd, mid, max);
            checkHalfAndDouble(lhs);
            checkAddStr(""+lhs, ""+rhs);
            checkAddStr(""+-lhs, ""+rhs);
            checkAddStr(""+lhs, ""+-rhs);
            checkAddStr(""+-lhs, ""+-rhs);
            checkSubtractStr(""+lhs, ""+rhs);
            checkSubtractStr(""+-lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+-rhs);
            checkSubtractStr(""+-lhs, ""+-rhs);

            lhs = random(rnd, min, mid);
            rhs = random(rnd, min, mid);
            checkMulStr(""+lhs, ""+rhs);
            checkMulStr(""+-lhs, ""+rhs);
            checkMulStr(""+lhs, ""+-rhs);
            checkMulStr(""+-lhs, ""+-rhs);
            checkDivideInPlace(lhs, (int) rhs + 1); // prevent / by zero
            checkDivideInPlace(lhs, 3);
            checkDivideInPlaceBy3Exact(lhs);

            checkHalfAndDouble(lhs);
            checkAddStr(""+lhs, ""+rhs);
            checkAddStr(""+-lhs, ""+rhs);
            checkAddStr(""+lhs, ""+-rhs);
            checkAddStr(""+-lhs, ""+-rhs);
            checkSubtractStr(""+lhs, ""+rhs);
            checkSubtractStr(""+-lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+-rhs);
            checkSubtractStr(""+-lhs, ""+-rhs);

            lhs = random(rnd, min, mid);
            rhs = random(rnd, min, Long.MAX_VALUE - 1);
            checkFromInt((int) lhs);
            checkFromLong(rhs);
        }
    }

    @Test
    public void fromInt() {
        checkFromInt(0);
        checkFromInt(1);
        checkFromInt(999_999_998);
        checkFromInt(999_999_999);
        checkFromInt(999_999_999 + 1);
        checkFromInt(Integer.MAX_VALUE - 10);
        checkFromInt(Integer.MAX_VALUE);

        checkFromInt(-1);
        checkFromInt(-1534);
        checkFromInt(Integer.MIN_VALUE + 1);
        checkFromInt(Integer.MIN_VALUE);
    }

    @Test
    public void fromLong() {
        checkFromLong(0);
        checkFromLong(1);
        checkFromLong(999_999_998);
        checkFromLong(999_999_999);

        checkFromLong(999_999_999 + 1);
        checkFromLong(999_999_999_999_999_999L);
        checkFromLong(999_999_999_999_999_999L + 1);
        checkFromLong(Long.MAX_VALUE);

        checkFromLong(-7);
        checkFromLong(-76456456456L);
        checkFromLong(Long.MIN_VALUE + 5);
        checkFromLong(Long.MIN_VALUE + 1);
        checkFromLong(Long.MIN_VALUE);
    }

    @Test
    public void halfInPlace() {
        checkHalf(false, "0", "0");
        checkHalf(true, "0", "1");
        checkHalf(false, "1", "2");
        checkHalf(true, "1", "3");
        checkHalf(false, "500000000000000000000000000000000000000000000000000000", "1000000000000000000000000000000000000000000000000000000");
        checkHalf(true, "500000000000000000000000000000000000000000000000000000", "1000000000000000000000000000000000000000000000000000001");
        checkHalf(false, "4000", "8000");
        checkHalf(false, "5" + "0".repeat(10_000), "1" + "0".repeat(10_001));
        checkHalf(false, "5" + "0".repeat(100_000), "1" + "0".repeat(100_001));
        checkHalf(false, "4" + "0".repeat(100_000), "8" + "0".repeat(100_000));
        checkHalf(true, "61", "123");
        checkHalf(false,
            "317437674379467379917397867492896737967394786747867487673798674928674789673796737867289673796728674487967379673978674928969237967397867492894673799173799218978674478971879917379936749287178967",
            "634875348758934759834795734985793475934789573495734975347597349857349579347593475734579347593457348975934759347957349857938475934795734985789347598347598437957348957943759834759873498574357934"
        );
    }

    private static void checkHalf(boolean expectedRet, String expected, String input) {
        var b = Int9.fromString(input);
        var ret = b.halfInPlace();
        Assert.assertEquals(expected, b.toString());
        Assert.assertEquals(expectedRet, ret);
    }

    @Test
    public void doubleInPlace() {
        checkDouble("0", "0");
        checkDouble("666", "333");
        checkDouble("1999999998", "999999999");
        checkDouble("999999998", "499999999");
        checkDouble("1000000000", "500000000");

        checkDouble("1669166951887518695146915869750695069597146991596695196695188757914699146995069579146971469975069506951786959714697146951966951469971586950875146979146951868",
            "834583475943759347573457934875347534798573495798347598347594378957349573497534789573485734987534753475893479857348573475983475734985793475437573489573475934"
        );
    }

    private static void checkDouble(String expected, String input) {
        var b = Int9.fromString(input);
        b.doubleInPlace();
        Assert.assertEquals(expected, b.toString());
    }

    private static void checkHalfAndDouble(long value) {
        var bigint = Int9.fromString("" + value);
        Assert.assertEquals(value, bigint.toLong());
        int carry = bigint.halfInPlace() ? 1 : 0;
        Assert.assertEquals(value / 2, bigint.toLong());
        bigint.doubleInPlace();
        Assert.assertEquals(value - carry, bigint.toLong());
        bigint.doubleInPlace();
        Assert.assertEquals((value - carry) * 2, bigint.toLong());
    }

    private static void checkFromLong(long value) {
        var bigint = Int9.fromString("" + value);
        Assert.assertEquals(value, bigint.toLong());
        var fromLong = Int9.fromLong(value);
        Assert.assertEquals(bigint.toString(), "" + fromLong);
        Assert.assertEquals(bigint, fromLong);
        Assert.assertEquals(value, fromLong.toLong());
    }

    private static void checkFromInt(int value) {
        var bigint = Int9.fromString("" + value);
        Assert.assertEquals(value, bigint.toInt());
        Assert.assertEquals(value, bigint.toLong());
        var fromInt = Int9.fromInt(value);
        Assert.assertEquals(bigint, fromInt);
        Assert.assertEquals(value, fromInt.toInt());
        Assert.assertEquals(value, fromInt.toLong());
    }

    @Test
    public void mulNoSideEffects() {
        checkMulNoSideEffects("6".repeat(10001), "3".repeat(100044));
        checkMulNoSideEffects("3".repeat(10001), "3".repeat(7));
        checkMulNoSideEffects("4".repeat(32), "3".repeat(100044));
        checkMulNoSideEffects("7".repeat(323233), "3".repeat(100044));
    }

    private static void checkMulNoSideEffects(String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);

        Assert.assertEquals(lhsStr, lhs.toString());
        Assert.assertEquals(rhsStr, rhs.toString());

        Int9.setForkJoinPool(null);
        lhs.multiply(rhs);

        // multiply didn't modify it's arguments:
        Assert.assertEquals(lhsStr, lhs.toString());
        Assert.assertEquals(rhsStr, rhs.toString());

        Int9.setForkJoinPool(pool());
        lhs.multiply(rhs);

        // multiply didn't modify it's arguments:
        Assert.assertEquals(lhsStr, lhs.toString());
        Assert.assertEquals(rhsStr, rhs.toString());
    }

    @Test
    @Ignore
    public void mulDev() {
        // change Int9.BASE to 10 for simple numbers
        {
            var lhs = new Int9(1, 2);
            var rhs = new Int9(3, 4, 5);
            var prod = lhs.multiply(rhs);
            System.out.println(prod.toDebugString());
        }
        {
            var lhs = new Int9(7, 8);
            var rhs = new Int9(9, 1);
            var prod = lhs.multiply(rhs);
            System.out.println(prod.toDebugString());
        }
        {
            var lhs = new Int9(7, 8, 1);
            var rhs = new Int9(9, 1, 5, 1, 0);
            var prod = lhs.multiply(rhs);
            System.out.println(prod.toDebugString());
        }
    }

    @Test
    public void mulStr() {
        checkMulStr("5", "6");
        checkMulStr("5000", "6888");

        checkMulStr("35", "48");
        checkMulStr("0", "0");
        checkMulStr("0", "1");
        checkMulStr("1", "0");
        checkMulStr("-1", "0");

        checkMulStr("1", "1");
        checkMulStr("1", "1000000000000");
        checkMulStr("-1", "1");
        checkMulStr("-1", "1000000000000");
        checkMulStr("1", "-1");
        checkMulStr("1", "-1000000000000");
        checkMulStr("-1", "-1");
        checkMulStr("-1", "-1000000000000");

        checkMulStr("2", "1");
        checkMulStr("2", "1000000000000");
        checkMulStr("-2", "1");
        checkMulStr("-2", "1000000000000");
        checkMulStr("2", "-1");
        checkMulStr("2", "-1000000000000");
        checkMulStr("-2", "-1");
        checkMulStr("-2", "-1000000000000");

        checkMulStr("1", "1543432");
        checkMulStr("1543432", "1");
        checkMulStr("0", "54354353");

        checkMulStr("2", "3");
        checkMulStr("999999999", "1");
        checkMulStr("999999999", "2");

        checkMulStr("423423", "-1");

        checkMulStr("12345", "678901");
        checkMulStr("1234567890", "123");
        checkMulStr("1234567890", "123456789");

        checkMulStr("999999999", "999999999");
        checkMulStr("42343232", "64564564545");
        checkMulStr("999999999", "999999999");

        checkMulStr("-524543534", "-4242");
        checkMulStr("524543534", "-4242");
        checkMulStr("-524543534", "4242");
    }

    @Test
    public void mulStrBig() {
        checkMulStrBig("0", "0", "0");
        checkMulStrBig("1", "1", "1");

        checkMulStrBig("351314223566715967153201654060810044035211251953749909658504825035588175368399455464543237429221890910",
                "6459068045986045860945809684509684509860945860954890",
                "54390853458346573468756348563456843391203901293219"
        );

        checkMulStrBig("351314223566715967153201654060810044035211251953749909658504825035588175368399455464543237429221890910",
                "-6459068045986045860945809684509684509860945860954890",
                "-54390853458346573468756348563456843391203901293219"
        );

        checkMulStrBig("-351314223566715967153201654060810044035211251953749909658504825035588175368399455464543237429221890910",
                "6459068045986045860945809684509684509860945860954890",
                "-54390853458346573468756348563456843391203901293219"
        );
        checkMulStrBig("-351314223566715967153201654060810044035211251953749909658504825035588175368399455464543237429221890910",
                "-6459068045986045860945809684509684509860945860954890",
                "54390853458346573468756348563456843391203901293219"
        );


        checkMulStrBig("66666666370368146666676543951000014079975308628542962991358031255592562901245406233945753084914988395074074097518641975308661728395061728382716049382716049506172839506172691358024691358",
                "9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "6666666666666444444444444444477777777777777777222222222222222888888888888889"
        );

        checkMulStrBig("9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "1"
        );

        checkMulStrBig("-9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "-1"
        );

        checkMulStrBig("0",
                "9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "0"
        );
        checkMulStrBig("0",
                "-9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "0"
        );

        checkMulStrBig("370370370333333333333333333333333333333333333333329629629630",
                "5".repeat(50),
                "6".repeat(10)
        );
    }

    @Test
    public void mulStrNew() {
        checkMulStrNew("0", "0");
        checkMulStrNew("1", "0");
        checkMulStrNew("0", "1");
        checkMulStrNew("1", "1");
        checkMulStrNew("1", "-1");
        checkMulStrNew("-1", "-1");
        checkMulStrNew("-1", "1");
        checkMulStrNew("-1", "-1");
        checkMulStrNew("1", "-2");
        checkMulStrNew("-1", "-2");
        checkMulStrNew("-1", "2");
        checkMulStrNew("-1", "-2");

        checkMulStrNew("1", "-20000000000000000000000000");
        checkMulStrNew("-1", "-200000000000000000000000000000000");
        checkMulStrNew("-1", "2000000000");
        checkMulStrNew("-1", "-2" + "0".repeat(1000));
        checkMulStrNew("2", "-20000000000000000000000000");
        checkMulStrNew("-2", "-200000000000000000000000000000000");
        checkMulStrNew("-2", "2000000000");
        checkMulStrNew("-2", "-2" + "0".repeat(1000));

        checkMulStrNew("1", "-200000000000000000000000001");
        checkMulStrNew("-1", "-2000000000000000000000000000000001");
        checkMulStrNew("-1", "20000000001");
        checkMulStrNew("-1", "-2" + "0".repeat(1000) + "1");
        checkMulStrNew("2", "-200000000000000000000000001");
        checkMulStrNew("-2", "-2000000000000000000000000000000001");
        checkMulStrNew("-2", "20000000001");

        checkMulStrNew("10000000000000000000000000000", "2000000000000000000000000000000000000");
    }

    private void checkMulStrNew(String lhs, String rhs) {
        checkMulStrNew0(lhs, rhs);
        checkMulStrNew0(rhs, lhs);
    }

    private void checkMulStrNew0(String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        var expected = new BigInteger(lhsStr).multiply(new BigInteger(rhsStr)).toString();

        Assert.assertEquals(expected, Int9.multiplySimple(lhs, rhs).toString());
        Assert.assertEquals(expected, Int9.multiplyRussianPeasant(lhs, rhs).toString());
        Assert.assertEquals(expected, Int9.multiplyKaratsuba(lhs, rhs).toString());
        Assert.assertEquals(expected, Int9.multiplyKaratsuba(lhs, rhs, 1).toString());
        Assert.assertEquals(expected, Int9.parallelMultiplyKaratsuba(lhs, rhs, pool()).toString());
    }

    @Test
    public void mulStrHuge() {
        int[] lengths = { 10, 100, 1_100, 10_000 };
        for (int length : lengths) {
            checkMulStrBig("6".repeat(length),        "3".repeat(length), "2");
            checkMulStrBig("8".repeat(length),        "2".repeat(length), "4");
            checkMulStrBig("5".repeat(length) + "00", "5".repeat(length), "100");
        }
    }

    @Test
    public void randomHuge() {
        int[] lengths = { 10, 1234, 10_000 };
        int max = 13_000;
        var rnd = new Random();
        String[] constants = { "0", "1", "-1" };
        for (int left : lengths) {
            for (int right : lengths) {
                String lhsSign = rnd.nextBoolean() ? "" : "-";
                String rhsSign = rnd.nextBoolean() ? "" : "-";
                String lhs = lhsSign + randomNumericString(rnd, left, max);
                String rhs = rhsSign + randomNumericString(rnd, right, max);
                BigInteger lhsInt = new BigInteger(lhs);
                BigInteger rhsInt = new BigInteger(rhs);

                checkMulStrBig(lhsInt.multiply(rhsInt).toString(), lhs, rhs);
                checkAddStrBig(lhsInt.add(rhsInt).toString(), lhs, rhs);
                checkSubtractStrBig(lhsInt.subtract(rhsInt).toString(), lhs, rhs);
                checkSubtractStrBig(lhsInt.subtract(rhsInt).toString(), lhs, rhs);

                checkDivideInPlace(lhs, ""+Integer.MAX_VALUE);
                checkDivideInPlace(lhs, "1234567890");
                checkDivideInPlace(lhs, "-612345678");
                checkDivideInPlace(lhs, "2812");
                checkDivideInPlace(lhs, "1");
                checkDivideInPlace(lhs, "-1");
                checkDivideInPlace(lhs, "3");
                checkDivideInPlaceBy3Exact(lhs);

                for (String constant : constants) {
                    rhs = constant;
                    rhsInt = new BigInteger(rhs);
                    checkMulStrBig(lhsInt.multiply(rhsInt).toString(), lhs, rhs);
                    checkAddStrBig(lhsInt.add(rhsInt).toString(), lhs, rhs);
                    checkSubtractStrBig(lhsInt.subtract(rhsInt).toString(), lhs, rhs);
                }
            }
        }
    }

    @Test
    public void powStr() {
        checkPowStr("1", "10", 0);
        checkPowStr("10", "10", 1);
        checkPowStr("100", "10", 2);
        checkPowStr("1" + "0".repeat(100), "10", 100);
        checkPowStr("1" + "0".repeat(10_000), "10", 10000);
        checkPowStr("" + (1L << 32), "2", 32);
        checkPowStr("14774235820095264004498794573921235350758313964623449966161598941405119454882112300765336770281511027492774636711848411946648949240160971642785234944",
                "84", 77
        );
        checkPowStr("2879671080859757669371632905130642863008240727636363385187223405517100107785937605972400386749113299846163442776643166525359504770992949038206792400556228561822598713052726895715596993040180470325897606484268540331518071802980113724009674815162984629068887443281710147857666015625",
                "665", 99
        );
    }

    private static void checkPowStr(String expected, String baseStr, int exponent) {
        var base = Int9.fromString(baseStr);
        Assert.assertEquals(expected, Int9.pow(base, exponent).toString());
        Assert.assertEquals(expected, Int9.pow(base, exponent).toString());
        Assert.assertEquals(expected, Int9.pow(base, exponent, 1).toString());
        Assert.assertEquals(expected, Int9.parallelPow(base, exponent, pool()).toString());
        Assert.assertEquals(expected, Int9.parallelPow(base, exponent, 1, 999, pool()).toString());
    }

    private void checkAddBig(String expected, String lhsStr, String rhsStr) {
        checkAddBig0(expected, lhsStr, rhsStr);
        checkAddBig0(expected, rhsStr, lhsStr);
    }

    private static void checkAddBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        checkStringRepresentation(expected, Int9.add(lhs, rhs));

        ////////// in-place ///////////
        if (canAddInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.addInPlace(rhs);
            checkStringRepresentation(expected, lhs);

            if (rhs.isLong()) {
                copy.addInPlace(rhs.toLong());
                checkStringRepresentation(expected, copy);
            }
        }
    }

    private void checkSubtractBig(String expected, String lhsStr, String rhsStr) {
        checkSubtractBig0(expected, lhsStr, rhsStr);
    }

    private static void checkSubtractBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        var res = Int9.subtract(lhs, rhs);
        Assert.assertEquals(expected, res.toString());

        var res2 = Int9.add(lhs, rhs.negate());
        Assert.assertEquals(expected, res2.toString());

        var res3 = Int9.subtract(rhs, lhs).negate();
        Assert.assertEquals(expected, res3.toString());

        ////////// in-place ///////////
        if (canSubtractInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.subtractInPlace(rhs);
            Assert.assertEquals(expected, lhs.toString());

            if (rhs.isLong()) {
                copy.subtractInPlace(rhs.toLong());
                Assert.assertEquals(expected, copy.toString());
            }
        }
    }

    private static void checkAddStr(String lhsStr, String rhsStr) {
        checkAddStr0(lhsStr, rhsStr);
        checkAddStr0(rhsStr, lhsStr);
    }

    private static void checkAddStr0(String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        var sum = Int9.add(lhs, rhs);

        long l = Long.parseLong(lhsStr);
        Assert.assertEquals(l, lhs.toLong());
        long r = Long.parseLong(rhsStr);
        Assert.assertEquals(r, rhs.toLong());
        long expected = Math.addExact(l, r);
        Assert.assertEquals(expected, Long.parseLong(sum.toString()));
        Assert.assertEquals(expected, sum.toLong());

        ////////// in-place ///////////
        if (canAddInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.addInPlace(rhs);
            Assert.assertEquals(expected, Long.parseLong(lhs.toString()));

            if (rhs.isLong()) {
                copy.addInPlace(rhs.toLong());
                Assert.assertEquals(expected, Long.parseLong(copy.toString()));
            }
        }
    }

    private static void checkSubtractStr(String lhsStr, String rhsStr) {
        checkSubtractStr0(lhsStr, rhsStr);
    }

    private static void checkSubtractStr0(String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        var res = Int9.subtract(lhs, rhs);

        long l = Long.parseLong(lhsStr);
        Assert.assertEquals(l, lhs.toLong());
        long r = Long.parseLong(rhsStr);
        Assert.assertEquals(r, rhs.toLong());
        long expected = Math.subtractExact(l, r);

        Assert.assertEquals(expected, Long.parseLong(res.toString()));
        Assert.assertEquals(expected, res.toLong());

        var res2 = Int9.add(lhs, rhs.negate());
        Assert.assertEquals(expected, Long.parseLong(res2.toString()));
        Assert.assertEquals(expected, res2.toLong());

        ////////// in-place ///////////
        if (canSubtractInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.subtractInPlace(rhs);
            Assert.assertEquals(expected, lhs.toLong());

            if (rhs.isLong()) {
                copy.subtractInPlace(rhs.toLong());
                Assert.assertEquals(expected, copy.toLong());
            }
        }
    }

    private static ForkJoinPool pool = new ForkJoinPool();

    private static ForkJoinPool pool() {
        return pool;
    }

    private static void checkMulStr(String lhsStr, String rhsStr) {
        checkMulStr0(lhsStr, rhsStr);
        checkMulStr0(rhsStr, lhsStr);
    }

    private static void checkMulStr0(String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        long l = Long.parseLong(lhsStr);
        long r = Long.parseLong(rhsStr);
        long expected = Math.multiplyExact(l, r);
        Assert.assertEquals(expected, Long.parseLong(Int9.multiplySimple(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(Int9.multiplyRussianPeasant(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(Int9.multiplyKaratsuba(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(Int9.multiplyKaratsuba(lhs, rhs, 1).toString()));
        Assert.assertEquals(expected, Long.parseLong(Int9.parallelMultiplyKaratsuba(lhs, rhs, pool()).toString()));
    }

    private static void checkAddStrBig(String expected, String lhsStr, String rhsStr) {
        checkAddStrBig0(expected, lhsStr, rhsStr);
        checkAddStrBig0(expected, rhsStr, lhsStr);
    }

    private static void checkAddStrBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        checkStringRepresentation(expected, Int9.add(lhs, rhs));

        ////////// in-place ///////////
        if (canAddInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.addInPlace(rhs);
            checkStringRepresentation(expected, lhs);

            if (rhs.isLong()) {
                copy.addInPlace(rhs.toLong());
                checkStringRepresentation(expected, copy);
            }
        }
    }

    private static void checkSubtractStrBig(String expected, String lhsStr, String rhsStr) {
        checkSubtractStrBig0(expected, lhsStr, rhsStr);
    }

    private static void checkSubtractStrBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        checkStringRepresentation(expected, Int9.subtract(lhs, rhs));

        ////////// in-place ///////////
        if (canSubtractInPlace(lhs, rhs)) {
            var copy = lhs.copy();
            lhs.subtractInPlace(rhs);
            checkStringRepresentation(expected, lhs);

            if (rhs.isLong()) {
                copy.subtractInPlace(rhs.toLong());
                checkStringRepresentation(expected, copy);
            }
        }
    }

    private static void checkMulStrBig(String expected, String lhsStr, String rhsStr) {
        checkMulStrBig0(expected, lhsStr, rhsStr);
        checkMulStrBig0(expected, rhsStr, lhsStr);
    }

    private static void checkMulStrBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = Int9.fromString(lhsStr);
        var rhs = Int9.fromString(rhsStr);
        checkStringRepresentation(expected, Int9.multiplySimple(lhs, rhs));
        checkStringRepresentation(expected, Int9.multiplyRussianPeasant(lhs, rhs));
        checkStringRepresentation(expected, Int9.multiplyKaratsuba(lhs, rhs));
        checkStringRepresentation(expected, Int9.multiplyKaratsuba(lhs, rhs, 1));
        checkStringRepresentation(expected, Int9.parallelMultiplyKaratsuba(lhs, rhs, pool()));
    }

    private static void checkStringRepresentation(String input, Int9 value) {
        Assert.assertEquals(input, value.toString());
        Assert.assertTrue(stringsEqual(input, value));
    }

    @Test
    public void string() {
        checkString("0");
        checkString("15");
        checkString("999999999");
        checkString("999999999222");
        checkString("111222333000666777");
        checkString("111222333444555666777888999");
        checkString("1112223334445556667778889990");
        checkString("11122233344455566677788899900");
        checkString("111222333444555666777888999009");

        checkString("12345", 0, 3);
        checkString("12345", 1, 4);
        checkString("12345", 2, 5);
        checkString("12345", 4, 5);
    }

    @Test
    public void trailingZeroesForm() {
        var x = Int9.fromString("5" + "0".repeat(30));
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", x.toDebugString());
        x = x.copy();
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", x.toDebugString());

        x.setValue(1L << 60);
        Assert.assertEquals("Int9 {digits=19, negative=false, offset=0, length=3, capacity=3, data=[1, 152921504, 606846976]}", x.toDebugString());

        x = Int9.fromString("5" + "0".repeat(30));
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", x.toDebugString());
        x.incrementInPlace();
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=4, data=[5000, 0, 0, 1]}", x.toDebugString());

        x = Int9.fromString("5" + "0".repeat(30));
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", x.toDebugString());
        x.addInPlace(1);
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=1, length=4, capacity=5, data=[5000, 0, 0, 1]}", x.toDebugString());

        x = Int9.fromString("5" + "0".repeat(30));
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", x.toDebugString());

        x.clear();
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[0]}", x.toDebugString());

        var y = Int9.fromString("5" + "0".repeat(30));
        Assert.assertEquals("Int9 {digits=31, negative=false, offset=0, length=4, capacity=1, data=[5000]}", y.toDebugString());

        y.setValue(100);
        Assert.assertEquals("Int9 {digits=3, negative=false, offset=0, length=1, capacity=1, data=[100]}", y.toDebugString());

        y.setValue(1L << 61);
        Assert.assertEquals("Int9 {digits=19, negative=false, offset=0, length=3, capacity=3, data=[2, 305843009, 213693952]}", y.toDebugString());

        y.setValue(Int9.fromString("5".repeat(500)));
        Assert.assertEquals("Int9 {digits=500, negative=false, offset=0, length=56, capacity=56, data=[55555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555, 555555555]}", y.toDebugString());

        y.setValue(100);
        Assert.assertEquals("Int9 {digits=3, negative=false, offset=55, length=1, capacity=56, data=[100]}", y.toDebugString());

        y = y.copy();
        Assert.assertEquals("Int9 {digits=3, negative=false, offset=0, length=1, capacity=1, data=[100]}", y.toDebugString());

        var z = Int9.fromScientific("1E100");

        for (int i = 0; i < z.length(); i++) {
            Assert.assertEquals(i == 0 ? '1' : '0', z.charAt(i));
        }
    }

    @Test
    public void inPlaceResizeBehavior() {
        var x = Int9.fromString("5".repeat(30));

        Assert.assertEquals("Int9 {digits=30, negative=false, offset=0, length=4, capacity=4, data=[555, 555555555, 555555555, 555555555]}", x.toDebugString());
        x.addInPlace(ONE);
        Assert.assertEquals("Int9 {digits=30, negative=false, offset=1, length=4, capacity=5, data=[555, 555555555, 555555555, 555555556]}", x.toDebugString());

        x.subtractInPlace(ONE);
        Assert.assertEquals("Int9 {digits=30, negative=false, offset=1, length=4, capacity=5, data=[555, 555555555, 555555555, 555555555]}", x.toDebugString());

        var y = ONE.copy();
        Assert.assertEquals("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[1]}", y.toDebugString());
        y.addInPlace(x);
        Assert.assertEquals("Int9 {digits=30, negative=false, offset=1, length=4, capacity=5, data=[555, 555555555, 555555555, 555555556]}", y.toDebugString());
        y.subtractInPlace(ONE);
        Assert.assertEquals("Int9 {digits=30, negative=false, offset=1, length=4, capacity=5, data=[555, 555555555, 555555555, 555555555]}", y.toDebugString());

        var z = ONE.copy();
        z.subtractInPlace(x);

        Assert.assertEquals("Int9 {digits=30, negative=true, offset=0, length=4, capacity=4, data=[555, 555555555, 555555555, 555555554]}", z.toDebugString());
        z.addInPlace(y);
        z.addInPlace(y);
        Assert.assertEquals("Int9 {digits=30, negative=false, offset=1, length=4, capacity=8, data=[555, 555555555, 555555555, 555555556]}", z.toDebugString());
    }

    @Test
    public void leadingTrailingZeroes() {
        checkDebugStr("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[1]}", "1", "00000000000000000001");
        checkDebugStr("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[1]}", "1", "0000000000000000000000000000000000001");
        checkDebugStr("Int9 {digits=1, negative=true, offset=0, length=1, capacity=1, data=[1]}", "-1", "-0000000000000000000000000000000000001");
        checkDebugStr("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[0]}", "0", "0000000000000000000000000000000000000");
        checkDebugStr("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[0]}", "0", "0");

        checkDebugStr("Int9 {digits=1, negative=true, offset=0, length=1, capacity=1, data=[1]}", "-1");
        checkDebugStr("Int9 {digits=1, negative=false, offset=0, length=1, capacity=1, data=[1]}", "1");
        checkDebugStr("Int9 {digits=2, negative=false, offset=0, length=1, capacity=1, data=[10]}", "10");
        checkDebugStr("Int9 {digits=9, negative=false, offset=0, length=1, capacity=1, data=[100000000]}", "100000000");
        checkDebugStr("Int9 {digits=10, negative=false, offset=0, length=2, capacity=1, data=[1]}", "1000000000");
        checkDebugStr("Int9 {digits=91, negative=false, offset=0, length=11, capacity=1, data=[1]}", "1000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        checkDebugStr("Int9 {digits=92, negative=false, offset=0, length=11, capacity=11, data=[10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]}", "10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001");
        checkDebugStr("Int9 {digits=90, negative=false, offset=0, length=10, capacity=1, data=[100000000]}", "100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

        checkDebugStr("Int9 {digits=100004, negative=false, offset=0, length=11112, capacity=1, data=[45670]}", AsciiDigits.fromScientific("4567E100000"));
        checkDebugStr("Int9 {digits=100004, negative=false, offset=0, length=11112, capacity=1, data=[45670]}", AsciiDigits.fromScientific("4.567E+100003"));

        Assert.assertTrue(Int9.fromString(AsciiDigits.fromScientific("4567E100000")).isEven());
        Assert.assertFalse(Int9.fromString(AsciiDigits.fromScientific("4567E100000")).isEmpty());
        Assert.assertFalse(Int9.fromString(AsciiDigits.fromScientific("4567E100000")).isInt());
        Assert.assertFalse(Int9.fromString(AsciiDigits.fromScientific("4567E100000")).isLong());
        Assert.assertFalse(Int9.fromString(AsciiDigits.fromScientific("4567E100000")).isZero());
    }

    private static void checkDebugStr(String expected, CharSequence input) {
        checkDebugStr(expected, input, input);
    }

    private static void checkDebugStr(String expected, CharSequence expectedStr, CharSequence input) {
        Int9 value = Int9.fromString(input);
        Assert.assertEquals(expected, value.toDebugString());
        Assert.assertEquals(expectedStr.toString(), value.toString());
    }

    @Test
    public void stringSigned() {
        checkString("0", "-0");
        checkString("0", "+0");

        checkString("-15");
        checkString("-999999999");
        checkString("-999999999222");
        checkString("-111222333000666777");
        checkString("-111222333444555666777888999");
        checkString("-1112223334445556667778889990");
        checkString("-11122233344455566677788899900");

        checkString("3", "+3");
        checkString("11122233344455566677788899900", "+11122233344455566677788899900");
    }

    private static void checkString(String input) {
        checkString(input, input);
    }

    private static void checkString(String expected, String input) {
        Int9 value = Int9.fromString(input);
        Assert.assertEquals(expected, value.toString());
        var out = new ByteArrayOutputStream();
        if (value.isNegative()) {
            out.write('-');
        }
        value.stream(AsciiDigitArraySink.of(out));
        Assert.assertEquals(expected, out.toString(StandardCharsets.UTF_8));
        checkString(expected, 0, expected.length());

        var out2 = new ByteArrayOutputStream();
        value.stream(AsciiDigitArraySink.of(out2));
        Assert.assertArrayEquals(value.toByteArray(/*includeSign*/ false), out2.toByteArray());
    }

    private static void checkString(String input, int fromIndex, int toIndex) {
        Int9 value = Int9.fromString(input, fromIndex, toIndex);
        Assert.assertEquals(input.substring(fromIndex, toIndex), value.toString());
    }

    private static class Karatsuba {

        // inspired by https://www.youtube.com/watch?v=yWI2K4jOjFQ
        static long alexeyevich(long x, long y) {
            if (x < 10 && y < 10) {
                return x * y;
            }

            int n = Math.max(len(x), len(y));
            int half = n / 2;

            long powFull = pow(10, half * 2); // sic
            long powHalf = pow(10, half);
            long powZero = pow(10, 0);
            assert powZero == 1;

            long a = x / powHalf; //  left part of x
            long b = x % powHalf; // right part of x
            long c = y / powHalf; //  left part of y
            long d = y % powHalf; // right part of y

            long ac = alexeyevich(a, c);
            long bd = alexeyevich(b, d);
            long product = alexeyevich(a+b, c+d);
            long sum = ac + bd;
            assert product >= sum; // means we don't need negative numbers
            long diff = product - sum;

            return ac * powFull + diff * powHalf + bd * powZero;
        }

        private static long pow(int a, int b) {
            return (long) Math.pow(a, b);
        }

        private static int len(long x) {
            return (""+x).length();
        }
    }
}
