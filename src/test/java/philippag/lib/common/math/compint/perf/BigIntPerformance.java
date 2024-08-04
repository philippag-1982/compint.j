package philippag.lib.common.math.compint.perf;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.After;
import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;
import philippag.lib.common.math.compint.Int9;
import philippag.lib.common.math.compint.IntAscii;

public class BigIntPerformance extends CommonTestBase {

    @Override
    protected boolean isPerformanceTest() {
        return true;
    }

    private static final Int9 ONE = Int9.fromInt(1);

    private static final String[] BINARY_ARGS_BIG = {
            "589034583485345", "58903457894375873489578943534",
            "589034583485345492349238423842374237462346", "58903457894375873489578943534432949234823472374263462343526",
            "5".repeat(10_000), "6".repeat(100),
    };

    private static final String[] BINARY_ARGS_BIG_LEFT = {
            "58903457894375873489578943534", "423432432",
            "589034583485345492349238423842374237462346", "423423",
            "589034583485345492349238423842374237462346", "-423423",
            "-589034583485345492349238333333333333333333333333423842374237462346", "-423423",
            "5".repeat(10_000), "600",
    };

    private static final String[] BINARY_ARGS_LONG = {
            "43", "661",
            "4313", "89",
            "4313423423", "89",
            "9456467577", "893",
            "1456467577", "13",
    };

    private static final String[] BINARY_ARGS_LONG_NEG = {
            "43", "661",
            "4313", "89",
            "4313423423", "89",
            "9456467577", "893",
            "1456467577", "13",
            "1456467577", "-13",
            "-1456467577", "13",
            "-1456467577", "-13",
    };

    private static final String[] UNARY_ARGS_BIG = {
            "0",
            "58903457894375873489578943534",
            "8".repeat(10_000),
            "-" + "7".repeat(10_000),
    };

    @Test
    public void pow() {
        int REPEATS = 100;
        var ARGS = BINARY_ARGS_LONG;
        boolean symm = false;
        binary(symm, "Int9  POW", Int9::fromString, Pow::pow, REPEATS, ARGS);
        binary(symm, "Int9 PPOW", Int9::fromString, Pow::ppow, REPEATS, ARGS);
        binary(symm, "JDK   POW", BigInteger::new, Pow::pow, REPEATS, ARGS);
    }

    @Test
    public void div() {
        int REPEATS = 1_000;
        var ARGS = BINARY_ARGS_BIG_LEFT;
        boolean symm = false;
        binary(symm, "Int9  DIV", Int9::fromString, Div::div, REPEATS, ARGS);
        binary(symm, "JDK   DIV", BigInteger::new, BigInteger::divide, REPEATS, ARGS);
    }

    @Test
    public void mul() {
        int REPEATS = 10;
        var ARGS = BINARY_ARGS_BIG;
        binary("Int9  MUL", Int9::fromString, Int9::multiplySimple, REPEATS, ARGS);
        binary("Int9  MRP", Int9::fromString, Int9::multiplyRussianPeasant, REPEATS, ARGS);
        binary("Int9  MAK", Int9::fromString, Int9::multiplyKaratsuba, REPEATS, ARGS);
        binary("Ascii MUL", IntAscii::fromString, IntAscii::multiplySimple, REPEATS, ARGS);
        binary("Ascii MAK", IntAscii::fromString, IntAscii::multiplyKaratsuba, REPEATS, ARGS);
        binary("JDK   MUL", BigInteger::new, BigInteger::multiply, REPEATS, ARGS);
    }

    @Test
    public void add() {
        int REPEATS = 100;
        var ARGS = BINARY_ARGS_BIG;
        binary("Int9  ADD", Int9::fromString, Add::add, REPEATS, ARGS);
        binary("Ascii ADD", IntAscii::fromString, Add::add, REPEATS, BINARY_ARGS_BIG);
        binary("JDK   ADD", BigInteger::new, BigInteger::add, REPEATS, BINARY_ARGS_BIG);
    }

    @Test
    public void addInPlace() {
        int REPEATS = 100_000;
        var ARGS = BINARY_ARGS_LONG_NEG;
        binary    ("Int9  AIP", Int9::fromString, AddInPlace::add, REPEATS, ARGS);
        binaryLong("Int9 pAIP", Int9::fromString, Int9::addInPlace, REPEATS, ARGS);
        binary    ("Ascii AIP", IntAscii::fromString, AddInPlace::add, REPEATS, ARGS);
    }

    @Test
    public void subtractInPlace() {
        int REPEATS = 100_000;
        var ARGS = BINARY_ARGS_LONG_NEG;
        binary    ("Int9  SIP", Int9::fromString, AddInPlace::subtract, REPEATS, ARGS);
        binaryLong("Int9 pSIP", Int9::fromString, Int9::subtractInPlace, REPEATS, ARGS);
    }

    @Test
    public void incdec() {
        int REPEATS = 10_000;
        var ARGS = UNARY_ARGS_BIG;

        unary("Int9  INC   ", Int9::fromString, Int9::incrementInPlace, REPEATS, ARGS);
        unary("Int9 pAIP(1)", Int9::fromString, Inc::addOne, REPEATS, ARGS);
        unary("Int9  AIP(1)", Int9::fromString, Inc::addOneObj, REPEATS, ARGS);
        unary("Int9  ADD(1)", Int9::fromString, Inc::addOneFun, REPEATS, ARGS);

        unary("Int9  DEC   ", Int9::fromString, Int9::decrementInPlace, REPEATS, ARGS);
        unary("Int9 pSIP(1)", Int9::fromString, Inc::subtractOne, REPEATS, ARGS);
        unary("Int9  SIP(1)", Int9::fromString, Inc::subtractOneObj, REPEATS, ARGS);
        unary("Int9  SUB(1)", Int9::fromString, Inc::subtractOneFun, REPEATS, ARGS);
    }

    @Test
    public void str() {
        int REPEATS = 100;
        var ARGS = UNARY_ARGS_BIG;
        unary("Int9  STR", Int9::fromString, Object::toString, REPEATS, ARGS);
        unary("Ascii STR", IntAscii::fromString, Object::toString, REPEATS, ARGS);
        unary("JDK   STR", BigInteger::new, Object::toString, REPEATS, ARGS);
    }

    @Test
    public void stream() throws IOException {
        int REPEATS = 100;
        var ARGS = UNARY_ARGS_BIG;
        unary("Int9  STM", Int9::fromString, Streams::take, REPEATS, ARGS);
        unary("Ascii STM", IntAscii::fromString, Streams::take, REPEATS, ARGS);
        unary("JDK   STM", BigInteger::new, Streams::take, REPEATS, ARGS);
    }

    private static class Div {

        private static Int9 div(Int9 lhs, Int9 rhs) {
            assert rhs.isInt();
            lhs.divideInPlace(rhs.toInt());
            return lhs;
        }
    }

    private static class Pow {

        private static BigInteger pow(BigInteger lhs, BigInteger rhs) {
            return lhs.pow(rhs.intValue());
        }

        private static Int9 pow(Int9 lhs, Int9 rhs) {
            return Int9.pow(lhs, rhs.toInt());
        }

        private static ForkJoinPool pool = new ForkJoinPool();

        private static Int9 ppow(Int9 lhs, Int9 rhs) {
            return Int9.parallelPow(lhs, rhs.toInt(), pool);
        }
    }

    private static class Add {

        private static Int9 add(Int9 lhs, Int9 rhs) {
            return Int9.add(lhs, rhs);
        }

        private static IntAscii add(IntAscii lhs, IntAscii rhs) {
            return IntAscii.add(lhs, rhs);
        }
    }

    private static class AddInPlace {

        private static Int9 add(Int9 lhs, Int9 rhs) {
            lhs.addInPlace(rhs);
            return lhs;
        }

        private static Int9 subtract(Int9 lhs, Int9 rhs) {
            lhs.subtractInPlace(rhs);
            return lhs;
        }

        private static IntAscii add(IntAscii lhs, IntAscii rhs) {
            lhs.addInPlace(rhs);
            return lhs;
        }
    }

    private static class Inc {

        private static void addOne(Int9 x) {
            x.addInPlace(1);
        }

        private static void addOneObj(Int9 x) {
            x.addInPlace(ONE);
        }

        private static void addOneFun(Int9 x) {
            x.add(ONE);
        }

        private static void subtractOne(Int9 x) {
            x.subtractInPlace(1);
        }

        private static void subtractOneObj(Int9 x) {
            x.subtractInPlace(ONE);
        }

        private static void subtractOneFun(Int9 x) {
            x.subtract(ONE);
        }
    }

    private static class Streams {

        private static boolean accept(byte[] a, int offset, int length) {
            // ignore data, we only test streaming perf itself
            return true;
        }

        private static void take(Int9 value) {
            value.stream(Streams::accept);
        }

        private static void take(IntAscii value) {
            value.stream(Streams::accept);
        }

        private static void take(BigInteger value) {
            byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            accept(bytes, 0, bytes.length);
        }
    }

    private static <T> void binary(String desc, Function<String, T> factory, BinaryOperator<T> operator, int REPEATS, String[] ARGS) {
        binary(/*symm*/ true, desc, factory, operator, REPEATS, ARGS);
    }

    private static <T> void binary(boolean symm, String desc, Function<String, T> factory, BinaryOperator<T> operator, int REPEATS, String[] ARGS) {
        long t0 = System.nanoTime();
        long top = 0;

        if (symm) {
            operator = symm(operator);
        }

        for (int i = 0; i < REPEATS; i++) {
            for (int j = 0; j < ARGS.length;) {
                T lhs = factory.apply(ARGS[j++]);
                T rhs = factory.apply(ARGS[j++]);

                long t1 = System.nanoTime();
                T result = operator.apply(lhs, rhs);
                t1 = System.nanoTime() - t1;

                assert result != null;
                top += t1;
            }
        }

        t0 = System.nanoTime() - t0;
        System.out.printf(Locale.ROOT, "[%12s] %,15d total %,15d op %,15d diff\n", desc, t0 / 1000, top / 1000, (t0 - top) / 1000);
    }

    private interface LongOperator<T> {

        void apply(T value, long arg);
    }

    private static <T> void binaryLong(String desc, Function<String, T> factory, LongOperator<T> operator, int REPEATS, String[] ARGS) {
        long t0 = System.nanoTime();
        long top = 0;

        for (int i = 0; i < REPEATS; i++) {
            for (int j = 0; j < ARGS.length;) {
                T lhs = factory.apply(ARGS[j++]);
                long rhs = Long.parseLong(ARGS[j++]);

                long t1 = System.nanoTime();
                operator.apply(lhs, rhs);
                t1 = System.nanoTime() - t1;

                top += t1;
            }
        }

        t0 = System.nanoTime() - t0;
        System.out.printf(Locale.ROOT, "[%12s] %,15d total %,15d op %,15d diff\n", desc, t0 / 1000, top / 1000, (t0 - top) / 1000);
    }

    private static <T> BinaryOperator<T> symm(BinaryOperator<T> operator) {
        return (lhs, rhs) -> {
            operator.apply(rhs, lhs);
            return operator.apply(lhs, rhs);
        };
    }

    private static <T> void unary(String desc, Function<String, T> factory, Consumer<T> action, int REPEATS, String[] ARGS) {
        long t0 = System.nanoTime();
        long top = 0;

        for (int i = 0; i < REPEATS; i++) {

            for (String arg : ARGS) {
                T value = factory.apply(arg);

                long t1 = System.nanoTime();
                action.accept(value);
                t1 = System.nanoTime() - t1;

                top += t1;
            }
        }

        t0 = System.nanoTime() - t0;
        System.out.printf(Locale.ROOT, "[%12s] %,15d total %,15d op %,15d diff\n", desc, t0 / 1000, top / 1000, (t0 - top) / 1000);
    }

    @After
    public void after() {
        System.out.println("-".repeat(100));
        System.out.println();
    }
}
