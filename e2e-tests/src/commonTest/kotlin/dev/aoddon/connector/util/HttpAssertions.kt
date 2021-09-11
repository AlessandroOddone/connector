package dev.aoddon.connector.util

import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url

suspend fun HttpTestContext.assertHttpLogMatches(
  logEntryMatcher: HttpLogEntry.MatcherBuilder.() -> Unit
) {
  assertHttpLogMatches(listOf(logEntryMatcher))
}

suspend fun HttpTestContext.assertHttpLogMatches(
  vararg logEntryMatchers: HttpLogEntry.MatcherBuilder.() -> Unit
) {
  assertHttpLogMatches(logEntryMatchers.toList())
}

suspend fun HttpTestContext.assertHttpLogMatches(
  logEntryMatchers: Collection<HttpLogEntry.MatcherBuilder.() -> Unit>
) {
  if (logEntryMatchers.size != httpLog.size) {
    throw AssertionError(
      "Expected ${logEntryMatchers.size} HTTP requests, but ${httpLog.size} were logged."
    )
  }
  val errorsByIndex: List<List<String>> = logEntryMatchers.mapIndexed { index, matcher ->
    httpLog[index].collectErrorMessages(matcher)
  }
  var errorMessage = ""
  errorsByIndex.forEachIndexed { index, errors ->
    if (errors.isNotEmpty()) {
      errorMessage += "\nHTTP log entry #${index + 1} (${httpLog[index]})\n"
      errorMessage += errors.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
    }
  }
  if (errorMessage.isNotEmpty()) {
    throw AssertionError(errorMessage)
  }
}

private suspend fun HttpLogEntry.collectErrorMessages(
  matcher: HttpLogEntry.MatcherBuilder.() -> Unit
): List<String> {
  var expectedUrl: String? = null
  var expectedMethod: HttpMethod? = null
  var expectedRequestHeaders: Headers? = null
  var expectedRequestBytes: ByteArray? = null
  var expectedContentType: ContentType? = null
  matcher(
    object : HttpLogEntry.MatcherBuilder {
      override fun hasUrl(url: String) {
        expectedUrl = url
      }

      override fun hasUrl(url: Url) {
        expectedUrl = url.toString()
      }

      override fun hasMethod(method: HttpMethod) {
        expectedMethod = method
      }

      override fun hasMethod(method: String) {
        expectedMethod = HttpMethod(method)
      }

      override fun hasRequestHeaders(headers: Headers) {
        expectedRequestHeaders = headers
      }

      override fun hasRequestBody(bytes: ByteArray, contentType: ContentType?) {
        expectedRequestBytes = bytes
        expectedContentType = contentType
      }
    }
  )
  val errorMessages = mutableListOf<String>()
  if (expectedUrl != null) {
    val actualUrl = url
    if (actualUrl != expectedUrl) {
      errorMessages.add("Expected URL '$expectedUrl', but was: '$actualUrl'.")
    }
  }
  if (expectedMethod != null) {
    val actualMethod = method
    if (actualMethod != expectedMethod) {
      errorMessages.add("Expected HTTP method '${expectedMethod!!.value}', but was: '${actualMethod.value}'.")
    }
  }
  if (expectedRequestHeaders != null) {
    val actualRequestHeaders = requestHeaders
    if (actualRequestHeaders != expectedRequestHeaders) {
      errorMessages.add(
        "Expected request headers ${expectedRequestHeaders!!.entries()}, " +
          "but were: ${actualRequestHeaders.entries()}."
      )
    }
  }
  if (expectedRequestBytes != null) {
    val actualRequestBytes = requestBody.toByteArray()
    if (!actualRequestBytes.contentEquals(expectedRequestBytes)) {
      errorMessages.add(
        "Expected request body '${expectedRequestBytes!!.decodeToString()}', " +
          "but was '${actualRequestBytes.decodeToString()}'."
      )
    }
    val actualContentType = requestBody.contentType
    if (actualContentType != expectedContentType) {
      errorMessages.add("Expected Content-Type '$expectedContentType', but was '$actualContentType'.")
    }
  }
  return errorMessages
}
