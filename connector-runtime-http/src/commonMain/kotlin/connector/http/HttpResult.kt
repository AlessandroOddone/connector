package connector.http

import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMessage
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import kotlin.contracts.contract

public sealed class HttpResult<out SuccessBodyT> {
  public abstract val request: HttpRequest

  public class Failure internal constructor(
    public val exception: Throwable,
    override val request: HttpRequest,
  ) : HttpResult<Nothing>() {
    override fun toString(): String {
      return "HttpResult.Failure(${request.method.value} ${request.url} -> $exception)"
    }

    public class CopyBuilder internal constructor(private val original: Failure) {
      public var exception: Throwable = original.exception

      internal fun build(): Failure = original.request.failure(exception)
    }
  }
}

public sealed class HttpResponse<out SuccessBodyT> : HttpResult<SuccessBodyT>(), HttpMessage {
  public abstract val status: HttpStatusCode
  abstract override val headers: Headers
  public abstract val protocol: HttpProtocolVersion
  public abstract val timestamp: Long
  public abstract val requestTimestamp: Long

  public class Success<out SuccessBodyT> internal constructor(
    override val status: HttpStatusCode,
    override val headers: Headers,
    public val body: SuccessBodyT,
    override val protocol: HttpProtocolVersion,
    override val timestamp: Long,
    override val request: HttpRequest,
    override val requestTimestamp: Long
  ) : HttpResponse<SuccessBodyT>() {
    override fun toString(): String {
      return "HttpResponse.Success(${request.method.value} ${request.url} -> $status)"
    }
  }

  public class Error internal constructor(
    override val status: HttpStatusCode,
    override val headers: Headers,
    public val body: ByteArray,
    override val protocol: HttpProtocolVersion,
    override val timestamp: Long,
    override val request: HttpRequest,
    override val requestTimestamp: Long
  ) : HttpResponse<Nothing>() {
    override fun toString(): String {
      return "HttpResponse.Error(${request.method.value} ${request.url} -> $status)"
    }
  }

  public class CopyBuilder<ResponseT : HttpResponse<*>> internal constructor(
    private val original: ResponseT
  ) {
    public var status: HttpStatusCode = original.status
    public val headers: HeadersBuilder = HeadersBuilder().apply { appendAll(original.headers) }
    public var protocol: HttpProtocolVersion = original.protocol
    public var timestamp: Long = original.timestamp
    public var requestTimestamp: Long = original.requestTimestamp

    internal fun build(): ResponseT {
      @Suppress("UNCHECKED_CAST")
      return when (val response = original as HttpResponse<*>) {
        is Success<*> -> original.request.success(
          status = status,
          headers = headers.build(),
          body = response.body,
          protocol = protocol,
          timestamp = timestamp,
          requestTimestamp = requestTimestamp
        )
        is Error -> original.request.responseError(
          status = status,
          headers = headers.build(),
          body = response.body,
          protocol = protocol,
          timestamp = timestamp,
          requestTimestamp = requestTimestamp
        )
      } as ResponseT
    }
  }
}

public fun <SuccessBodyT> HttpRequest.success(
  body: SuccessBodyT,
  status: HttpStatusCode = HttpStatusCode.OK,
  headers: Headers = Headers.Empty,
  protocol: HttpProtocolVersion = HttpProtocolVersion.HTTP_2_0,
  timestamp: Long = 0L,
  requestTimestamp: Long = 0L
): HttpResponse.Success<SuccessBodyT> {
  return HttpResponse.Success(
    status = status,
    headers = headers,
    body = body,
    protocol = protocol,
    timestamp = timestamp,
    request = this,
    requestTimestamp = requestTimestamp
  )
}

public fun HttpRequest.success(
  status: HttpStatusCode = HttpStatusCode.OK,
  headers: Headers = Headers.Empty,
  protocol: HttpProtocolVersion = HttpProtocolVersion.HTTP_2_0,
  timestamp: Long = 0L,
  requestTimestamp: Long = 0L
): HttpResponse.Success<HttpBody<Nothing>?> {
  return success(
    body = null,
    status = status,
    headers = headers,
    protocol = protocol,
    timestamp = timestamp,
    requestTimestamp = requestTimestamp
  )
}

public fun HttpRequest.responseError(
  status: HttpStatusCode = HttpStatusCode.InternalServerError,
  headers: Headers = Headers.Empty,
  body: ByteArray = EMPTY_BYTE_ARRAY,
  protocol: HttpProtocolVersion = HttpProtocolVersion.HTTP_2_0,
  timestamp: Long = 0L,
  requestTimestamp: Long = 0L
): HttpResponse.Error {
  return HttpResponse.Error(
    status = status,
    headers = headers,
    body = body,
    protocol = protocol,
    timestamp = timestamp,
    request = this,
    requestTimestamp = requestTimestamp
  )
}

public fun HttpRequest.failure(exception: Throwable): HttpResult.Failure {
  return HttpResult.Failure(exception, this)
}

public fun <ResponseT : HttpResponse<*>> ResponseT.copy(
  block: HttpResponse.CopyBuilder<ResponseT>.() -> Unit = {}
): ResponseT {
  val builder = HttpResponse.CopyBuilder(this)
  block(builder)
  return builder.build()
}

public fun HttpResult.Failure.copy(
  block: HttpResult.Failure.CopyBuilder.() -> Unit = {}
): HttpResult.Failure {
  val builder = HttpResult.Failure.CopyBuilder(this)
  block(builder)
  return builder.build()
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResponse<*>.toSuccess(
  body: SuccessBodyT
): HttpResponse.Success<SuccessBodyT> {
  return request.success(
    status = status,
    headers = headers,
    body = body,
    protocol = protocol,
    timestamp = timestamp,
    requestTimestamp = requestTimestamp
  )
}

@Suppress("NOTHING_TO_INLINE")
public inline fun HttpResponse<*>.toError(
  body: ByteArray = EMPTY_BYTE_ARRAY
): HttpResponse.Error {
  return request.responseError(
    status = status,
    headers = headers,
    body = body,
    protocol = protocol,
    timestamp = timestamp,
    requestTimestamp = requestTimestamp
  )
}

@Suppress("NOTHING_TO_INLINE")
public inline fun HttpResult<*>.toFailure(exception: Throwable): HttpResult.Failure {
  return request.failure(exception)
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.responseOrNull(): HttpResponse<SuccessBodyT>? {
  contract {
    returnsNotNull() implies (this@responseOrNull is HttpResponse<SuccessBodyT>)
    returns(null) implies (this@responseOrNull is HttpResult.Failure)
  }
  return when (this) {
    is HttpResponse -> this
    is HttpResult.Failure -> null
  }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.responseOrThrow(): HttpResponse<SuccessBodyT> {
  return when (this) {
    is HttpResponse -> this
    is HttpResult.Failure -> throw exception
  }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.successOrNull(): HttpResponse.Success<SuccessBodyT>? {
  contract {
    returnsNotNull() implies (this@successOrNull is HttpResponse.Success<SuccessBodyT>)
    returns(null) implies (this@successOrNull !is HttpResponse.Success)
  }
  return when (this) {
    is HttpResponse.Success -> this
    else -> null
  }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.successOrThrow(): HttpResponse.Success<SuccessBodyT> {
  return when (this) {
    is HttpResponse.Success -> this
    is HttpResponse.Error -> throw HttpResponseException(this)
    is HttpResult.Failure -> throw exception
  }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.successBodyOrNull(): SuccessBodyT? {
  contract {
    returnsNotNull() implies (this@successBodyOrNull is HttpResponse.Success<SuccessBodyT>)
    returns(null) implies (this@successBodyOrNull !is HttpResponse.Success)
  }
  return when (this) {
    is HttpResponse.Success -> body
    else -> null
  }
}

@Suppress("NOTHING_TO_INLINE")
public inline fun <SuccessBodyT> HttpResult<SuccessBodyT>.successBodyOrThrow(): SuccessBodyT {
  return when (this) {
    is HttpResponse.Success -> body
    is HttpResponse.Error -> throw HttpResponseException(this)
    is HttpResult.Failure -> throw exception
  }
}

@PublishedApi internal val EMPTY_BYTE_ARRAY: ByteArray = ByteArray(0)
