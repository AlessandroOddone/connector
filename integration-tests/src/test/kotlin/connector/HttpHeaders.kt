package connector

import connector.http.GET
import connector.http.Header
import connector.http.Headers
import connector.util.assertHttpLogMatches
import connector.util.runHttpTest
import io.ktor.client.utils.buildHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import org.junit.Test

private val BASE_URL = Url("https://headers/")

@Service interface HttpHeadersTestService {
  @GET("get")
  @Headers("header: value")
  suspend fun singleStaticHeader()

  @GET("get")
  @Headers(
    "header1: value1",
    "header2: value2"
  )
  suspend fun multipleStaticHeaders()

  @GET("get")
  @Headers(
    "header1:value1",
    "header2: value2",
    "header3   :    value3",
    "header4 :value4"
  )
  suspend fun multipleStaticHeadersWithDifferentSpacingAroundColon()

  @GET("get")
  suspend fun dynamicStringHeader(
    @Header("header") h: String
  )

  @GET("get")
  suspend fun dynamicNullableAnyHeader(
    @Header("header") h: Any?
  )

  @GET("get")
  suspend fun multipleDynamicHeaders(
    @Header("header1") h1: Any?,
    @Header("header2") h2: Any?,
    @Header("header3") h3: Any?,
  )

  @GET("get")
  @Headers("header:static")
  suspend fun multipleHeadersWithSameName(
    @Header("header") h1: String,
    @Header("header") h2: String,
  )
}

class HttpHeaders {
  @Test fun `@Headers defining a single static header`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.singleStaticHeader()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `@Headers defining multiple static headers`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header2", "value2")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleStaticHeaders()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `@Headers ignores whitespaces around colon`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header2", "value2")
      append("header3", "value3")
      append("header4", "value4")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleStaticHeadersWithDifferentSpacingAroundColon()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `String @Header argument is used as the header value`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicStringHeader(h = "value")
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `'toString' of object @Header argument is used as the header value`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicNullableAnyHeader(
      h = object : Any() {
        override fun toString() = "value"
      }
    )
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `If the @Header argument is null, the header is omitted`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicNullableAnyHeader(h = null)
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `Multiple @Header parameters`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header3", "value3")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleDynamicHeaders(
      h1 = object : Any() {
        override fun toString() = "value1"
      },
      h2 = null,
      h3 = object : Any() {
        override fun toString() = "value3"
      }
    )
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `Multiple @Header parameters with the same name`() = runHttpTest {
    val service = HttpHeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = headersOf(
      "header" to listOf("dynamic1", "dynamic2", "static"),
      "Accept-Charset" to listOf("UTF-8"),
      "Accept" to listOf("*/*")
    )
    service.multipleHeadersWithSameName(h1 = "dynamic1", h2 = "dynamic2")
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }
}
