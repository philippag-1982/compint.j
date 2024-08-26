package philippag.lib.common.math.compint;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;

public class BigIntMemoryPerformance extends CommonTestBase {

    @Test
    public void hugeModifyInt9() {
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
    public void hugeModifyIntAscii() {
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
            //System.out.println(e);
        }

        System.out.printf("[IntAscii] modification completed successfully for length %,d\n", length);
    }

    @Test
    public void stringOps() {
        var rnd = new Random();
        int[] lengths = { 1 << 18, 1 << 24, 1 << 28, 1 << 30, 0x7FFF_0000 };
        for (int i = 0; i < 3; i++) {
            for (int length : lengths) {
                String str = randomNumericString(rnd, length, length);
                str("Int9", str, Int9::fromString);
                str("IntAscii", str, IntAscii::fromString);
                if (length == lengths[0]) {
                    str("BigInteger", str, BigInteger::new);
                }
                System.out.println("=".repeat(130));
            }
            System.out.println();
        }
    }

    private static <T> void str(String desc, String str, Function<String, T> factory) {
        int length = str.length();
        long t = System.nanoTime();
        var x = factory.apply(str);
        t = System.nanoTime() - t;
        System.out.printf(Locale.ROOT, "%12s %25s %,20d chars %,20d micros %,20d nanos\n", desc, "from string construction", length, t / 1000, t);

        t = System.nanoTime();
        String str2 = x.toString();
        t = System.nanoTime() - t;
        System.out.printf(Locale.ROOT, "%12s %25s %,20d chars %,20d micros %,20d nanos\n", desc, "to string conversion", length, t / 1000, t);

        Assert.assertEquals(str, str2);

        int wrong = 0;
        t = System.nanoTime();
        /*
         * These ugly `instanceof <class-type>` checks are on purpose
         * to prevent `invokeinterface` opcode for the `CharSequence` interface.
         */
        if (x instanceof Int9 cs) {
            for (int i = 0, len = cs.length(); i < len; i++) {
                char c = cs.charAt(i);
                if (c < '0' || c > '9') {
                    // i.e. blackhole.consume()
                    wrong++;
                }
            }
        } else if (x instanceof IntAscii cs) {
            for (int i = 0, len = cs.length(); i < len; i++) {
                char c = cs.charAt(i);
                if (c < '0' || c > '9') {
                    // i.e. blackhole.consume()
                    wrong++;
                }
            }
        } else {
            return;
        }
        t = System.nanoTime() - t;
        System.out.printf(Locale.ROOT, "%12s %25s %,20d chars %,20d micros %,20d nanos\n", desc, "random access", length, t / 1000, t);
        if (wrong > 0) {
            throw new AssertionError();
        }
    }
}
