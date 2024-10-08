package philippag.lib.common.math;

import java.util.Random;

public abstract class CommonTestBase {

    protected CommonTestBase()  {
        boolean b = false;
        assert (b = true);
        if (isPerformanceTest()) {
            if (b) {
                throw new AssertionError("Assertions are enabled for performance test!");
            }
        } else {
            if (!b) {
                throw new AssertionError("Assertions are not enabled!");
            }
        }
    }

    private boolean isPerformanceTest() {
        return getClass().getName().endsWith("Performance");
    }

    public static int random(Random rnd, int min, int max) {
        return  rnd.nextInt(max - min + 1) + min;
    }

    public static long random(Random rnd, long min, long max) {
        return  rnd.nextLong(max - min + 1) + min;
    }

    public static String randomNumericString(Random rnd, int minLength, int maxLength) {
        return new String(randomNumericChars(rnd, minLength, maxLength));
    }

    private static char[] randomNumericChars(Random rnd, int minLength, int maxLength) {
        int length = random(rnd, minLength, maxLength);
        char[] chars = new char[length];
        char min = '1';
        char max = '9';
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) random(rnd, min, max);
            min = '0';
        }
        return chars;
    }

    public static boolean stringsEqual(CharSequence left, CharSequence right) {
        int len = left.length();
        if (len != right.length()) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (left.charAt(i) != right.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
