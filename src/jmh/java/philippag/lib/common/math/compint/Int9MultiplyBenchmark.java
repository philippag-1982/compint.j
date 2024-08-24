package philippag.lib.common.math.compint;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(1)
@State(Scope.Benchmark)
public class Int9MultiplyBenchmark {

    private static class Args {

        private static final String[] STRING = {
                "589034583485345", "58903457894375873489578943534",
                "589034583485345492349238423842374237462346", "58903457894375873489578943534432949234823472374263462343526",
                "5".repeat(10_000), "6".repeat(100),
                "5".repeat(100_000), "6".repeat(50_000),
                "8".repeat(400_000), "3".repeat(150_000),
        };

        private static final BigInteger[] BIG_INTEGER = parse(STRING, BigInteger::new, BigInteger.class);
        private static final Int9[] INT_9 = parse(STRING, Int9::fromString, Int9.class);
        private static final Int9N[] INT_9N = parse(STRING, Int9N::fromString, Int9N.class);
    }

//    @Param({"10", "40", "80"})
    @Param({"40"})
    public int karatsubaThreshold;

//    @Param({"1", "4", "16", "999"})
    @Param({"8"})
    public int maxDepth;

    private ForkJoinPool forkJoinPool;

    @Setup
    public void setup() {
        forkJoinPool = new ForkJoinPool();
    }

    @Benchmark
    public void multiplyJdkBigInteger(Blackhole blackhole) {
        perform(Args.BIG_INTEGER, BigInteger::multiply, blackhole);
    }

    @Benchmark
    public void parallelMultiplyJdkBigInteger(Blackhole blackhole) {
        perform(Args.BIG_INTEGER, BigInteger::parallelMultiply, blackhole);
    }

    @Benchmark
    public void parseAndMultiplyJdkBigInteger(Blackhole blackhole) {
        parseAndPerform(Args.STRING, BigInteger::new, BigInteger::multiply, blackhole);
    }

    @Benchmark
    public void parseAndParallelMultiplyJdkBigInteger(Blackhole blackhole) {
        parseAndPerform(Args.STRING, BigInteger::new, BigInteger::parallelMultiply, blackhole);
    }

    @Benchmark
    public void multiplySimpleInt9(Blackhole blackhole) {
        perform(Args.INT_9, Int9::multiplySimple, blackhole);
    }

    @Benchmark
    public void multiplySimpleInt9N(Blackhole blackhole) {
        if (!Int9N.nativeLibAvailable) {
            return;
        }
        perform(Args.INT_9N, Int9N::multiplySimple, blackhole);
    }

    @Benchmark
    public void parseAndMultiplySimpleInt9(Blackhole blackhole) {
        parseAndPerform(Args.STRING, Int9::fromString, Int9::multiplySimple, blackhole);
    }

    @Benchmark
    public void parseAndMultiplySimpleInt9N(Blackhole blackhole) {
        if (!Int9N.nativeLibAvailable) {
            return;
        }
        parseAndPerform(Args.STRING, Int9N::fromString, Int9N::multiplySimple, blackhole);
    }

    @Benchmark
    public void multiplyKaratsubaInt9(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.multiplyKaratsuba(lhs, rhs, karatsubaThreshold);
        perform(Args.INT_9, operator, blackhole);
    }

    @Benchmark
    public void multiplyKaratsubaInt9N(Blackhole blackhole) {
        if (!Int9N.nativeLibAvailable) {
            return;
        }
        BinaryOperator<Int9N> operator = (lhs, rhs) -> Int9N.multiplyKaratsuba(lhs, rhs, karatsubaThreshold);
        perform(Args.INT_9N, operator, blackhole);
    }

    @Benchmark
    public void parseAndMultiplyKaratsubaInt9(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.multiplyKaratsuba(lhs, rhs, karatsubaThreshold);
        parseAndPerform(Args.STRING, Int9::fromString, operator, blackhole);
    }

    @Benchmark
    public void parallelMultiplyKaratsubaInt9(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        perform(Args.INT_9, operator, blackhole);
    }

    @Benchmark
    public void parallelMultiplyKaratsubaInt9N(Blackhole blackhole) {
        if (!Int9N.nativeLibAvailable) {
            return;
        }
        BinaryOperator<Int9N> operator = (lhs, rhs) -> Int9N.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        perform(Args.INT_9N, operator, blackhole);
    }

    @Benchmark
    public void parseAndParallelMultiplyKaratsubaInt9(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        parseAndPerform(Args.STRING, Int9::fromString, operator, blackhole);
    }

    @Benchmark
    public void parseAndParallelMultiplyKaratsubaInt9N(Blackhole blackhole) {
        if (!Int9N.nativeLibAvailable) {
            return;
        }
        BinaryOperator<Int9N> operator = (lhs, rhs) -> Int9N.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        parseAndPerform(Args.STRING, Int9N::fromString, operator, blackhole);
    }

    private static <T> void parseAndPerform(String[] ARGS, Function<String, T> factory, BinaryOperator<T> operator, Blackhole blackhole) {
        operator = symm(operator);

        for (int j = 0; j < ARGS.length;) {
            T lhs = factory.apply(ARGS[j++]);
            T rhs = factory.apply(ARGS[j++]);
            T result = operator.apply(lhs, rhs);
            blackhole.consume(result);
        }
    }

    private static <T> void perform(T[] args, BinaryOperator<T> operator, Blackhole blackhole) {
        operator = symm(operator);

        for (int j = 0; j < args.length;) {
            T lhs = args[j++];
            T rhs = args[j++];
            T result = operator.apply(lhs, rhs);
            blackhole.consume(result);
        }
    }

    private static <T> BinaryOperator<T> symm(BinaryOperator<T> operator) {
        return (lhs, rhs) -> {
            operator.apply(rhs, lhs);
            return operator.apply(lhs, rhs);
        };
    }

    private static <T> T[] parse(String[] ARGS, Function<String, T> factory, Class<T> cls) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(cls, ARGS.length);

        for (int i = 0; i < result.length; i++) {
            result[i] = factory.apply(ARGS[i]);
        }

        return result;
    }
}
