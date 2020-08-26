package connector.http

import connector.Service
import connector.util.assertHttpLogMatches
import connector.util.runTest
import io.ktor.client.utils.buildHeaders
import io.ktor.http.Url
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
        @Header("header") header: String
    )

    @GET("get")
    suspend fun dynamicNullableAnyHeader(
        @Header("header") header: Any?
    )

    @GET("get")
    suspend fun multipleDynamicHeaders(
        @Header("header1") header1: Any?,
        @Header("header2") header2: Any?,
        @Header("header3") header3: Any?,
    )
}

class HttpHeaders {
    @Test fun `@Headers defining a single static header`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("header", "value")
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.singleStaticHeader()
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }

    @Test fun `@Headers defining multiple static headers`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("header1", "value1")
            append("header2", "value2")
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.multipleStaticHeaders()
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }

    @Test fun `@Headers ignores whitespaces around colon`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
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

    @Test fun `String @Header argument is used as the header value`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("header", "value")
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.dynamicStringHeader(header = "value")
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }

    @Test fun `'toString' of object @Header argument is used as the header value`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("header", "value")
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.dynamicNullableAnyHeader(
            header = object : Any() {
                override fun toString() = "value"
            }
        )
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }

    @Test fun `If the @Header argument is null, the header is omitted`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.dynamicNullableAnyHeader(header = null)
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }

    @Test fun `Multiple @Header parameters`() = runTest {
        val service = HttpHeadersTestService(BASE_URL, httpClient, emptyList())
        val expectedHeaders = buildHeaders {
            append("header1", "value1")
            append("header3", "value3")
            append("Accept-Charset", "UTF-8")
            append("Accept", "*/*")
        }
        service.multipleDynamicHeaders(
            header1 = object : Any() {
                override fun toString() = "value1"
            },
            header2 = null,
            header3 = object : Any() {
                override fun toString() = "value3"
            }
        )
        assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
    }
}
