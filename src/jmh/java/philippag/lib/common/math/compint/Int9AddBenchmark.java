package philippag.lib.common.math.compint;

import java.lang.reflect.Array;
import java.math.BigInteger;
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
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(1)
@State(Scope.Benchmark)
public class Int9AddBenchmark {

    private static class Args {

        private static final String[] STRING = { // lhs > rhs
                "58903457894375873489578943534", "589034583485345",
                "58903457894375873489578943534432949234823472374263462343526", "589034583485345492349238423842374237462346",
                "5".repeat(10_000), "6".repeat(100),
                "5".repeat(100_000), "6".repeat(50_000),
                "8".repeat(400_000), "3".repeat(150_000),
        };

        private static final BigInteger[] BIG_INTEGER = parse(STRING, BigInteger::new, BigInteger.class);

        private static final Int9[] INT_9 = parse(STRING, Int9::fromString, Int9.class);
    }

    @Param({"false", "true"})
    public boolean reversed;

    @Benchmark
    public void addJdkBigInteger(Blackhole blackhole) {
        perform(Args.BIG_INTEGER, BigInteger::add, blackhole);
    }

    @Benchmark
    public void subtractJdkBigInteger(Blackhole blackhole) {
        perform(Args.BIG_INTEGER, BigInteger::subtract, blackhole);
    }

    @Benchmark
    public void addInt9(Blackhole blackhole) {
        perform(Args.INT_9, (l, r) -> l.add(r), blackhole);
    }

    @Benchmark
    public void subtractInt9(Blackhole blackhole) {
        perform(Args.INT_9, (l, r) -> l.subtract(r), blackhole);
    }

    @Benchmark
    public void addInPlaceInt9(Blackhole blackhole) {
        // we must copy() otherwise each call affects the next one!
        perform(Args.INT_9, (l, r) -> l.copy().addInPlace(r), blackhole);
    }

    @Benchmark
    public void subtractInPlaceInt9(Blackhole blackhole) {
        // we must copy() otherwise each call affects the next one!
        perform(Args.INT_9, (l, r) -> l.copy().subtractInPlace(r), blackhole);
    }

    private <T> void perform(T[] args, BinaryOperator<T> operator, Blackhole blackhole) {
        if (reversed) {
            for (int j = 0; j < args.length;) {
                T lhs = args[j++];
                T rhs = args[j++];
                T result = operator.apply(rhs, lhs); // reversed!
                blackhole.consume(result);
            }
        } else {
            for (int j = 0; j < args.length;) {
                T lhs = args[j++];
                T rhs = args[j++];
                T result = operator.apply(lhs, rhs);
                blackhole.consume(result);
            }
        }
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
