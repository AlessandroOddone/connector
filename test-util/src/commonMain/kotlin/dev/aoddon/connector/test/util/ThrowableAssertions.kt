package dev.aoddon.connector.test.util

public inline fun <reified T : Throwable> assertThrows(message: String? = null, block: () -> Unit): T {
  try {
    block()
    throw AssertionError("Expected ${T::class.simpleName}, but nothing was thrown.")
  } catch (t: Throwable) {
    if (t !is T) {
      throw AssertionError("Expected ${T::class.simpleName}}, but caught: $t.")
    }
    if (message != null && t.message != message) {
      throw AssertionError("Expected error message: '$message'. Found: '${t.message}'.")
    }
    return t
  }
}
