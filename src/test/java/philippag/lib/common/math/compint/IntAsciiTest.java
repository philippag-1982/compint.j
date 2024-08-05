package philippag.lib.common.math.compint;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;
import philippag.lib.common.math.compint.AsciiDigits.AsciiDigitArraySink;
import philippag.lib.common.math.compint.IntAscii.BaseConversion;
import philippag.lib.common.math.compint.IntAscii.SpecialBaseConversions;
import philippag.lib.common.math.compint.IntAscii.StandardBaseConversions;
import philippag.lib.common.math.compint.IntAscii.TableBaseConversion;

public class IntAsciiTest extends CommonTestBase {

    private static final BaseConversion[] BASES = {
            StandardBaseConversions.BINARY,
            StandardBaseConversions.OCTAL,
            StandardBaseConversions.DECIMAL,
            StandardBaseConversions.HEX,
            SpecialBaseConversions.BASE_32(),
            SpecialBaseConversions.BASE_36(),
            SpecialBaseConversions.BASE_64(),
            SpecialBaseConversions.BASE_256(),
            BaseConversion.forBase(6),
            BaseConversion.forBase(9),
            TableBaseConversion.build('q', "qwertyuiopasdfghjklzxcvbnm,./;'#]["),
    };

    private static final IntAscii ZERO = IntAscii.fromInt(0);

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
    }

    private static ForkJoinPool pool = new ForkJoinPool();

    private static ForkJoinPool pool() {
        return pool;
    }

    @Test
    public void leadingZeroes() {
        checkDebugStr("IntAscii {digits=1, offset=0, length=1, capacity=1, data=[49]}", "1", "00000000000000000001");
        checkDebugStr("IntAscii {digits=1, offset=0, length=1, capacity=1, data=[49]}", "1", "0000000000000000000000000000000000001");
        checkDebugStr("IntAscii {digits=1, offset=0, length=1, capacity=1, data=[48]}", "0", "0000000000000000000000000000000000000");
        checkDebugStr("IntAscii {digits=1, offset=0, length=1, capacity=1, data=[48]}", "0", "0");
    }

    private static void checkDebugStr(String expected, String expectedStr, String input) {
        IntAscii value = IntAscii.fromString(input);
        Assert.assertEquals(expected, value.toDebugString());
        Assert.assertEquals(expectedStr.toString(), value.toString());
    }

    @Test
    public void subSeqNoWriteThrough() {
        String original = "123456789012345678901234567890123456789012345678901234567890";
        var x = IntAscii.fromString(original);
        var y = x.subSequence(1, 11);
        Assert.assertEquals("2345678901", y.toString());

        y.addInPlace(IntAscii.fromInt(10));

        Assert.assertEquals("2345678911", y.toString());
        Assert.assertEquals(original, x.toString());

        y.addInPlace(IntAscii.fromString("5".repeat(200)));
        Assert.assertEquals("55555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555557901234466", y.toString());
        Assert.assertEquals(original, x.toString());
    }

    @Test
    public void fromInt9() {
        checkInt9(0);
        checkInt9(0xcafebabeL);
        checkInt9(0xcafebabe1fffa12L);

        checkInt9("9dc456ebe5fb963e16e662485bba46a9beb6a60e",
                "9006".repeat(12)
        );
        checkInt9("e0c2f1aafa53402a9181377d96f43da26826727aac0d29f5a787108775fef61b0e311352ead95b0f752b0ef8c330d4587cfed979cf80d98a230369b54d86d8c5efcb2abfe7197d28b44ec7bbda9daac2b67e34001fa2fc21449e615fcd52172ab335e16e77b36cc67004f8709ad5bc1b1965ae75466910015ed81459d48bb3ac0029be28653f33bd03c0e91d2df2dbe52e62367b82d09355a7e8d63e50eec21b33ecccc0378ad52efe9056e084f51e096a702cda05a31dfd9e8254b6144a6f701b2820dee813997c80710cfcacadc8e",
                "45006".repeat(100)
        );
        checkInt9("13d118d3743d1976b2927f9be25fedfcc683ef3310c10772394d8c3264d617f3c36ab0614af16910ba5f76686ba7135d623c20d8f48fad38f836e378a4386a44c9ebc2e9cb1c2927ee6bc40a3b6f658c5d3b35c70ad70956c323e020d77e4691b01276c6622be3470869bfd7b0640c81e123828c5b93c41eeb775b8a2e037b72144a57af4b2a01fcb4e95ef0abc86dbb738a91f72a7cace620540b4989987d0bf377089aaf926ca0dfd51008596b16499d52ee46a726d46afe6e98e997b1ed1a5bef2640a22fa903ff161565c3ac1d63b1415058ec2ae696e495f7a99b722956ad80959f87ddd27a7149dbbb927e97bc231a8b937fea542254984539e22dbc1308e80a128972af3dab87f6cc6c54e226013c8e49c03428f084fed98a9cbe8e730a8a2ab9af268a27fe6682ebaecc672bc0eaa2df7c54da38179e31d43755ce96c022e6b178e8bd78712d0e624d1c0d22f0890a12c49f225a68b7998732cbbc27fa33fa7a88189fe6a8b18b2076640cd25c72355ae1f56c92ed013dc091edba536d99cf6e81f46d93cebb209d6ff425124fd7fd0644f72edd53b6585519e0c0366e66a3dfd806b6729f89f5fb26451db49878eb2305aed7e8a232f07f8d7d1e44d56b7b652d8b81216cffe1bcbb7277aca3eb7810552fe1e48b2487595d995d6c1b02f261ed9c033560a384e269452912ed93352e0058fe6d74ec6f2e56d38b0254a3ed67c7a6f357b4d80ed49376cec83fbfb162bf753a4fbdaa5d81cec68353fdf478c9cebabf699469d8cf3833ded06c48423dc3f1735b63a1214bb28b03067bb703fafc4203148ce5677135eb1a8e7fbab6a46d18f46d170e007fdfa4f1253a684810ad1c55e410ad9b52394ce6175bb2422cbf32a1c371c59875abc7ea10b0f4f329b4169593dba1c938019f6e2397aacad60c42c28f8d7c5df0",
                "391823123162368172645872".repeat(67)
        );
    }

    @Test
    public void fromInt9Huge() {
        // when converting to a base less than 10, the number needs more digits
        try {
            IntAscii.fromInt9(StandardBaseConversions.BINARY, Int9.fromString(AsciiDigits.fromScientific("9E999999999")));
            Assert.fail("Expecting IllegalStateException");
        } catch (IllegalStateException e) {
            System.out.println(e);
        }
    }

    private static void checkInt9(long value) {
        Int9 i = Int9.fromLong(value);
        IntAscii a = IntAscii.fromInt9(StandardBaseConversions.HEX, i);
        Assert.assertEquals(Long.toHexString(value), a.toString());
        a = IntAscii.fromInt9(i);
        Assert.assertEquals(Long.toString(value), a.toString());

        for (var base : BASES) {
            a = IntAscii.fromInt9(base, i);
            Assert.assertEquals(i.toString(), a.toInt9().toString());
            Assert.assertEquals(i, a.toInt9());
        }
    }

    private static void checkInt9(String expectedHex, String input) {
        Int9 i = Int9.fromString(input);
        IntAscii a = IntAscii.fromInt9(StandardBaseConversions.HEX, i);
        Assert.assertEquals(expectedHex, a.toString());
        a = IntAscii.fromInt9(i);
        Assert.assertEquals(input, a.toString());

        for (var base : BASES) {
            a = IntAscii.fromInt9(base, i);
            Assert.assertEquals(i.toString(), a.toInt9().toString());
            Assert.assertEquals(i, a.toInt9());
        }
    }

    @Test
    public void unmappable() {
        // unmappable chars are just zero
        var a = IntAscii.fromString("123...456");
        Assert.assertEquals(123000456, a.toInt());
        Assert.assertEquals(123000456, a.toLong());

        var b = IntAscii.fromString("123000456");
        Assert.assertEquals(123000456, b.toInt());
        Assert.assertEquals(123000456, b.toLong());

        Assert.assertNotEquals(a.toString(), b.toString());
        Assert.assertEquals(a, b);
    }

    @Test
    public void base32() {
        var base = SpecialBaseConversions.BASE_32();
        var a = IntAscii.fromLong(base, 0xFFFFFF);
        Assert.assertEquals("p7777", a.toString());
        a.addInPlace(IntAscii.fromInt(base, 1));
        Assert.assertEquals("qaaaa", a.toString());
        a.addInPlace(IntAscii.fromInt(base, 1));
        Assert.assertEquals("qaaab", a.toString());
        a.addInPlace(IntAscii.fromInt(base, 0));
        Assert.assertEquals("qaaab", a.toString());
        Assert.assertEquals(0xFFFFFF + 2, a.toInt());

        Assert.assertEquals("bcd", IntAscii.fromLong(base, 32*32*1 + 32*2 + 3).toString());
        Assert.assertEquals("eaaa", IntAscii.fromLong(base, 32*32*32*4).toString());
        Assert.assertEquals("3e000000", Long.toHexString(IntAscii.fromString(base, "7aaaaa").toLong()));
        Assert.assertEquals("3e000000", Long.toHexString(IntAscii.fromString(base, "7AAAAA").toLong()));
    }

    @Test
    public void base256() {
        var base = SpecialBaseConversions.BASE_256();
        var a = IntAscii.fromLong(base, 0xcafebabeL);
        Assert.assertEquals("cafebabe", a.toHexString());
        var b = IntAscii.multiplySimple(a, IntAscii.fromLong(base, 0x1000));
        Assert.assertEquals(0xcafebabe000L, b.toLong());
        Assert.assertEquals(256, b.getBase());
    }

    @Test
    public void base16() {
        var base = StandardBaseConversions.HEX;
        var a = IntAscii.fromLong(base, 0xcafebabeL);
        Assert.assertEquals("6361666562616265", a.toHexString());
        var b = IntAscii.multiplySimple(a, IntAscii.fromLong(base, 0x1000));
        Assert.assertEquals(0xcafebabe000L, b.toLong());
        Assert.assertEquals(16, b.getBase());
    }

    @Test
    public void base2() {
        var base = StandardBaseConversions.BINARY;
        var a = IntAscii.fromLong(base, 0xcafebabeL);
        Assert.assertEquals(Long.toBinaryString(0xcafebabeL), a.toString());
        a.addInPlace(IntAscii.fromInt(base, 1));
        Assert.assertEquals(Long.toBinaryString(0xcafebabeL + 1), a.toString());
        Assert.assertEquals(2, a.getBase());
    }

    @Test
    public void baseHex() {
        var base = StandardBaseConversions.HEX;
        var a = IntAscii.fromString(base, "10000");
        var b = IntAscii.fromString(base, "20000");
        Assert.assertEquals( "200000000", IntAscii.multiplySimple(a, b).toString());
        Assert.assertEquals(0x200000000L, IntAscii.multiplySimple(a, b).toLong());
        Assert.assertEquals(0x200000000L, 0x10000L * 0x20000L);
        Assert.assertEquals(16, b.getBase());
    }

    @Test
    public void baseHexNonAsciiAliasing() {
        var base = StandardBaseConversions.HEX;
        Assert.assertEquals(0x10000, IntAscii.fromString(base, "1....").toInt());
        Assert.assertEquals(0x10000, IntAscii.fromString(base, "1...\377").toInt());
        Assert.assertEquals(0xABA, IntAscii.fromString(base, "ABA").toInt());
         // octal \301 is 'A'+128 - we check 7-bit aliasing
        Assert.assertEquals(0xAB0, IntAscii.fromString(base, "AB\301").toInt());
    }

    @Test
    public void primitiveConversions() {
        int[] ints = { 1, 2, Integer.MAX_VALUE, 0xfffff, 10, 102, 1045, 10111, 123456, 1234567, 12345678, 123456789, 1234567890 };
        for (int value : ints) {
            checkIntConv(value);
            checkLongConv(value);
        }
        long[] longs = { 1, 2, Integer.MAX_VALUE, 0xfffff, 0xFFFFFFL, Long.MAX_VALUE/10, Long.MAX_VALUE/100, Long.MAX_VALUE/1000, Long.MAX_VALUE/1000, Long.MAX_VALUE/10000 };
        for (long value : longs) {
            checkLongConv(value);
        }
        Assert.assertTrue(IntAscii.fromInt(Integer.MAX_VALUE).isInt());
        Assert.assertFalse(IntAscii.fromLong(Integer.MAX_VALUE + 1L).isInt());
        Assert.assertTrue(IntAscii.fromLong(Integer.MAX_VALUE + 1L).isLong());
        Assert.assertTrue(IntAscii.fromLong(Long.MAX_VALUE).isLong());
        Assert.assertTrue(IntAscii.fromString("0"+Long.MAX_VALUE).isLong());
        Assert.assertFalse(IntAscii.fromString(Long.MAX_VALUE+"0").isLong());
    }

    private static void checkIntConv(int value) {
        var expected = IntAscii.fromString(""+value);
        var actual = IntAscii.fromInt(value);
        Assert.assertEquals(expected.toString(), actual.toString());
        Assert.assertEquals(expected, actual);
        Assert.assertEquals(value, actual.toInt());
        Assert.assertTrue(actual.isInt());

        for (var base : BASES) {
            actual = IntAscii.fromInt(base, value);
            Assert.assertEquals(value, actual.toInt());
            Assert.assertTrue(actual.isInt());
        }
    }

    private static void checkLongConv(long value) {
        var expected = IntAscii.fromString(""+value);
        var actual = IntAscii.fromLong(value);
        Assert.assertEquals(expected.toString(), actual.toString());
        Assert.assertEquals(expected, actual);
        Assert.assertEquals(value, actual.toLong());
        Assert.assertTrue(actual.isLong());

        for (var base : BASES) {
            actual = IntAscii.fromLong(base, value);
            Assert.assertEquals(value, actual.toLong());
            Assert.assertTrue(actual.isLong());
        }
    }

    @Test
    public void errors() {
        try {
            IntAscii.fromString("");
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            IntAscii.fromString("123", 2, 2);
            Assert.fail("Expecting NumberFormatException");
        } catch (NumberFormatException e) {
            System.out.println(e);
        }
        try {
            IntAscii.fromInt(-1);
            Assert.fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }
        try {
            IntAscii.fromLong(-2);
            Assert.fail("Expecting IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.out.println(e);
        }
    }

    @Test
    public void addInPlace() {
        var lhs = IntAscii.fromString("12345");
        lhs.addInPlace(IntAscii.fromInt(23456));
        Assert.assertEquals("35801", lhs.toString());
        Assert.assertEquals(35801, lhs.toInt());

        lhs.addInPlace(IntAscii.fromString("23456"));
        Assert.assertEquals("59257", lhs.toString());

        lhs.addInPlace(IntAscii.fromString("23456"));
        Assert.assertEquals("82713", lhs.toString());

        lhs.addInPlace(IntAscii.fromString("12345678"));
        Assert.assertEquals("12428391", lhs.toString());

        lhs.addInPlace(IntAscii.fromString("5"));
        Assert.assertEquals("12428396", lhs.toString());

        for (int i = 0; i < 100; i++) {
            lhs.addInPlace(IntAscii.fromString(""+i));
        }

        var b = IntAscii.fromString("0000000000000000012345");
        b.addInPlace(IntAscii.fromString("23456"));
        Assert.assertEquals("35801", b.toString());
    }

    @Test
    public void add() {
        checkAdd(ZERO, ZERO, ZERO);
        checkAdd(ZERO, ZERO, IntAscii.fromString("0"));
        checkAdd(IntAscii.fromString("1"), ZERO, IntAscii.fromString("1"));
        checkAdd(IntAscii.fromString("101"), IntAscii.fromString("101"), ZERO);
    }

    @Test
    public void addStr() {
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
    public void addBig() {
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
    }

    @Test
    public void subtractStr() {
        checkSubtractStr("100", "5");
        checkSubtractStr("99999999999999", "5");
        checkSubtractStr("9999999999999911111111111111111111111222222222222222222", "5");
        checkSubtractStr("9999999999999911111111111111111111111222222222222222222", "111111111111111111111115");
        checkSubtractStr("5", "5");
        checkSubtractStr("0", "0");
        checkSubtractStr("10", "20");
    }

    @Test
    public void random() {
        int repeats = 10_000;
        var rnd = new Random();
        long min = 0;
        long mid = 0x7FFFFFFFL;
        long max = Long.MAX_VALUE / 10; // prevent long overflow in test code path
        long lhs, rhs;

        while (repeats-- > 0) {
            lhs = random(rnd, min, max);
            rhs = random(rnd, min, max);
            checkAddStr(""+lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+rhs);

            lhs = random(rnd, mid, max);
            rhs = random(rnd, mid, max);
            checkAddStr(""+lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+rhs);

            lhs = random(rnd, min, mid);
            rhs = random(rnd, min, mid);
            checkAddStr(""+lhs, ""+rhs);
            checkMulStr(""+lhs, ""+rhs);
            checkSubtractStr(""+lhs, ""+rhs);
        }
    }

    @Test
    public void mulStr() {
        checkMulStr("0", "0");
        checkMulStr("0", "54354353");
        checkMulStr("1", "1");
        checkMulStr("2", "3");
        checkMulStr("999999999", "1");
        checkMulStr("999999999", "2");
        checkMulStr("543534", "654645");
        checkMulStr("999999999", "999999999");
        checkMulStr("42343232", "64564564545");
        checkMulStr("999999999", "999999999");
    }

    @Test
    public void mulStrBig() {
        checkMulStrBig("0", "0", "0");
        checkMulStrBig("1", "1", "1");

        checkMulStrBig("351314223566715967153201654060810044035211251953749909658504825035588175368399455464543237429221890910",
                "6459068045986045860945809684509684509860945860954890",
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

        checkMulStrBig("0",
                "9999999955555555333333333444444446556666666666666666666666666622222222222222222222222222222222222222222222222",
                "0"
        );
    }

    private void checkAddBig(String expected, String lhsStr, String rhsStr) {
        checkAddBig0(expected, lhsStr, rhsStr);
        checkAddBig0(expected, rhsStr, lhsStr);
    }

    private static void checkAddBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = IntAscii.fromString(lhsStr);
        var rhs = IntAscii.fromString(rhsStr);
        var sum = IntAscii.add(lhs, rhs);
        Assert.assertEquals(expected, sum.toString());

        ////////// in-place ///////////

        lhs.addInPlace(rhs);
        Assert.assertEquals(expected, lhs.toString());
    }

    private static void checkAddStr(String lhsStr, String rhsStr) {
        checkAddStr0(lhsStr, rhsStr);
        checkAddStr0(rhsStr, lhsStr);
    }

    private static void checkAddStr0(String lhsStr, String rhsStr) {
        var lhs = IntAscii.fromString(lhsStr);
        var rhs = IntAscii.fromString(rhsStr);
        var sum = IntAscii.add(lhs, rhs);

        long l = Long.parseLong(lhsStr);
        long r = Long.parseLong(rhsStr);
        long expected = Math.addExact(l, r);
        Assert.assertEquals(expected, Long.parseLong(sum.toString()));

        ////////// in-place ///////////

        lhs.addInPlace(rhs);
        Assert.assertEquals(expected, Long.parseLong(lhs.toString()));
    }

    private static void checkAdd(long expected, IntAscii lhs, IntAscii rhs) {
        checkAdd(IntAscii.fromString("" + expected), lhs, rhs);
    }

    private static void checkAdd(IntAscii expected, IntAscii lhs, IntAscii rhs) {
        checkAdd0(expected, lhs, rhs);
        checkAdd0(expected, rhs, lhs);
    }

    private static void checkAdd0(IntAscii expected, IntAscii lhs, IntAscii rhs) {
        Assert.assertEquals(expected.toString(), IntAscii.add(lhs, rhs).toString());
        Assert.assertEquals(expected, IntAscii.add(lhs, rhs));

        ////////// in-place ///////////

        var copy = lhs.copy();
        copy.addInPlace(rhs);
        Assert.assertEquals(expected.toString(), copy.toString());
        Assert.assertEquals(expected, copy);
    }

    private static void checkSubtractStr(String lhsStr, String rhsStr) {
        checkSubtractStr0(lhsStr, rhsStr);
        checkSubtractStr0(rhsStr, lhsStr);
    }

    private static void checkSubtractStr0(String lhsStr, String rhsStr) {
        var lhs = IntAscii.fromString(lhsStr);
        var rhs = IntAscii.fromString(rhsStr);
        var diff = IntAscii.subtract(lhs, rhs);

        var l = new BigInteger(lhsStr);
        var r = new BigInteger(rhsStr);
        var expected = l.subtract(r).abs();
        Assert.assertEquals(expected.toString(), diff.toString());

        ////////// in-place ///////////

        var copy = lhs.copy();
        int cmp = lhs.compareTo(rhs);
        boolean ret = copy.subtractInPlace(rhs);
        if (cmp <= 0) {
            Assert.assertFalse(ret);
            Assert.assertEquals("0", copy.toString());
            Assert.assertEquals(0, copy.toLong());
        } else {
            Assert.assertTrue(ret);
            Assert.assertEquals(expected.toString(), copy.toString());
        }
    }

    private static void checkMul(IntAscii expected, IntAscii lhs, IntAscii rhs) {
        checkMul0(expected, lhs, rhs);
        checkMul0(expected, rhs, lhs);
    }

    private static void checkMul0(IntAscii expected, IntAscii lhs, IntAscii rhs) {
        Assert.assertEquals(expected.toString(), IntAscii.multiplySimple(lhs, rhs).toString());
        Assert.assertEquals(expected.toString(), IntAscii.multiplyImpl(lhs, rhs).toString());
        Assert.assertEquals(expected.toString(), IntAscii.multiplyKaratsuba(lhs, rhs).toString());
        Assert.assertEquals(expected.toString(), IntAscii.multiplyKaratsuba(lhs, rhs, 1).toString());
        Assert.assertEquals(expected.toString(), IntAscii.multiplyKaratsuba(lhs, rhs, 1).toString());
        Assert.assertEquals(expected.toString(), IntAscii.parallelMultiplyKaratsuba(lhs, rhs, pool()).toString());
        Assert.assertEquals(expected.toString(), IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 1, 100, pool()).toString());
    }

    private static void checkMulStr(String lhsStr, String rhsStr) {
        checkMulStr0(lhsStr, rhsStr);
        checkMulStr0(rhsStr, lhsStr);
    }

    private static void checkMulStr0(String lhsStr, String rhsStr) {
        var lhs = IntAscii.fromString(lhsStr);
        var rhs = IntAscii.fromString(rhsStr);
        long l = Long.parseLong(lhsStr);
        long r = Long.parseLong(rhsStr);
        long expected = Math.multiplyExact(l, r);
        Assert.assertEquals(expected, Long.parseLong(IntAscii.multiplySimple(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(IntAscii.multiplyImpl(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(IntAscii.multiplyKaratsuba(lhs, rhs).toString()));
        Assert.assertEquals(expected, Long.parseLong(IntAscii.multiplyKaratsuba(lhs, rhs, 1).toString()));
        Assert.assertEquals(expected, Long.parseLong(IntAscii.parallelMultiplyKaratsuba(lhs, rhs, pool()).toString()));
        Assert.assertEquals(expected, Long.parseLong(IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 1, 100, pool()).toString()));
    }

    private static void checkMulStrBig(String expected, String lhsStr, String rhsStr) {
        checkMulStrBig0(expected, lhsStr, rhsStr);
        checkMulStrBig0(expected, rhsStr, lhsStr);
    }

    private static void checkMulStrBig0(String expected, String lhsStr, String rhsStr) {
        var lhs = IntAscii.fromString(lhsStr);
        var rhs = IntAscii.fromString(rhsStr);
        Assert.assertEquals(expected, IntAscii.multiplySimple(lhs, rhs).toString());
        Assert.assertEquals(expected, IntAscii.multiplyImpl(lhs, rhs).toString());
        Assert.assertEquals(expected, IntAscii.multiplyKaratsuba(lhs, rhs).toString());
        Assert.assertEquals(expected, IntAscii.multiplyKaratsuba(lhs, rhs, 1).toString());
        Assert.assertEquals(expected, IntAscii.parallelMultiplyKaratsuba(lhs, rhs, pool()).toString());
        Assert.assertEquals(expected, IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 1, 100, pool()).toString());
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
        checkString("12345", 3, 4);
        checkString("12345", 4, 5);
    }

    @Test
    public void byteSlices() {
        String str = "123456789011111222223333344444555556666677777888889999900000";
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);

        checkAdd(12345 + 67890,
                IntAscii.fromBytes(bytes, 0, 5),
                IntAscii.fromBytes(bytes, 5, 5)
        );
        checkAdd(1111122222L + 1111122222L,
                IntAscii.fromBytes(bytes, 10, 10),
                IntAscii.fromBytes(bytes, 10, 10)
        );
        checkAdd(1111122222L + 1111122222L,
                IntAscii.fromBytes(bytes, 10, 10).copy(),
                IntAscii.fromBytes(bytes, 10, 10)
        );
        checkAdd(1111122222L + 1111122222L,
                IntAscii.fromBytes(bytes, 10, 10),
                IntAscii.fromBytes(bytes, 10, 10).copy()
        );
        checkAdd(1111122222L + 1111122222L,
                IntAscii.fromBytes(bytes, 10, 10).copy(),
                IntAscii.fromBytes(bytes, 10, 10).copy()
        );
        checkAdd(IntAscii.fromString(str),
                IntAscii.fromBytes(bytes),
                IntAscii.fromBytes(bytes, bytes.length - 1, 1)
        );
        checkAdd(IntAscii.fromString(str),
                IntAscii.fromBytes(bytes),
                IntAscii.fromBytes(bytes, bytes.length - 5, 5)
        );

        checkAdd(IntAscii.fromString("246913578022222444446666688889111113333355555777779999800000"),
                IntAscii.fromBytes(bytes),
                IntAscii.fromBytes(bytes)
        );
        checkMul(IntAscii.fromString("246913578022222444446666688889111113333355555777779999800000"),
                IntAscii.fromBytes(bytes),
                IntAscii.fromBytes(bytes, 1, 1) // 2
        );
    }

    private static void checkString(String input) {
        int len = input.length();

        IntAscii value = IntAscii.fromString(input);
        Assert.assertEquals(input, value.toString());
        Assert.assertEquals(len, value.length());
        Assert.assertTrue(stringsEqual(input, value));
        Assert.assertTrue(stringsEqual(input, value.subSequence(0, len)));

        if (len > 2) {
            var inputSub = input.subSequence(1, len);
            var valueSub = value.subSequence(1, len);
            Assert.assertTrue(stringsEqual(inputSub, valueSub));
            Assert.assertEquals(inputSub.toString(), valueSub.toString());

            inputSub = inputSub.subSequence(0, len - 1);
            valueSub = valueSub.subSequence(0, len - 1);
            Assert.assertTrue(stringsEqual(inputSub, valueSub));
            Assert.assertEquals(inputSub.toString(), valueSub.toString());

            inputSub = inputSub.subSequence(1, len - 1);
            valueSub = valueSub.subSequence(1, len - 1);
            Assert.assertTrue(stringsEqual(inputSub, valueSub));
            Assert.assertEquals(inputSub.toString(), valueSub.toString());
        }

        var out = new ByteArrayOutputStream();
        value.stream(AsciiDigitArraySink.of(out));
        Assert.assertEquals(input, out.toString(StandardCharsets.UTF_8));

        checkString(input, 0, input.length());
    }

    private static void checkString(String input, int fromIndex, int toIndex) {
        IntAscii value = IntAscii.fromString(input, fromIndex, toIndex);
        Assert.assertEquals(input.substring(fromIndex, toIndex), value.toString());
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

    private static IntAscii fromBytes(int[] asInts) {
        byte[] asBytes = new byte[asInts.length];
        for (int i = 0; i < asBytes.length; i++) {
            asBytes[i] = (byte) asInts[i];
        }
        return IntAscii.fromBytes(asBytes);
    }

    private static void checkLeftRightPart0(String aExp, String bExp, String cExp, String dExp, int[] lhsValues, int[] rhsValues) {
        var lhs = fromBytes(lhsValues);
        var rhs = fromBytes(rhsValues);

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
}
