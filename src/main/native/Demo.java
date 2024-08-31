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

        String lhs = "7".repeat(n[0]);
        String rhs = "8".repeat(n[1]);
        String as, bs, cs;
        long t = System.currentTimeMillis();
        if (useBigInteger) {
            var a = new BigInteger(lhs);
            var b = new BigInteger(rhs);
            var c = a.multiply(b);
            as = a.toString();
            bs = b.toString();
            cs = c.toString();
        } else if (useJavaInt9) {
            var a = Int9.fromString(lhs);
            var b = Int9.fromString(rhs);
            var c = a.multiply(b);
            as = a.toString();
            bs = b.toString();
            cs = c.toString();
        } else {
            var a = Int9N.fromString(lhs);
            var b = Int9N.fromString(rhs);
            var c = a.multiply(b);
            as = a.toString();
            bs = b.toString();
            cs = c.toString();
        }
        t = System.currentTimeMillis() - t;
        System.out.printf( "%s * %s = %s\n", as, bs, cs);
        System.err.printf(Locale.ROOT, "Digit count: %,d x %,d; use BigInteger: %s use Java Int9: %s time elapsed: %,d millis\n",
                n[0],
                n[1],
                useBigInteger ? "Y" : "N",
                useJavaInt9 ? "Y" : "N",
                t
        );
    }
}
