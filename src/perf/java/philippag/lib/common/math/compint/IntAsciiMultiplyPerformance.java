package philippag.lib.common.math.compint;

import java.math.BigInteger;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;

public class IntAsciiMultiplyPerformance extends CommonTestBase {

    private static final String[] BINARY_ARGS_BIG = {
            "589034583485345", "58903457894375873489578943534",
            "589034583485345492349238423842374237462346", "58903457894375873489578943534432949234823472374263462343526",
            "5".repeat(10_000), "6".repeat(100),
            "5".repeat(100_000), "6".repeat(5_000),
    };

    @Test
    public void mul() {
        ForkJoinPool pool = new ForkJoinPool();
        int REPEATS = 1;
        var ARGS = BINARY_ARGS_BIG;
        for (int i = 0; i < 3; i++) {
            binary("multiplySimple", IntAscii::fromString, IntAscii::multiplySimple, REPEATS, ARGS);

            binary("multiplyKaratsuba T=5", IntAscii::fromString, (lhs, rhs) -> IntAscii.multiplyKaratsuba(lhs, rhs, 5), REPEATS, ARGS);
            binary("multiplyKaratsuba T=50", IntAscii::fromString, (lhs, rhs) -> IntAscii.multiplyKaratsuba(lhs, rhs, 50), REPEATS, ARGS);
            binary("multiplyKaratsuba T=100", IntAscii::fromString, (lhs, rhs) -> IntAscii.multiplyKaratsuba(lhs, rhs, 100), REPEATS, ARGS);

            binary("parallelMultiplyKaratsuba T=5 D=1", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 5, 1, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=50 D=1", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 50, 1, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=100 D=1", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 100, 1, pool), REPEATS, ARGS);

            binary("parallelMultiplyKaratsuba T=5 D=4", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 5, 4, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=50 D=4", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 50, 4, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=100 D=4", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 100, 4, pool), REPEATS, ARGS);

            binary("parallelMultiplyKaratsuba T=5 D=16", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 5, 16, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=50 D=16", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 50, 16, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=100 D=16", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 100, 16, pool), REPEATS, ARGS);

            binary("parallelMultiplyKaratsuba T=5 D=999", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 5, 999, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=50 D=999", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 50, 999, pool), REPEATS, ARGS);
            binary("parallelMultiplyKaratsuba T=100 D=999", IntAscii::fromString, (lhs, rhs) -> IntAscii.parallelMultiplyKaratsuba(lhs, rhs, 100, 999, pool), REPEATS, ARGS);

            binary("multiply java.math.BigInteger", BigInteger::new, BigInteger::multiply, REPEATS, ARGS);

            System.out.println("=".repeat(120));
            System.out.println();
        }
    }

    private static <T> void binary(String desc, Function<String, T> factory, BinaryOperator<T> operator, int REPEATS, String[] ARGS) {
        long t0 = System.nanoTime();
        long top = 0;

        operator = symm(operator);

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
        System.out.printf(Locale.ROOT, "%40s %,15d total %,15d op %,15d diff\n", desc, t0 / 1000, top / 1000, (t0 - top) / 1000);
    }

    private static <T> BinaryOperator<T> symm(BinaryOperator<T> operator) {
        return (lhs, rhs) -> {
            operator.apply(rhs, lhs);
            return operator.apply(lhs, rhs);
        };
    }
}
