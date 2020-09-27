package connector.util

inline fun <reified T : Throwable> assertThrows(message: String? = null, block: () -> Unit) {
  try {
    block()
    throw AssertionError("Expected ${T::class.simpleName}, but nothing was thrown")
  } catch (t: Throwable) {
    if (t !is T) {
      throw AssertionError("Expected ${T::class.simpleName}, but $t was thrown instead")
    }
    if (message != null && t.message != message) {
      throw AssertionError("Expected error message: '$message'. Found: '${t.message}'")
    }
  }
}
