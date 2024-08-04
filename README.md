# compint.j - Composed Integers for Java

This research library provides alternative implementations to `java.math.BigInteger` that outperform it in some scenarios.

## Building with gradle

- `gradle` - compile main and test classes
- `gradle test` - run all the unit tests
- `gradle jar` - create a JAR inside the `build/` folder
- `gradle jmh` - run all JMH benckmarks
- `gradle tasks` - see which tasks are available

## Usage without gradle

- Just reference or copy the classes from the `philippag.lib.common.math.compint` java package.

## Classes overview

### `Int9`

 `Int9` implements "big integers" using base 1E9. This comes with some nice benefits for to/from `String` construction. 
Multiplication performs comparable to `java.math.BigInteger`, when using 
`parallelMultiplyKaratsuba()` or setting a `setForkJoinPool()` and using the `multiply` convenience instance method.

### `IntAscii`
 `IntAscii` implements "big integers" using an arbitrary base, the numbers are represented as ASCII/Latin1/whatever byte arrays.
 This makes to/from `String` construction trivial, at the cost of calculation performance.
