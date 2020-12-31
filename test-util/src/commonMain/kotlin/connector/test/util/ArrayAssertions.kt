package connector.test.util

public fun <T> assertArrayEquals(expected: Array<T>, actual: Array<T>) {
  if (!expected.contentDeepEquals(actual)) {
    throw AssertionError(
      "Expected ${expected.contentDeepToString()}, but was: ${actual.contentDeepToString()}."
    )
  }
}

public fun assertArrayEquals(expected: BooleanArray, actual: BooleanArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: CharArray, actual: CharArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: DoubleArray, actual: DoubleArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: FloatArray, actual: FloatArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: IntArray, actual: IntArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: LongArray, actual: LongArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}

public fun assertArrayEquals(expected: ShortArray, actual: ShortArray) {
  assertArrayEquals(expected.toTypedArray(), actual.toTypedArray())
}
