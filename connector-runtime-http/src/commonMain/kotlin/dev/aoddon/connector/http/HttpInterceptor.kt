package dev.aoddon.connector.http

import io.ktor.utils.io.ByteReadChannel

public interface HttpInterceptor {
  public suspend fun Context.intercept(): HttpResult<ByteReadChannel>

  public interface Context {
    public val request: HttpRequest
    public suspend fun proceedWith(request: HttpRequest): HttpResult<ByteReadChannel>
  }
}

public suspend inline fun HttpInterceptor.Context.proceed(): HttpResult<ByteReadChannel> {
  return proceedWith(request)
}
