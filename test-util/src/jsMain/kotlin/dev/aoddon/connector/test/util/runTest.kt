package dev.aoddon.connector.test.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import kotlin.coroutines.EmptyCoroutineContext

public actual fun runTest(block: suspend () -> Unit) {
  coroutineScope.promise { block() }.asDynamic()
}

private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
