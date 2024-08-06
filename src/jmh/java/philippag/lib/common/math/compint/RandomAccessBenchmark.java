package philippag.lib.common.math.compint;

import java.util.Random;
import java.util.concurrent.TimeUnit;

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

import philippag.lib.common.math.CommonTestBase;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode({Mode.AverageTime, Mode.SingleShotTime})
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@Fork(value = 1, jvmArgs = "-Xmx8G")
@State(Scope.Benchmark)
public class RandomAccessBenchmark {

    private static Random rnd = createRandom();

    private static Random createRandom() {
        var rnd = new Random();
        rnd.setSeed(1000);
        return rnd;
    }

//    @Param({"262144", "16777216", "268435456", "1073741824", "2147418112"})
    @Param({"1073741824", "2147418112"})
    public int length;

    Int8 int8;
    Int9 int9;
    IntAscii intAscii;

    @Setup
    public void setup() {
        String str = CommonTestBase.randomNumericString(rnd, length, length);
        int8 = Int8.fromString(str);
        int9 = Int9.fromString(str);
        intAscii = IntAscii.fromString(str);
    }

    @Benchmark
    public void randomAccessInt8(Blackhole blackhole) {
        for (int i = 0, len = int8.length(); i < len; i++) {
            char c = int8.charAt(i);
            blackhole.consume(c);
        }
    }

    @Benchmark
    public void randomAccessInt9(Blackhole blackhole) {
        for (int i = 0, len = int9.length(); i < len; i++) {
            char c = int9.charAt(i);
            blackhole.consume(c);
        }
    }

    @Benchmark
    public void randomAccessIntAscii(Blackhole blackhole) {
        for (int i = 0, len = intAscii.length(); i < len; i++) {
            char c = intAscii.charAt(i);
            blackhole.consume(c);
        }
    }
}
