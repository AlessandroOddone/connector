package dev.aoddon.connector.util

import dev.aoddon.connector.test.util.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.features.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel

fun runHttpTest(block: suspend HttpTestContext.() -> Unit) = runTest {
  block(
    object : HttpTestContext {
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
        install(Logging) {
          logger = Logger.SIMPLE
          level = LogLevel.ALL
        }
      }

      override val httpLog: MutableList<HttpLogEntry> = mutableListOf()

      override fun httpRequestHandler(handler: MockRequestHandler) {
        mockHttpRequestHandler = handler
      }
    }
  )
}

interface HttpTestContext {
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
    fun hasRequestBody(bytes: ByteArray, contentType: ContentType?)
  }

  override fun toString(): String = "${method.value} $url"
}

fun HttpLogEntry.MatcherBuilder.hasRequestBody(text: String, contentType: ContentType) {
  return hasRequestBody(text.encodeToByteArray(), contentType)
}

private val defaultMockHttpRequestHandler: MockRequestHandler = { respond(ByteReadChannel.Empty) }
