package dev.aoddon.connector.http

public class HttpResponseException(
  public val response: HttpResponse.Error
) : IllegalStateException("HTTP response error: $response")
