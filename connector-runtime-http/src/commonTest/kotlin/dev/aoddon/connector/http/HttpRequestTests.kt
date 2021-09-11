package dev.aoddon.connector.http

import dev.aoddon.connector.test.util.assertArrayEquals
import dev.aoddon.connector.test.util.runTest
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.utils.EmptyContent
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import kotlin.js.JsName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpRequestTests {
  @JsName("constructor_defaults")
  @Test fun `constructor defaults`() = runTest {
    val request = HttpRequest(
      method = HttpMethod.Get,
      url = Url("/")
    )
    assertEquals(headersOf(), request.headers)
    assertEquals(EmptyContent, request.bodySupplier())
  }

  @JsName("simple_copy")
  @Test fun `simple copy`() = runTest {
    val expectedBytes = Random.nextBytes(10)
    val original = HttpRequest(
      method = HttpMethod.Post,
      url = Url("/abc/def?q=3"),
      headers = headersOf(
        "a" to listOf("1", "2", "3"),
        "b" to listOf("4")
      ),
      bodySupplier = { ByteArrayContent(expectedBytes) }
    )
    val copy = original.copy()

    assertEquals(original.method, copy.method)
    assertEquals(original.url, copy.url)
    assertEquals(original.headers, copy.headers)
    val actualBytes = copy.bodySupplier().toByteArray()
    assertArrayEquals(expectedBytes, actualBytes)
  }

  @JsName("copy_updating_properties")
  @Test fun `copy updating properties`() = runTest {
    val original = HttpRequest(
      method = HttpMethod.Get,
      url = Url("/"),
      headers = Headers.Empty,
      bodySupplier = { EmptyContent }
    )
    val expectedBytes = Random.nextBytes(10)
    val copy = original.copy {
      method = HttpMethod.Head
      url.takeFrom(Url("/abc"))
      headers.append("a", "1")
      headers.append("b", "2")
      bodySupplier = { ByteArrayContent(expectedBytes) }
    }

    assertEquals(HttpMethod.Head, copy.method)
    assertEquals(Url("/abc"), copy.url)
    assertEquals(
      headersOf(
        "a" to listOf("1"),
        "b" to listOf("2")
      ),
      copy.headers
    )
    val actualBytes = copy.bodySupplier().toByteArray()
    assertArrayEquals(expectedBytes, actualBytes)
  }
}
