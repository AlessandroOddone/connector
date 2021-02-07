package dev.aoddon.connector.http

import dev.aoddon.connector.test.util.assertArrayEquals
import dev.aoddon.connector.test.util.assertIs
import dev.aoddon.connector.test.util.assertThrows
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HttpResultTests {
  private val request = HttpRequest(HttpMethod.Get, Url("/"))

  @Test fun `Success constructor defaults`() {
    val defaultSuccess = request.success()
    assertNull(defaultSuccess.body)
    assertEquals(HttpStatusCode.OK, defaultSuccess.status)
    assertEquals(headersOf(), defaultSuccess.headers)
    assertEquals(HttpProtocolVersion.HTTP_2_0, defaultSuccess.protocol)
    assertEquals(0L, defaultSuccess.timestamp)
    assertEquals(0L, defaultSuccess.requestTimestamp)
    assertSame(request, defaultSuccess.request)
  }

  @Test fun `simple copy of Success`() {
    val original = request.success(
      status = HttpStatusCode.NotImplemented,
      headers = headersOf(
        "a" to listOf("1", "2"),
        "b" to listOf("3")
      ),
      protocol = HttpProtocolVersion.HTTP_1_1,
      timestamp = 11L,
      requestTimestamp = 10L
    )
    val copy = original.copy()

    assertSame(original.body, copy.body)
    assertEquals(original.status, copy.status)
    assertEquals(original.headers, copy.headers)
    assertEquals(original.protocol, copy.protocol)
    assertEquals(original.timestamp, copy.timestamp)
    assertEquals(original.requestTimestamp, copy.requestTimestamp)
    assertSame(original.request, copy.request)
  }

  @Test fun `copy of Success updating properties`() {
    val original = request.success(body = "body")
    val copy = original.copy {
      status = HttpStatusCode.NotAcceptable
      headers.append("a", "1")
      headers.append("b", "2")
      protocol = HttpProtocolVersion.HTTP_1_0
      timestamp = 11L
      requestTimestamp = 10L
    }

    assertEquals("body", copy.body)
    assertEquals(HttpStatusCode.NotAcceptable, copy.status)
    assertEquals(
      headersOf(
        "a" to listOf("1"),
        "b" to listOf("2")
      ),
      copy.headers
    )
    assertEquals(HttpProtocolVersion.HTTP_1_0, copy.protocol)
    assertEquals(11L, copy.timestamp)
    assertEquals(10L, copy.requestTimestamp)
    assertSame(original.request, copy.request)
  }

  @Test fun `Response Error constructor defaults`() {
    val defaultResponseError = request.responseError()
    assertArrayEquals(ByteArray(0), defaultResponseError.body)
    assertEquals(HttpStatusCode.InternalServerError, defaultResponseError.status)
    assertEquals(headersOf(), defaultResponseError.headers)
    assertEquals(HttpProtocolVersion.HTTP_2_0, defaultResponseError.protocol)
    assertEquals(0L, defaultResponseError.timestamp)
    assertEquals(0L, defaultResponseError.requestTimestamp)
    assertSame(request, defaultResponseError.request)
  }

  @Test fun `simple copy of Response Error`() {
    val original = request.responseError(
      status = HttpStatusCode.BadRequest,
      headers = headersOf(
        "a" to listOf("1", "2"),
        "b" to listOf("3")
      ),
      protocol = HttpProtocolVersion.HTTP_1_1,
      timestamp = 11L,
      requestTimestamp = 10L
    )
    val copy = original.copy()

    assertSame(original.body, copy.body)
    assertEquals(original.status, copy.status)
    assertEquals(original.headers, copy.headers)
    assertEquals(original.protocol, copy.protocol)
    assertEquals(original.timestamp, copy.timestamp)
    assertEquals(original.requestTimestamp, copy.requestTimestamp)
    assertSame(original.request, copy.request)
  }

  @Test fun `copy of Response Error updating properties`() {
    val bytes = Random.nextBytes(10)
    val original = request.responseError(body = bytes)
    val copy = original.copy {
      status = HttpStatusCode.NotAcceptable
      headers.append("a", "1")
      headers.append("b", "2")
      protocol = HttpProtocolVersion.HTTP_1_0
      timestamp = 11L
      requestTimestamp = 10L
    }

    assertArrayEquals(bytes, copy.body)
    assertEquals(HttpStatusCode.NotAcceptable, copy.status)
    assertEquals(
      headersOf(
        "a" to listOf("1"),
        "b" to listOf("2")
      ),
      copy.headers
    )
    assertEquals(HttpProtocolVersion.HTTP_1_0, copy.protocol)
    assertEquals(11L, copy.timestamp)
    assertEquals(10L, copy.requestTimestamp)
    assertSame(original.request, copy.request)
  }

  @Test fun `simple copy of Failure`() {
    val copy = request.failure(IOException("oops")).copy()
    assertIs<IOException>(copy.exception)
    assertEquals("oops", copy.exception.message)
  }

  @Test fun `copy of Failure updating properties`() {
    val original = request.failure(Throwable())
    val copy = original.copy {
      exception = IllegalStateException("illegal")
    }
    assertIs<IllegalStateException>(copy.exception)
    assertEquals("illegal", copy.exception.message)
  }

  @Test fun `HttpResponse toSuccess`() {
    val expectedBody = "Success Body"
    val expectedStatus = HttpStatusCode.BadRequest
    val expectedHeaders = headersOf(
      "a" to listOf("1", "2"),
      "b" to listOf("3")
    )
    val expectedProtocol = HttpProtocolVersion.HTTP_1_1
    val expectedTimestamp = 11L
    val expectedRequestTimestamp = 10L

    val originalSuccess = request.success(
      body = true,
      status = expectedStatus,
      headers = expectedHeaders,
      protocol = expectedProtocol,
      timestamp = expectedTimestamp,
      requestTimestamp = expectedRequestTimestamp
    )
    val originalError = request.responseError(
      body = ByteArray(1) { 1 },
      status = expectedStatus,
      headers = expectedHeaders,
      protocol = expectedProtocol,
      timestamp = expectedTimestamp,
      requestTimestamp = expectedRequestTimestamp
    )

    fun HttpResponse.Success<String>.assertExpected() {
      assertSame(expectedBody, body)
      assertEquals(expectedStatus, status)
      assertEquals(expectedHeaders, headers)
      assertEquals(expectedProtocol, protocol)
      assertEquals(expectedTimestamp, timestamp)
      assertEquals(expectedRequestTimestamp, requestTimestamp)
      assertSame(this@HttpResultTests.request, this.request)
    }

    originalSuccess.toSuccess(expectedBody).assertExpected()
    originalError.toSuccess(expectedBody).assertExpected()
  }

  @Test fun `HttpResponse toError`() {
    val expectedBody = Random.nextBytes(10)
    val expectedStatus = HttpStatusCode.BadRequest
    val expectedHeaders = headersOf(
      "a" to listOf("1", "2"),
      "b" to listOf("3")
    )
    val expectedProtocol = HttpProtocolVersion.HTTP_1_1
    val expectedTimestamp = 11L
    val expectedRequestTimestamp = 10L

    val originalSuccess = request.success(
      body = true,
      status = expectedStatus,
      headers = expectedHeaders,
      protocol = expectedProtocol,
      timestamp = expectedTimestamp,
      requestTimestamp = expectedRequestTimestamp
    )
    val originalError = request.responseError(
      body = ByteArray(1) { 1 },
      status = expectedStatus,
      headers = expectedHeaders,
      protocol = expectedProtocol,
      timestamp = expectedTimestamp,
      requestTimestamp = expectedRequestTimestamp
    )

    fun HttpResponse.Error.assertExpected() {
      assertSame(expectedBody, body)
      assertEquals(expectedStatus, status)
      assertEquals(expectedHeaders, headers)
      assertEquals(expectedProtocol, protocol)
      assertEquals(expectedTimestamp, timestamp)
      assertEquals(expectedRequestTimestamp, requestTimestamp)
      assertSame(this@HttpResultTests.request, this.request)
    }

    originalSuccess.toError(expectedBody).assertExpected()
    originalError.toError(expectedBody).assertExpected()
  }

  @Test fun `HttpResult toFailure`() {
    val success = request.success()
    val responseError = request.responseError()
    val failure = request.failure(Throwable(":("))

    val expectedException = IllegalArgumentException("Something went wrong.")

    fun HttpResult.Failure.assertExpected() {
      assertIs<IllegalArgumentException>(exception)
      assertEquals(expectedException.message, exception.message)
      assertSame(this@HttpResultTests.request, this.request)
    }

    success.toFailure(expectedException).assertExpected()
    responseError.toFailure(expectedException).assertExpected()
    failure.toFailure(expectedException).assertExpected()
  }

  @Test fun `HttpResult responseOrNull`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame(success, success.responseOrNull())
    assertSame(responseError, responseError.responseOrNull())
    assertNull(failure.responseOrNull())
  }

  @Test fun `HttpResult responseOrThrow`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame(success, success.responseOrThrow())
    assertSame(responseError, responseError.responseOrThrow())
    assertThrows<IOException>(":(") { failure.responseOrThrow() }
  }

  @Test fun `HttpResult successOrNull`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame(success, success.successOrNull())
    assertNull(responseError.successOrNull())
    assertNull(failure.successOrNull())
  }

  @Test fun `HttpResult successOrThrow`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame(success, success.successOrThrow())
    val responseException = assertThrows<HttpResponseException> { responseError.successOrThrow() }
    assertSame(responseError, responseException.response)
    assertThrows<IOException>(":(") { failure.successOrThrow() }
  }

  @Test fun `HttpResult successBodyOrNull`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame("body", success.successBodyOrNull())
    assertNull(responseError.successBodyOrNull())
    assertNull(failure.successBodyOrNull())
  }

  @Test fun `HttpResult successBodyOrThrow`() {
    val success = request.success("body")
    val responseError = request.responseError()
    val failure = request.failure(IOException(":("))

    assertSame("body", success.successBodyOrThrow())
    val responseException = assertThrows<HttpResponseException> { responseError.successBodyOrThrow() }
    assertSame(responseError, responseException.response)
    assertThrows<IOException>(":(") { failure.successBodyOrThrow() }
  }
}
