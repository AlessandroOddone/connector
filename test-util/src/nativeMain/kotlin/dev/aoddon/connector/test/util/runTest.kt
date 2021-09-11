package dev.aoddon.connector.test.util

import kotlinx.coroutines.runBlocking

public actual fun runTest(block: suspend () -> Unit): Unit = runBlocking { block() }
