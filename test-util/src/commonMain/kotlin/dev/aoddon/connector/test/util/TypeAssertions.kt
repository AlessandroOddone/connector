package dev.aoddon.connector.test.util

import kotlin.contracts.contract
import kotlin.test.assertTrue

public inline fun <reified T> assertIs(actual: Any?) {
  contract { returns() implies (actual is T) }
  assertTrue(
    actual is T,
    "Expected an instance of '${T::class.simpleName}'. Found: $actual."
  )
}
