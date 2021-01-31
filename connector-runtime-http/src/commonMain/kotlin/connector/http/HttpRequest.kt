package connector.http

import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMessage
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent

public interface HttpRequest : HttpMessage {
  public val method: HttpMethod
  public val url: Url
  override val headers: Headers
  public val contentSupplier: suspend () -> OutgoingContent

  public class CopyBuilder internal constructor(private val original: HttpRequest) {
    public var method: HttpMethod = original.method
    public val url: URLBuilder = URLBuilder(original.url)
    public val headers: HeadersBuilder = HeadersBuilder().apply { appendAll(original.headers) }
    public var contentSupplier: suspend () -> OutgoingContent = original.contentSupplier

    internal fun build(): HttpRequest {
      return HttpRequestImpl(
        method = method,
        url = url.build(),
        headers = headers.build(),
        contentSupplier = contentSupplier
      )
    }
  }
}

public fun HttpRequest(
  method: HttpMethod,
  url: Url,
  headers: Headers = Headers.Empty,
  contentSupplier: suspend () -> OutgoingContent = { EmptyContent }
): HttpRequest {
  return HttpRequestImpl(
    method = method,
    url = url,
    headers = headers,
    contentSupplier = contentSupplier
  )
}

public fun HttpRequest.copy(
  block: HttpRequest.CopyBuilder.() -> Unit = {}
): HttpRequest {
  val builder = HttpRequest.CopyBuilder(this)
  block(builder)
  return builder.build()
}

private class HttpRequestImpl(
  override val method: HttpMethod,
  override val url: Url,
  override val headers: Headers,
  override val contentSupplier: suspend () -> OutgoingContent
) : HttpRequest {
  override fun toString(): String {
    return "HttpRequest(${method.value} $url)"
  }
}
