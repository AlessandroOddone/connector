package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.test.util.assertIs
import dev.aoddon.connector.test.util.assertThrows
import dev.aoddon.connector.util.runHttpTest
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import kotlin.js.JsName
import kotlin.properties.Delegates
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val BASE_URL = Url("https://streaming/")

@Service interface StreamingTestService {
  @GET("get") suspend fun streamingReturningUnit(
    @Streaming consumer: suspend (ByteReadChannel) -> Unit
  )

  @GET("get") suspend fun streamingReturningString(
    @Streaming consumer: suspend (ByteReadChannel) -> String
  ): String

  @GET("get") suspend fun streamingHttpResult(
    @Streaming consumer: suspend (HttpResult<ByteReadChannel>) -> Boolean
  ): Boolean

  @GET("get") suspend fun streamingHttpResponse(
    @Streaming consumer: suspend (HttpResponse<ByteReadChannel>) -> Int
  ): Int

  @GET("get") suspend fun streamingHttpResponseSuccess(
    @Streaming consumer: suspend (HttpResponse.Success<ByteReadChannel>) -> Unit
  )
}

class StreamingTest {
  @JsName("Streaming_lambda_returning_Unit")
  @Test fun `@Streaming lambda returning Unit`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    var channel: ByteReadChannel by Delegates.notNull()
    val result = service.streamingReturningUnit { byteReadChannel ->
      channel = byteReadChannel
      (0 until size).forEach { index ->
        assertEquals(index, byteReadChannel.totalBytesRead.toInt())
        assertEquals(randomBytes[index], byteReadChannel.readByte())
      }
    }

    assertSame(Unit, result)
    assertEquals(size, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
    assertTrue(channel.isClosedForRead)
  }

  @JsName("Streaming_lambda_returning_String")
  @Test fun `@Streaming lambda returning String`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    var channel: ByteReadChannel by Delegates.notNull()
    val result = service.streamingReturningString { byteReadChannel ->
      channel = byteReadChannel
      (0 until size).forEach { index ->
        assertEquals(index, byteReadChannel.totalBytesRead.toInt())
        assertEquals(randomBytes[index], byteReadChannel.readByte())
      }
      "Done!"
    }

    assertEquals("Done!", result)
    assertEquals(size, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
    assertTrue(channel.isClosedForRead)
  }

  @JsName("Streaming_lambda_with_HttpResult_parameter")
  @Test fun `@Streaming lambda with HttpResult parameter`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    var channel: ByteReadChannel by Delegates.notNull()
    val result = service.streamingHttpResult { result ->
      val byteReadChannel = result.successBodyOrThrow().also { channel = it }
      (0 until size).forEach { index ->
        assertEquals(index, byteReadChannel.totalBytesRead.toInt())
        assertEquals(randomBytes[index], byteReadChannel.readByte())
      }
      true
    }

    assertTrue(result)
    assertEquals(size, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
    assertTrue(channel.isClosedForRead)
  }

  @JsName("Streaming_lambda_with_HttpResponse_parameter")
  @Test fun `@Streaming lambda with HttpResponse parameter`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    var channel: ByteReadChannel by Delegates.notNull()
    val result = service.streamingHttpResponse { response ->
      val byteReadChannel = response.successBodyOrThrow().also { channel = it }
      (0 until size).forEach { index ->
        assertEquals(index, byteReadChannel.totalBytesRead.toInt())
        assertEquals(randomBytes[index], byteReadChannel.readByte())
      }
      12345
    }

    assertEquals(12345, result)
    assertEquals(size, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
    assertTrue(channel.isClosedForRead)
  }

  @JsName("Streaming_lambda_with_HttpResponseSuccess_parameter")
  @Test fun `@Streaming lambda with HttpResponseSuccess parameter`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    var channel: ByteReadChannel by Delegates.notNull()
    val result = service.streamingHttpResponseSuccess { response ->
      val byteReadChannel = response.successBodyOrThrow().also { channel = it }
      (0 until size).forEach { index ->
        assertEquals(index, byteReadChannel.totalBytesRead.toInt())
        assertEquals(randomBytes[index], byteReadChannel.readByte())
      }
    }

    assertSame(Unit, result)
    assertEquals(size, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
    assertTrue(channel.isClosedForRead)
  }

  @JsName("Streaming_response_error")
  @Test fun `@Streaming response error`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    httpRequestHandler { respondError(HttpStatusCode.InternalServerError) }

    val responseException1 = assertThrows<HttpResponseException> {
      service.streamingReturningUnit {
      }
    }
    assertEquals(HttpStatusCode.InternalServerError, responseException1.response.status)

    val responseException2 = assertThrows<HttpResponseException> {
      service.streamingHttpResponseSuccess {
      }
    }
    assertEquals(HttpStatusCode.InternalServerError, responseException2.response.status)

    var responseError1: HttpResponse.Error by Delegates.notNull()
    service.streamingHttpResult { result ->
      responseError1 = result as HttpResponse.Error
      true
    }
    assertEquals(HttpStatusCode.InternalServerError, responseError1.status)

    var responseError2: HttpResponse.Error by Delegates.notNull()
    service.streamingHttpResponse { response ->
      responseError2 = response as HttpResponse.Error
      123
    }
    assertEquals(HttpStatusCode.InternalServerError, responseError2.status)
  }

  @JsName("Streaming_failure")
  @Test fun `@Streaming failure`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val exception = IllegalStateException("oops")
    httpRequestHandler { throw exception }

    assertThrows<IllegalStateException>("oops") {
      service.streamingReturningUnit {
      }
    }

    assertThrows<IllegalStateException>("oops") {
      service.streamingHttpResponseSuccess {
      }
    }

    assertThrows<IllegalStateException>("oops") {
      service.streamingHttpResponse { 0 }
    }

    var failure: HttpResult.Failure by Delegates.notNull()
    service.streamingHttpResult { result ->
      failure = result as HttpResult.Failure
      true
    }
    assertIs<IllegalStateException>(failure.exception)
    assertEquals("oops", failure.exception.message)
  }

  @JsName("Streaming_lambda_throws")
  @Test fun `@Streaming lambda throws`() = runHttpTest {
    val service = StreamingTestService(BASE_URL, httpClient)

    val size = 100
    val randomBytes = Random.nextBytes(size)
    httpRequestHandler { respond(randomBytes) }

    val readBytesBeforeError = 14
    var channel: ByteReadChannel by Delegates.notNull()
    assertThrows<IllegalStateException>("oops") {
      service.streamingReturningUnit { byteReadChannel ->
        channel = byteReadChannel
        (0 until readBytesBeforeError).forEach { index ->
          assertEquals(index, byteReadChannel.totalBytesRead.toInt())
          assertEquals(randomBytes[index], byteReadChannel.readByte())
        }
        throw IllegalStateException("oops")
      }
    }

    assertEquals(readBytesBeforeError, channel.totalBytesRead.toInt())
    assertTrue(channel.isClosedForWrite)
  }
}
