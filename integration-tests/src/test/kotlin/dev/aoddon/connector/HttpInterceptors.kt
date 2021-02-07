package dev.aoddon.connector

import dev.aoddon.connector.http.Body
import dev.aoddon.connector.http.HttpInterceptor
import dev.aoddon.connector.http.HttpResult
import dev.aoddon.connector.http.POST
import dev.aoddon.connector.http.copy
import dev.aoddon.connector.http.proceed
import dev.aoddon.connector.http.success
import dev.aoddon.connector.util.JsonContentSerializer
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.hasRequestBody
import dev.aoddon.connector.util.respondJson
import dev.aoddon.connector.util.runHttpTest
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.utils.io.ByteReadChannel
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

private val BASE_URL = Url("https://interceptors/")

@Service interface HttpInterceptorsTestService {
  @POST("post") suspend fun post(@Body("application/json") request: String): String
}

class HttpInterceptors {
  @Test fun `Chain of interceptors`() = runHttpTest {
    val events = mutableListOf<String>()

    fun createInterceptor(tag: String) = object : HttpInterceptor {
      override suspend fun HttpInterceptor.Context.intercept(): HttpResult<ByteReadChannel> {
        events.add("$tag proceed")
        val result = proceed()
        events.add("$tag result")
        return result
      }
    }

    val service = HttpInterceptorsTestService(
      BASE_URL,
      httpClient,
      httpContentSerializers = listOf(JsonContentSerializer),
      httpInterceptors = listOf(
        createInterceptor("first"),
        createInterceptor("second")
      )
    )

    httpRequestHandler { respondJson("\"1234\"") }

    assertEquals("1234", service.post("abcd"))
    assertHttpLogMatches {
      hasMethod(HttpMethod.Post)
      hasUrl("${BASE_URL}post")
      hasRequestBody("\"abcd\"", ContentType.Application.Json)
    }
    assertEquals(
      listOf("first proceed", "second proceed", "second result", "first result"),
      events
    )
  }

  @Test fun `Mutate request`() = runHttpTest {
    val interceptor = object : HttpInterceptor {
      override suspend fun HttpInterceptor.Context.intercept(): HttpResult<ByteReadChannel> {
        return proceedWith(
          request.copy {
            method = HttpMethod.Put
            url.parameters.append("param", "value")
            bodySupplier = { ByteArrayContent("text".toByteArray(), contentType = ContentType.Text.Plain) }
          }
        )
      }
    }

    val service = HttpInterceptorsTestService(
      BASE_URL,
      httpClient,
      httpContentSerializers = listOf(JsonContentSerializer),
      httpInterceptors = listOf(interceptor)
    )

    httpRequestHandler { respondJson("\"1234\"") }

    assertEquals("1234", service.post("abcd"))
    assertHttpLogMatches {
      hasMethod(HttpMethod.Put)
      hasUrl("${BASE_URL}post?param=value")
      hasRequestBody("text", ContentType.Text.Plain)
    }
  }

  @Test fun `Return result`() = runHttpTest {
    val interceptor = object : HttpInterceptor {
      override suspend fun HttpInterceptor.Context.intercept(): HttpResult<ByteReadChannel> {
        return request.success(
          ByteReadChannel("\"1234\""),
          status = HttpStatusCode.NotImplemented,
          headers = buildHeaders {
            append(HttpHeaders.ContentType, "application/json")
          }
        )
      }
    }

    val service = HttpInterceptorsTestService(
      BASE_URL,
      httpClient,
      httpContentSerializers = listOf(JsonContentSerializer),
      httpInterceptors = listOf(interceptor)
    )

    httpRequestHandler { throw IOException("network error") }

    assertEquals("1234", service.post("abcd"))
  }
}
