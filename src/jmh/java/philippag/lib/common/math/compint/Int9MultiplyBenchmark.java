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
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(1)
@State(Scope.Benchmark)
public class Int9MultiplyBenchmark {

    private static final String[] ARGS = {
            "589034583485345", "58903457894375873489578943534",
            "589034583485345492349238423842374237462346", "58903457894375873489578943534432949234823472374263462343526",
            "5".repeat(10_000), "6".repeat(100),
            "5".repeat(100_000), "6".repeat(50_000),
            "8".repeat(400_000), "3".repeat(150_000),
    };

    @Param({"10", "50", "200"})
    public int karatsubaThreshold;

    @Param({"1", "4", "16", "999"})
    public int maxDepth;

    private ForkJoinPool forkJoinPool;

    @Setup
    public void setup() {
        forkJoinPool = new ForkJoinPool();
    }

    @State(Scope.Benchmark)
    public static abstract class Args<T> {

        T[] args;

        @Setup
        public final void setup() {
            args = args();
        }

        abstract T[] args();
    }

    public static class ArgsBigInteger extends Args<BigInteger> {

        @Override
        BigInteger[] args() {
            return parse(BigInteger::new, BigInteger.class);
        }
    }

    public static class ArgsInt9 extends Args<Int9> {

        @Override
        Int9[] args() {
            return parse(Int9::fromString, Int9.class);
        }
    }

    @Benchmark
    public void multiplyJdkBigInteger(ArgsBigInteger args, Blackhole blackhole) {
        binary(args.args, BigInteger::multiply, blackhole);
    }

    @Benchmark
    public void parseAndMultiplyJdkBigInteger(Blackhole blackhole) {
        binary(BigInteger::new, BigInteger::multiply, blackhole);
    }

    @Benchmark
    public void multiplySimple(ArgsInt9 args, Blackhole blackhole) {
        binary(args.args, Int9::multiplySimple, blackhole);
    }

    @Benchmark
    public void parseAndMultiplySimple(Blackhole blackhole) {
        binary(Int9::fromString, Int9::multiplySimple, blackhole);
    }

    @Benchmark
    public void multiplyKaratsuba(ArgsInt9 args, Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.multiplyKaratsuba(lhs, rhs, karatsubaThreshold);
        binary(args.args, operator, blackhole);
    }

    @Benchmark
    public void parseAndMultiplyKaratsuba(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.multiplyKaratsuba(lhs, rhs, karatsubaThreshold);
        binary(Int9::fromString, operator, blackhole);
    }

    @Benchmark
    public void parallelMultiplyKaratsuba(ArgsInt9 args, Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        binary(args.args, operator, blackhole);
    }

    @Benchmark
    public void parseAndParallelMultiplyKaratsuba(Blackhole blackhole) {
        BinaryOperator<Int9> operator = (lhs, rhs) -> Int9.parallelMultiplyKaratsuba(lhs, rhs, karatsubaThreshold, maxDepth, forkJoinPool);
        binary(Int9::fromString, operator, blackhole);
    }

    private static <T> void binary(Function<String, T> factory, BinaryOperator<T> operator, Blackhole blackhole) {
        operator = symm(operator);

        for (int j = 0; j < ARGS.length;) {
            T lhs = factory.apply(ARGS[j++]);
            T rhs = factory.apply(ARGS[j++]);
            T result = operator.apply(lhs, rhs);
            blackhole.consume(result);
        }
    }

    private static <T> void binary(T[] args, BinaryOperator<T> operator, Blackhole blackhole) {
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

    private static <T> T[] parse(Function<String, T> factory, Class<T> cls) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(cls, ARGS.length);

        for (int i = 0; i < result.length; i++) {
            result[i] = factory.apply(ARGS[i]);
        }

        return result;
    }
}
