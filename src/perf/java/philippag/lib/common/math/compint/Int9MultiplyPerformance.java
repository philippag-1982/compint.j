package philippag.lib.common.math.compint;

import java.math.BigInteger;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.junit.Test;

import philippag.lib.common.math.CommonTestBase;

public class Int9MultiplyPerformance extends CommonTestBase {

    private static final String[] BINARY_ARGS_BIG = {
            "589034583485345", "58903457894375873489578943534",
            "589034583485345492349238423842374237462346", "58903457894375873489578943534432949234823472374263462343526",
            "5".repeat(10_000), "6".repeat(100),
            "5".repeat(100_000), "6".repeat(50_000),
            "8".repeat(400_000), "3".repeat(150_000),
    };

    @Test
    public void mul() {
        int REPEATS = 1;
        var ARGS = BINARY_ARGS_BIG;
        for (int i = 0; i < 5; i++) {
            binary("    MUL", Int9::fromString, Int9::multiplySimple, REPEATS, ARGS);
            binary("     K5", Int9::fromString, Mul::k5, REPEATS, ARGS);
            binary("    K50", Int9::fromString, Mul::k50, REPEATS, ARGS);
            binary("    K75", Int9::fromString, Mul::k75, REPEATS, ARGS);
            binary("   K100", Int9::fromString, Mul::k100, REPEATS, ARGS);

            binary("   P1K5", Int9::fromString, ParallelMul.MaxDepth1::k5, REPEATS, ARGS);
            binary("  P1K50", Int9::fromString, ParallelMul.MaxDepth1::k50, REPEATS, ARGS);
            binary("  P1K75", Int9::fromString, ParallelMul.MaxDepth1::k75, REPEATS, ARGS);
            binary(" P1K100", Int9::fromString, ParallelMul.MaxDepth1::k100, REPEATS, ARGS);

            binary("   P4K5", Int9::fromString, ParallelMul.MaxDepth4::k5, REPEATS, ARGS);
            binary("  P4K50", Int9::fromString, ParallelMul.MaxDepth4::k50, REPEATS, ARGS);
            binary("  P4K75", Int9::fromString, ParallelMul.MaxDepth4::k75, REPEATS, ARGS);
            binary(" P4K100", Int9::fromString, ParallelMul.MaxDepth4::k100, REPEATS, ARGS);

            binary("   P6K5", Int9::fromString, ParallelMul.MaxDepth6::k5, REPEATS, ARGS);
            binary("  P6K50", Int9::fromString, ParallelMul.MaxDepth6::k50, REPEATS, ARGS);
            binary("  P6K75", Int9::fromString, ParallelMul.MaxDepth6::k75, REPEATS, ARGS);
            binary(" P6K100", Int9::fromString, ParallelMul.MaxDepth6::k100, REPEATS, ARGS);

            binary(" P991K5", Int9::fromString, ParallelMul.MaxDepth99::k5, REPEATS, ARGS);
            binary(" P99K50", Int9::fromString, ParallelMul.MaxDepth99::k50, REPEATS, ARGS);
            binary(" P99K75", Int9::fromString, ParallelMul.MaxDepth99::k75, REPEATS, ARGS);
            binary("P99K100", Int9::fromString, ParallelMul.MaxDepth99::k100, REPEATS, ARGS);

            binary("    JDK", BigInteger::new, BigInteger::multiply, REPEATS, ARGS);
            System.out.println("=".repeat(100));
        }
    }

    private static class Mul {

        private static Int9 k5(Int9 lhs, Int9 rhs) {
            return Int9.multiplyKaratsuba(lhs, rhs, 5);
        }
        private static Int9 k50(Int9 lhs, Int9 rhs) {
            return Int9.multiplyKaratsuba(lhs, rhs, 50);
        }
        private static Int9 k75(Int9 lhs, Int9 rhs) {
            return Int9.multiplyKaratsuba(lhs, rhs, 75);
        }
        private static Int9 k100(Int9 lhs, Int9 rhs) {
            return Int9.multiplyKaratsuba(lhs, rhs, 100);
        }
    }

    private static class ParallelMul {

        private static ForkJoinPool pool = new ForkJoinPool();

        private static ForkJoinPool pool() {
            return pool;
        }

        private static class MaxDepth1 {
            private static Int9 k5(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 5, 1, pool());
            }
            private static Int9 k50(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 50, 1, pool());
            }
            private static Int9 k75(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 75, 1, pool());
            }
            private static Int9 k100(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 100, 1, pool());
            }
        }
        private static class MaxDepth4 {
            private static Int9 k5(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 5, 4, pool());
            }
            private static Int9 k50(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 50, 4, pool());
            }
            private static Int9 k75(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 75, 4, pool());
            }
            private static Int9 k100(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 100, 4, pool());
            }
        }
        private static class MaxDepth6 {
            private static Int9 k5(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 5, 6, pool());
            }
            private static Int9 k50(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 50, 6, pool());
            }
            private static Int9 k75(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 75, 6, pool());
            }
            private static Int9 k100(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 100, 6, pool());
            }
        }
        private static class MaxDepth99 {
            private static Int9 k5(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 5, 99, pool());
            }
            private static Int9 k50(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 50, 99, pool());
            }
            private static Int9 k75(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 75, 99, pool());
            }
            private static Int9 k100(Int9 lhs, Int9 rhs) {
                return Int9.parallelMultiplyKaratsuba(lhs, rhs, 100, 99, pool());
            }
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
        System.out.printf(Locale.ROOT, "[%s] %,15d total %,15d op %,15d diff\n", desc, t0 / 1000, top / 1000, (t0 - top) / 1000);
    }

    private static <T> BinaryOperator<T> symm(BinaryOperator<T> operator) {
        return (lhs, rhs) -> {
            operator.apply(rhs, lhs);
            return operator.apply(lhs, rhs);
        };
    }
}