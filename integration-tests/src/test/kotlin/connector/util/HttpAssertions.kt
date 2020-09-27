package connector.util

import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url

fun TestContext.assertHttpLogMatches(
    logEntryMatcher: HttpLogEntry.MatcherBuilder.() -> Unit
) {
    assertHttpLogMatches(listOf(logEntryMatcher))
}

fun TestContext.assertHttpLogMatches(
    vararg logEntryMatchers: HttpLogEntry.MatcherBuilder.() -> Unit
) {
    assertHttpLogMatches(logEntryMatchers.toList())
}

fun TestContext.assertHttpLogMatches(
    logEntryMatchers: Collection<HttpLogEntry.MatcherBuilder.() -> Unit>
) {
    if (logEntryMatchers.size != httpLog.size) {
        throw AssertionError(
            "Expected ${logEntryMatchers.size} HTTP calls, but ${httpLog.size} were logged.\nHTTP log: $httpLog\n"
        )
    }
    val errorsByIndex: List<List<String>> = logEntryMatchers.mapIndexed { index, matcher ->
        httpLog[index].collectErrorMessages(matcher)
    }
    var errorMessage = ""
    errorsByIndex.forEachIndexed { index, errors ->
        if (errors.isNotEmpty()) {
            if (errorMessage.isEmpty()) {
                errorMessage += "HTTP calls with unexpected data were logged.\n"
            }
            errorMessage += "\nAt index $index:\n"
            errorMessage += errors.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
        }
    }
    if (errorMessage.isNotEmpty()) {
        errorMessage += "\nHTTP log: $httpLog\n"
        throw AssertionError(errorMessage)
    }
}

fun HttpLogEntry.assertMatches(matcher: HttpLogEntry.MatcherBuilder.() -> Unit) {
    val errorMessages = collectErrorMessages(matcher)
    if (errorMessages.isNotEmpty()) {
        var errorMessage = "HTTP log entry was not as expected: $this\n"
        errorMessage += errorMessages.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
        throw AssertionError(errorMessage)
    }
}

private fun HttpLogEntry.collectErrorMessages(matcher: HttpLogEntry.MatcherBuilder.() -> Unit): List<String> {
    var expectedUrl: String? = null
    var expectedMethod: HttpMethod? = null
    var expectedRequestHeaders: Headers? = null
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
        }
    )
    val errorMessages = mutableListOf<String>()
    if (expectedUrl != null) {
        val actualUrl = url
        if (actualUrl != expectedUrl) {
            errorMessages.add("Expected URL '$expectedUrl', but was: '$actualUrl'")
        }
    }
    if (expectedMethod != null) {
        val actualMethod = method
        if (actualMethod != expectedMethod) {
            errorMessages.add("Expected HTTP method ${expectedMethod!!.value}, but was: ${actualMethod.value}")
        }
    }
    if (expectedRequestHeaders != null) {
        val actualRequestHeaders = requestHeaders
        if (actualRequestHeaders != expectedRequestHeaders) {
            errorMessages.add("Expected request headers ${expectedRequestHeaders!!.entries()}, but were: ${actualRequestHeaders.entries()}")
        }
    }
    return errorMessages
}
