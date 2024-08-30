import java.math.BigInteger;
import java.util.Locale;

import philippag.lib.common.math.compint.Int9;
import philippag.lib.common.math.compint.Int9N;

public class Demo {

    public static void main(String[] args) {
        int[] n = { 100_000, 100_000 };
        int idx = 0;
        boolean useBigInteger = false;
        boolean useJavaInt9 = false;

        for (String arg : args) {
            if ("-b".equals(arg)) {
                useBigInteger = true;
                continue;
            }
            if ("-j".equals(arg)) {
                useJavaInt9 = true;
                continue;
            }
            n[idx++] = Integer.parseInt(arg);
        }

        long t = System.currentTimeMillis();
        String lhs = "7".repeat(n[0]);
        String rhs = "8".repeat(n[1]);
        if (useBigInteger) {
            var a = new BigInteger(lhs);
            var b = new BigInteger(rhs);
            var c = a.multiply(b);
            System.out.printf( "%s * %s = %s\n", a, b, c);
        } else if (useJavaInt9) {
            var a = Int9.fromString(lhs);
            var b = Int9.fromString(rhs);
            var c = a.multiply(b);
            System.out.printf("%s * %s = %s\n", a, b, c);
        } else {
            var a = Int9N.fromString(lhs);
            var b = Int9N.fromString(rhs);
            var c = a.multiply(b);
            System.out.printf("%s * %s = %s\n", a, b, c);
        }
        t = System.currentTimeMillis() - t;

        System.err.printf(Locale.ROOT, "Digit count: %,d x %,d; use BigInteger: %s use Java Int9: %s time elapsed: %,d millis\n",
                n[0],
                n[1],
                useBigInteger ? "Y" : "N",
                useJavaInt9 ? "Y" : "N",
                t
        );
    }
}
