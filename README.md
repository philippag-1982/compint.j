# compint.j - Composed Integers for Java

This research library provides alternative implementations to `java.math.BigInteger` that outperform it in some scenarios.

## Building

- `gradle` - compile main and test classes
- `gradle test` - run all the unit tests
- `gradle jmh` - run all JMH benckmarks
- `gradle perf --rerun-tasks` - run all custom benckmarks
- `gradle jar` - create a JAR inside the `build/` folder
- `gradle tasks` - see which tasks are available
- `cd src/main/native && make` - build the optional native part (requires GNU make and GCC or Clang)

## Classes overview

### Int9

 `Int9` implements "big integers" using base 1E9. This comes with some nice benefits for to/from `String` construction. 
Multiplication performs comparable to `java.math.BigInteger`, when using 
`parallelMultiplyKaratsuba()` or setting a `setForkJoinPool()` and using the `multiply` convenience instance method.

### Int9N

`Int9N` is an experimental version of `Int9` that attempts to implement hot methods natively (via JNI). At the moment,
only `multiplyCore` has a native implementation. Performance benefit is very slight, and only observed on Linux with GCC.

### IntAscii
 `IntAscii` implements "big integers" using an arbitrary base, the numbers are represented as ASCII/Latin1/whatever byte arrays.
 This makes to/from `String` construction trivial, at the cost of calculation performance.

## Some performance numbers

The following numbers show excerpts of the `JMH` benchmark class `Int9MultiplyBenchmark` with mode `avgt` (lower is better).

### First table: raw multiplication

The first table compares `Simple` (naive "gradle school algorithm") with `Karatsuba` and `ParallelKaratsuba`
algorithms of `Int9` and races them against `java.math.BigInteger`'s methods
`multiply` and `parallelMultiply` (added in JDK 19).

Raw multiplication performance of `java.math.BigInteger` is almost attainable, but not quite,
for these reasons:
- JDK classes benefit from HotSpot intrinsics
- `java.math.BigInteger`'s base of `2 ^ 32` is more dense (uses all the bits)
- `java.math.BigInteger` uses `Toom-Cook-3` for large numbers (the benchmark does include large enough numbers).

| Benchmark                             |  Score   | Error     | Units |
| ------------------------------------- | -------- | --------- | ----- |
| multiplySimpleInt9                    | 3376.112 | ±  75.123 | ms/op |
| multiplyKaratsubaInt9                 |  390.624 | ±  10.599 | ms/op |
| parallelMultiplyKaratsubaInt9         |  100.363 | ±  17.506 | ms/op |
| multiplyJdkBigInteger                 |   81.057 | ±   9.000 | ms/op |
| parallelMultiplyJdkBigInteger         |   29.351 | ±  16.970 | ms/op |

### Second table: parsing and multiplying

The second table shows the same algorithms, but also measures time to parse Strings,
i.e. it also measures `java.math.BigInteger(String)` constructor and `Int9.fromString` factory method's
execution times.

This now clearly demonstrates the superior performance of a decimal-based (`1E9` in case of `Int9`) 
implementation when parsing and constructing strings. They are orders of magnitude faster, since no base conversion is needed.

This benchmark exists because it makes sense to not only look at raw multiplication speed in isolation, but also the speed of
constructing the big-number instances.

| Benchmark                             |  Score   | Error     | Units |
| ------------------------------------- | -------- | --------- | ----- |
| parseAndMultiplySimpleInt9            | 3441.402 | ± 235.381 | ms/op |
| parseAndMultiplyKaratsubaInt9         |  403.292 | ±  71.212 | ms/op |
| parseAndParallelMultiplyKaratsubaInt9 |  102.599 | ±   5.704 | ms/op |
| parseAndMultiplyJdkBigInteger         | 2475.494 | ± 309.174 | ms/op |
| parseAndParallelMultiplyJdkBigInteger | 2416.467 | ± 127.734 | ms/op |

## Use cases

`Int9` is interesting in that it provides random access to decimal digits by implementing the `CharSequence` interface.
It is therefore practically a string at the same time as being a number. This is a big advantage when having to output
big numbers to some method that accepts `CharSequence` - no `toString` materializiation is needed.

More concretely, `Int9` would lend itself to being used in a `REPL` (Read-Evaluate-Print-Loop), 
where to/from `String` conversion is frequent.
