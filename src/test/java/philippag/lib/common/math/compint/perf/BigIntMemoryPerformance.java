package philippag.lib.common.math.compint.perf;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;
import philippag.lib.common.math.compint.Int9;
import philippag.lib.common.math.compint.IntAscii;

public class BigIntMemoryPerformance extends CommonTestBase {

    @Override
    protected boolean isPerformanceTest() {
        return true;
    }

    @Test
    public void stringOps() {
        int[] lengths = { 1 << 18, 1 << 24, 1 << 28, 1 << 30, 0x7FFF_0000 };
        for (int i = 0; i < 3; i++) {
            for (int length : lengths) {
                str("Int9 ", length, Int9::fromString);
                str("Ascii", length, IntAscii::fromString);
                if (length == lengths[0]) {
                    str("JDK  ", length, BigInteger::new);
                }
                System.out.println("=".repeat(120));
            }
            System.out.println();
        }
    }

    @Test
    public void hugeModify9() {
        int length = 1 << 30;
        String str = "1".repeat(length);
        var x = Int9.fromString(str);
        Assert.assertEquals(str, x.toString());

        x.incrementInPlace();
        str = "1".repeat(length - 1) + "2";
        Assert.assertEquals(str, x.toString());

        System.out.printf("[Int9] modification completed successfully for length %,d\n", length);
    }

    @Test
    public void hugeModifyAscii() {
        int length = 1 << 30;
        String str = "1".repeat(length);
        var x = IntAscii.fromString(str);
        Assert.assertEquals(str, x.toString());

        x.addInPlace(IntAscii.fromInt(1));
        str = "1".repeat(length - 1) + "2";
        Assert.assertEquals(str, x.toString());

        try {
            x.toHexByteArray();
            Assert.fail("Expecting IllegalStateException");
        } catch (IllegalStateException e) {
            System.out.println(e);
        }

        System.out.printf("[IntAscii] modification completed successfully for length %,d\n", length);
    }

    static Random rnd = new Random();

    private static <T> void str(String desc, int length, Function<String, T> factory) {
        String str = randomNumericString(rnd, length, length);
        long t = System.nanoTime();
        var x = factory.apply(str);
        t = System.nanoTime() - t;
        System.out.printf(Locale.ROOT, "[%s] %,20d chars %,20d micros %,20d nanos [%s]\n", desc, length, t / 1000, t, "from string construction");

        t = System.nanoTime();
        String str2 = x.toString();
        t = System.nanoTime() - t;
        System.out.printf(Locale.ROOT, "[%s] %,20d chars %,20d micros %,20d nanos [%s]\n", desc, length, t / 1000, t, "to string conversion");

        Assert.assertEquals(str, str2);

        if (x instanceof CharSequence cs) {
            int wrong = 0;
            t = System.nanoTime();
            for (int i = 0, len = cs.length(); i < len; i++) {
                char c = cs.charAt(i);
                if (c < '0' || c > '9') {
                    // i.e. blackhole.consume()
                    wrong++;
                }
            }
            t = System.nanoTime() - t;
            System.out.printf(Locale.ROOT, "[%s] %,20d chars %,20d micros %,20d nanos [%s]\n", desc, length, t / 1000, t, "random access");
            if (wrong > 0) {
                throw new AssertionError();
            }
        }
    }
}
