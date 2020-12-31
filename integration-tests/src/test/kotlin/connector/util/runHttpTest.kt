package connector.util

import connector.test.util.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel

fun runHttpTest(block: suspend TestContext.() -> Unit) = runTest {
  block(
    object : TestContext {
      private var mockHttpRequestHandler = defaultMockHttpRequestHandler

      override val httpClient = HttpClient(MockEngine) {
        engine {
          addHandler { request ->
            mockHttpRequestHandler(request).also {
              httpLog.add(
                HttpLogEntry(
                  url = request.url.toString(),
                  method = request.method,
                  requestHeaders = request.headers,
                  requestBody = request.body
                )
              )
            }
          }
        }
      }

      override val httpLog: MutableList<HttpLogEntry> = mutableListOf()

      override fun httpRequestHandler(handler: MockRequestHandler) {
        mockHttpRequestHandler = handler
      }
    }
  )
}

interface TestContext {
  val httpClient: HttpClient
  val httpLog: List<HttpLogEntry>
  fun httpRequestHandler(handler: MockRequestHandler)
}

data class HttpLogEntry(
  val url: String,
  val method: HttpMethod,
  val requestHeaders: Headers,
  val requestBody: OutgoingContent
) {
  interface MatcherBuilder {
    fun hasUrl(url: String)
    fun hasUrl(url: Url)
    fun hasMethod(method: HttpMethod)
    fun hasMethod(method: String)
    fun hasRequestHeaders(headers: Headers)
    fun hasRequestBody(bytes: ByteArray)
  }

  override fun toString(): String = "${method.value} $url"
}

fun HttpLogEntry.MatcherBuilder.hasRequestBody(text: String) = hasRequestBody(text.encodeToByteArray())

private val defaultMockHttpRequestHandler: MockRequestHandler = { respond(ByteReadChannel.Empty) }
