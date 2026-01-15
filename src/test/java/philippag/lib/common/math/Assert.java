package philippag.lib.common.math;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedClass;

/**
 * Bridge for org.junit.Assert from JUnit 4 => JUnit 5/6
 *
 * The order of (expected, actual) is preserved in {@link org.junit.jupiter.api.Assertions}.
 * However, the message is now the last argument, which used to be the first argument in org.junit.Assert.
 *
 * We intend to keep this class since it saves us from changing virtually every single line of test code.
 *
 * Annotation name changes from JUnit 4 -> JUnit 5/6:
 * - Before => {@link BeforeEach}
 * - After => {@link AfterEach}
 * - Ignore => {@link Disabled}
 *
 * Also, {@link ParameterizedClass} now works differently in JUnit 6.
 */
public final class Assert {

    public static void assertTrue(boolean condition) {
        Assertions.assertTrue(condition);
    }

    public static void assertTrue(String msg, boolean condition) {
        Assertions.assertTrue(condition, msg);
    }

    public static void assertFalse(boolean condition) {
        Assertions.assertFalse(condition);
    }

    public static void assertFalse(String msg, boolean condition) {
        Assertions.assertFalse(condition, msg);
    }

    public static void assertEquals(String msg, int lhs, int rhs) {
        Assertions.assertEquals(lhs, rhs, msg);
    }

    public static void assertEquals(int lhs, int rhs) {
        Assertions.assertEquals(lhs, rhs);
    }

    public static void assertEquals(String msg, Object lhs, Object rhs) {
        Assertions.assertEquals(lhs, rhs, msg);
    }

    public static void assertEquals(Object lhs, Object rhs) {
        Assertions.assertEquals(lhs, rhs);
    }

    public static void assertEquals(String msg, long lhs, long rhs) {
        Assertions.assertEquals(lhs, rhs, msg);
    }

    public static void assertEquals(long lhs, long rhs) {
        Assertions.assertEquals(lhs, rhs);
    }

    public static void assertEquals(String msg, double lhs, double rhs, double delta) {
        Assertions.assertEquals(lhs, rhs, delta, msg);
    }

    public static void assertEquals(double lhs, double rhs, double delta) {
        Assertions.assertEquals(lhs, rhs, delta);
    }

    public static void assertNotEquals(String msg, Object lhs, Object rhs) {
        Assertions.assertNotEquals(lhs, rhs, msg);
    }

    public static void assertNotEquals(Object lhs, Object rhs) {
        Assertions.assertNotEquals(lhs, rhs);
    }

    public static void assertArrayEquals(String msg, byte[] lhs, byte[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs, msg);
    }

    public static void assertArrayEquals(byte[] lhs, byte[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs);
    }

    public static void assertArrayEquals(String msg, char[] lhs, char[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs, msg);
    }

    public static void assertArrayEquals(char[] lhs, char[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs);
    }

    public static void assertArrayEquals(String msg, int[] lhs, int[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs, msg);
    }

    public static void assertArrayEquals(int[] lhs, int[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs);
    }

    public static void assertArrayEquals(String msg, long[] lhs, long[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs, msg);
    }

    public static void assertArrayEquals(long[] lhs, long[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs);
    }

    public static void assertArrayEquals(String msg, Object[] lhs, Object[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs, msg);
    }

    public static void assertArrayEquals(Object[] lhs, Object[] rhs) {
        Assertions.assertArrayEquals(lhs, rhs);
    }

    public static void fail() {
        Assertions.fail();
    }

    public static void fail(String msg) {
        Assertions.fail(msg);
    }

    public static void assertSame(String msg, Object lhs, Object rhs) {
        Assertions.assertSame(lhs, rhs, msg);
    }

    public static void assertSame(Object lhs, Object rhs) {
        Assertions.assertSame(lhs, rhs);
    }

    public static void assertNotSame(String msg, Object lhs, Object rhs) {
        Assertions.assertNotSame(lhs, rhs, msg);
    }

    public static void assertNotSame(Object lhs, Object rhs) {
        Assertions.assertNotSame(lhs, rhs);
    }

    public static void assertNull(String msg, Object obj) {
        Assertions.assertNull(obj, msg);
    }

    public static void assertNull(Object obj) {
        Assertions.assertNull(obj);
    }

    public static void assertNotNull(String msg, Object obj) {
        Assertions.assertNotNull(obj, msg);
    }

    public static void assertNotNull(Object obj) {
        Assertions.assertNotNull(obj);
    }
}
