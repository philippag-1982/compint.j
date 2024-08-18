# compint.j - Composed Integers for Java

This research library provides alternative implementations to `java.math.BigInteger` that outperform it in some scenarios.

## Building with gradle

- `gradle` - compile main and test classes
- `gradle test` - run all the unit tests
- `gradle jmh` - run all JMH benckmarks
- `gradle perf --rerun-tasks` - run all custom benckmarks
- `gradle jar` - create a JAR inside the `build/` folder
- `gradle tasks` - see which tasks are available

## Usage without gradle

- Just reference or copy the classes from the `philippag.lib.common.math.compint` java package.

## Classes overview

### Int9

 `Int9` implements "big integers" using base 1E9. This comes with some nice benefits for to/from `String` construction. 
Multiplication performs comparable to `java.math.BigInteger`, when using 
`parallelMultiplyKaratsuba()` or setting a `setForkJoinPool()` and using the `multiply` convenience instance method.

### IntAscii
 `IntAscii` implements "big integers" using an arbitrary base, the numbers are represented as ASCII/Latin1/whatever byte arrays.
 This makes to/from `String` construction trivial, at the cost of calculation performance.

## Some performance numbers

The following numbers show an excerpt of the `JMH` benchmark with mode `avgt` (lower is better).

### First table: raw multiplication

The first table compares `Simple` (i.e. naive) with `Karatsuba` and `ParallelKaratsuba`
algorithms of `Int9` and compares them to `java.math.BigInteger`'s methods
`multiply` and `parallelMultiply` (added in JDK 19).
Raw multiplication performance of `java.math.BigInteger` is almost attainable, but not quite
for these reasons:
- JDK classes benefit from HotSpot intrinsics
- `java.math.BigInteger`'s base of `2 ^ 32` is more dense (uses all the bits)
- `java.math.BigInteger` uses `Toom-Cook-3` for large numbers (the benchmark includes numbers large enough).
`Int9` "only" uses `Karatsuba`.

| Benchmark                                                   |  Score   | Error     | Units |
| ----------------------------------------------------------- | -------- | --------- | ----- |
| Int9MultiplyBenchmark.multiplySimpleInt9                    | 3376.112 | ±  75.123 | ms/op |
| Int9MultiplyBenchmark.multiplyKaratsubaInt9                 |  390.624 | ±  10.599 | ms/op |
| Int9MultiplyBenchmark.parallelMultiplyKaratsubaInt9         |  100.363 | ±  17.506 | ms/op |
| Int9MultiplyBenchmark.multiplyJdkBigInteger                 |   81.057 | ±   9.000 | ms/op |
| Int9MultiplyBenchmark.parallelMultiplyJdkBigInteger         |   29.351 | ±  16.970 | ms/op |

### Second table: parsing and multipliation

The second table shows the same algorithms, but also measures time to parse Strings,
i.e. it also measures `java.math.BigInteger(String)` constructor and `Int9.fromString` factory method's
execution times.
This now clear demonstrates the superior performance of a decimal-based (`1E9` in case of `Int9`) 
implementation when parsing and constructing strings. No base conversion is needed.

| Benchmark                                                   |  Score   | Error     | Units |
| ----------------------------------------------------------- | -------- | --------- | ----- |
| Int9MultiplyBenchmark.parseAndMultiplySimpleInt9            | 3441.402 | ± 235.381 | ms/op |
| Int9MultiplyBenchmark.parseAndMultiplyKaratsubaInt9         |  403.292 | ±  71.212 | ms/op |
| Int9MultiplyBenchmark.parseAndParallelMultiplyKaratsubaInt9 |  102.599 | ±   5.704 | ms/op |
| Int9MultiplyBenchmark.parseAndMultiplyJdkBigInteger         | 2475.494 | ± 309.174 | ms/op |
| Int9MultiplyBenchmark.parseAndParallelMultiplyJdkBigInteger | 2416.467 | ± 127.734 | ms/op |
