package connector

import connector.http.HttpInterceptor
import connector.http.HttpResult
import connector.http.JsonBody
import connector.http.POST
import connector.http.copy
import connector.http.proceed
import connector.http.success
import connector.util.JsonBodySerializer
import connector.util.assertHttpLogMatches
import connector.util.hasRequestBody
import connector.util.respondJson
import connector.util.runHttpTest
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
  @POST("post") suspend fun post(@JsonBody request: String): String
}

class HttpInterceptors {
  @Test fun `chain of interceptors`() = runHttpTest {
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
      httpBodySerializers = listOf(JsonBodySerializer),
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
      hasRequestBody(text = "\"abcd\"")
    }
    assertEquals(
      listOf("first proceed", "second proceed", "second result", "first result"),
      events
    )
  }

  @Test fun `mutate request`() = runHttpTest {
    val interceptor = object : HttpInterceptor {
      override suspend fun HttpInterceptor.Context.intercept(): HttpResult<ByteReadChannel> {
        return proceedWith(
          request.copy {
            method = HttpMethod.Put
            url.parameters.append("param", "value")
            bodySupplier = { ByteArrayContent("true".toByteArray()) }
          }
        )
      }
    }

    val service = HttpInterceptorsTestService(
      BASE_URL,
      httpClient,
      httpBodySerializers = listOf(JsonBodySerializer),
      httpInterceptors = listOf(interceptor)
    )

    httpRequestHandler { respondJson("\"1234\"") }

    assertEquals("1234", service.post("abcd"))
    assertHttpLogMatches {
      hasMethod(HttpMethod.Put)
      hasUrl("${BASE_URL}post?param=value")
      hasRequestBody(text = "true")
    }
  }

  @Test fun `return result`() = runHttpTest {
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
      httpBodySerializers = listOf(JsonBodySerializer),
      httpInterceptors = listOf(interceptor)
    )

    httpRequestHandler { throw IOException("network error") }

    assertEquals("1234", service.post("abcd"))
  }
}