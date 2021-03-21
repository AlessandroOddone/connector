package dev.aoddon.connector.util

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.utils.buildHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

internal fun MockRequestHandleScope.respondJson(
  content: String,
  status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData {
  return respondJson(content = content.toByteArray(), status = status)
}

internal fun MockRequestHandleScope.respondJson(
  content: ByteArray,
  status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData {
  return respond(
    content = content,
    status = status,
    headers = buildHeaders {
      append(HttpHeaders.ContentLength, content.size.toString())
      append(HttpHeaders.ContentType, "application/json")
    }
  )
}

internal fun MockRequestHandleScope.respondEmpty(
  status: HttpStatusCode = HttpStatusCode.NoContent
): HttpResponseData {
  return respond(
    content = ByteArray(0),
    status = status,
    headers = buildHeaders {
      append(HttpHeaders.ContentLength, "0")
    }
  )
}
