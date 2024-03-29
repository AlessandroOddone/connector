package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.test.util.assertArrayEquals
import dev.aoddon.connector.test.util.assertIs
import dev.aoddon.connector.test.util.assertThrows
import dev.aoddon.connector.util.JsonBodySerializer
import dev.aoddon.connector.util.Node
import dev.aoddon.connector.util.Wrapper
import dev.aoddon.connector.util.respondEmpty
import dev.aoddon.connector.util.respondJson
import dev.aoddon.connector.util.runHttpTest
import io.ktor.client.engine.mock.respond
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.JsName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val BASE_URL = Url("https://responseBodies/")

@Service interface ResponseBodiesTestService {
  @GET("getBoolean") suspend fun getBoolean(): Boolean

  @GET("getByte") suspend fun getByte(): Byte

  @GET("getChar") suspend fun getChar(): Char

  @GET("getDouble") suspend fun getDouble(): Double

  @GET("getFloat") suspend fun getFloat(): Float

  @GET("getInt") suspend fun getInt(): Int

  @GET("getLong") suspend fun getLong(): Long

  @GET("getShort") suspend fun getShort(): Short

  @GET("getString") suspend fun getString(): String

  @GET("getBooleanArray") suspend fun getBooleanArray(): BooleanArray

  @GET("getByteArray") suspend fun getByteArray(): ByteArray

  @GET("getCharArray") suspend fun getCharArray(): CharArray

  @GET("getDoubleArray") suspend fun getDoubleArray(): DoubleArray

  @GET("getFloatArray") suspend fun getFloatArray(): FloatArray

  @GET("getIntArray") suspend fun getIntArray(): IntArray

  @GET("getLongArray") suspend fun getLongArray(): LongArray

  @GET("getShortArray") suspend fun getShortArray(): ShortArray

  @GET("getSerializable") suspend fun getSerializable(): Node

  @GET("getSerializableWithGeneric") suspend fun getSerializableWithGeneric(): Wrapper<String>

  @GET("getJsonElement") suspend fun getJsonElement(): JsonElement

  @GET("getArray") suspend fun getArray(): Array<Node>

  @GET("getList") suspend fun getList(): List<Wrapper<String>>

  @GET("getSet") suspend fun getSet(): Set<Int>

  @GET("getMap") suspend fun getMap(): Map<String, Node>

  @GET("getMapEntry") suspend fun getMapEntry(): Map.Entry<String, Boolean>

  @GET("getPair") suspend fun getPair(): Pair<Long, Node>

  @GET("getTriple") suspend fun getTriple(): Triple<String, Wrapper<String>, Double>

  @GET("getNullableString") suspend fun getNullableString(): String?

  @GET("getNullableIntArray") suspend fun getNullableIntArray(): IntArray?

  @GET("getNullableSerializable") suspend fun getNullableSerializable(): Node?

  @GET("getNullableList") suspend fun getNullableList(): List<Wrapper<String>>?

  @GET("getNullableMap") suspend fun getNullableMap(): Map<String, Node>?

  @GET("getStringHttpResult") suspend fun getStringHttpResult(): HttpResult<String>

  @GET("getUnitHttpResult") suspend fun getUnitHttpResult(): HttpResult<Unit>

  @GET("getWildcardHttpResult") suspend fun getWildcardHttpResult(): HttpResult<*>

  @GET("getHttpBodyHttpResult") suspend fun getHttpBodyHttpResult(): HttpResult<HttpBody<Boolean>>

  @GET("getNullableHttpBodyHttpResult") suspend fun getNullableHttpBodyHttpResult(): HttpResult<HttpBody<Boolean>?>

  @GET("getStringHttpResponse") suspend fun getStringHttpResponse(): HttpResponse<String>

  @GET("getUnitHttpResponse") suspend fun getUnitHttpResponse(): HttpResponse<Unit>

  @GET("getWildcardHttpResponse") suspend fun getWildcardHttpResponse(): HttpResponse<*>

  @GET("getHttpBodyHttpResponse") suspend fun getHttpBodyHttpResponse(): HttpResponse<HttpBody<Boolean>>

  @GET("getNullableHttpBodyHttpResponse") suspend fun getNullableHttpBodyHttpResponse(): HttpResponse<HttpBody<Boolean>?>

  @GET("getStringHttpResponseSuccess") suspend fun getStringHttpResponseSuccess(): HttpResponse.Success<String>

  @GET("getUnitHttpResponseSuccess") suspend fun getUnitHttpResponseSuccess(): HttpResponse.Success<Unit>

  @GET("getWildcardHttpResponseSuccess") suspend fun getWildcardHttpResponseSuccess(): HttpResponse.Success<*>

  @GET("getHttpBodyHttpResponseSuccess") suspend fun getHttpBodyHttpResponseSuccess(): HttpResponse.Success<HttpBody<Boolean>>

  @GET("getNullableHttpBodyHttpResponseSuccess") suspend fun getNullableHttpBodyHttpResponseSuccess(): HttpResponse.Success<HttpBody<Boolean>?>

  @GET("getStringHttpBody") suspend fun getStringHttpBody(): HttpBody<String>

  @GET("getStringNullableHttpBody") suspend fun getStringNullableHttpBody(): HttpBody<String>?
}

class ResponseBodiesTest {
  @JsName("Boolean_return_type")
  @Test fun `Boolean return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("true") }
    assertTrue(service.getBoolean())

    httpRequestHandler { respondJson("false") }
    assertFalse(service.getBoolean())
  }

  @JsName("Byte_return_type")
  @Test fun `Byte return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("10") }
    assertEquals(10, service.getByte())
  }

  @JsName("Char_return_type")
  @Test fun `Char return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("\"a\"") }
    assertEquals('a', service.getChar())
  }

  @JsName("Double_return_type")
  @Test fun `Double return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("-10.7") }
    assertEquals(-10.7, service.getDouble())
  }

  @JsName("Float_return_type")
  @Test fun `Float return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("2.4") }
    assertEquals(2.4f, service.getFloat())
  }

  @JsName("Int_return_type")
  @Test fun `Int return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("-10") }
    assertEquals(-10, service.getInt())
  }

  @JsName("Long_return_type")
  @Test fun `Long return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("10") }
    assertEquals(10, service.getLong())
  }

  @JsName("Short_return_type")
  @Test fun `Short return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("0") }
    assertEquals(0, service.getShort())
  }

  @JsName("String_return_type")
  @Test fun `String return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("\"aString\"") }
    assertEquals("aString", service.getString())
  }

  @JsName("BooleanArray_return_type")
  @Test fun `BooleanArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(booleanArrayOf(), service.getBooleanArray())

    httpRequestHandler { respondJson("[true]") }
    assertArrayEquals(booleanArrayOf(true), service.getBooleanArray())

    httpRequestHandler { respondJson("[true,false]") }
    assertArrayEquals(booleanArrayOf(true, false), service.getBooleanArray())
  }

  @JsName("ByteArray_return_type")
  @Test fun `ByteArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(byteArrayOf(), service.getByteArray())

    httpRequestHandler { respondJson("[-10]") }
    assertArrayEquals(byteArrayOf(-10), service.getByteArray())

    httpRequestHandler { respondJson("[10,-11]") }
    assertArrayEquals(byteArrayOf(10, -11), service.getByteArray())
  }

  @JsName("CharArray_return_type")
  @Test fun `CharArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(charArrayOf(), service.getCharArray())

    httpRequestHandler { respondJson("[\"a\"]") }
    assertArrayEquals(charArrayOf('a'), service.getCharArray())

    httpRequestHandler { respondJson("[\"a\",\"b\"]") }
    assertArrayEquals(charArrayOf('a', 'b'), service.getCharArray())
  }

  @JsName("DoubleArray_return_type")
  @Test fun `DoubleArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(doubleArrayOf(), service.getDoubleArray())

    httpRequestHandler { respondJson("[10.7]") }
    assertArrayEquals(doubleArrayOf(10.7), service.getDoubleArray())

    httpRequestHandler { respondJson("[10.7,-10.8]") }
    assertArrayEquals(doubleArrayOf(10.7, -10.8), service.getDoubleArray())
  }

  @JsName("FloatArray_return_type")
  @Test fun `FloatArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(floatArrayOf(), service.getFloatArray())

    httpRequestHandler { respondJson("[-2.4]") }
    assertArrayEquals(floatArrayOf(-2.4f), service.getFloatArray())

    httpRequestHandler { respondJson("[2.4,2.5]") }
    assertArrayEquals(floatArrayOf(2.4f, 2.5f), service.getFloatArray())
  }

  @JsName("IntArray_return_type")
  @Test fun `IntArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(intArrayOf(), service.getIntArray())

    httpRequestHandler { respondJson("[10]") }
    assertArrayEquals(intArrayOf(10), service.getIntArray())

    httpRequestHandler { respondJson("[10,11]") }
    assertArrayEquals(intArrayOf(10, 11), service.getIntArray())
  }

  @JsName("LongArray_return_type")
  @Test fun `LongArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(longArrayOf(), service.getLongArray())

    httpRequestHandler { respondJson("[-10]") }
    assertArrayEquals(longArrayOf(-10), service.getLongArray())

    httpRequestHandler { respondJson("[10,-11]") }
    assertArrayEquals(longArrayOf(10, -11), service.getLongArray())
  }

  @JsName("ShortArray_return_type")
  @Test fun `ShortArray return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(shortArrayOf(), service.getShortArray())

    httpRequestHandler { respondJson("[10]") }
    assertArrayEquals(shortArrayOf(10), service.getShortArray())

    httpRequestHandler { respondJson("[-10,11]") }
    assertArrayEquals(shortArrayOf(-10, 11), service.getShortArray())
  }

  @JsName("Serializable_return_type")
  @Test fun `@Serializable return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler {
      respondJson(
        """
          {"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}
        """.trimIndent()
      )
    }
    assertEquals(
      Node(
        id = "1",
        payload = 100,
        children = listOf(
          Node(id = "2", payload = 200, children = emptyList()),
          Node(id = "3", payload = 300, children = emptyList()),
        )
      ),
      service.getSerializable()
    )
  }

  @JsName("Serializable_with_generic_argument_return_type")
  @Test fun `@Serializable with generic argument return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler {
      respondJson(
        """
          {"value":"aString"}
        """.trimIndent()
      )
    }
    assertEquals(Wrapper("aString"), service.getSerializableWithGeneric())
  }

  @JsName("JsonElement_return_type")
  @Test fun `JsonElement return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("\"text\"") }
    assertEquals(JsonPrimitive("text"), service.getJsonElement())

    httpRequestHandler { respondJson("-1000") }
    assertEquals(JsonPrimitive(-1_000), service.getJsonElement())

    val jsonObject = buildJsonObject {
      put("id", JsonPrimitive("1"))
      put("values", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))
    }

    httpRequestHandler {
      respondJson(
        """
          {"id":"1","values":[1,2]}
        """.trimIndent()
      )
    }
    assertEquals(jsonObject, service.getJsonElement())

    httpRequestHandler {
      respondJson(
        """
          [{"id":"1","values":[1,2]},-9.9]
        """.trimIndent()
      )
    }
    assertEquals(
      JsonArray(listOf(jsonObject, JsonPrimitive(-9.9f))),
      service.getJsonElement()
    )
  }

  @JsName("Array_return_type")
  @Test fun `Array return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertArrayEquals(arrayOf(), service.getArray())

    val fistChild = Node(id = "2", payload = 200, children = emptyList())
    val secondChild = Node(id = "3", payload = 300, children = emptyList())
    val root = Node(id = "1", payload = 100, children = listOf(fistChild, secondChild))

    httpRequestHandler {
      respondJson(
        """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}]
        """.trimIndent()
      )
    }
    assertArrayEquals(arrayOf(root), service.getArray())

    httpRequestHandler {
      respondJson(
        """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]},{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]
        """.trimIndent()
      )
    }
    assertArrayEquals(arrayOf(root, fistChild, secondChild), service.getArray())
  }

  @JsName("List_return_type")
  @Test fun `List return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertEquals(emptyList(), service.getList())

    httpRequestHandler {
      respondJson(
        """
          [{"value":"a"}]
        """.trimIndent()
      )
    }
    assertEquals(listOf(Wrapper("a")), service.getList())

    httpRequestHandler {
      respondJson(
        """
          [{"value":"a"},{"value":"b"},{"value":"c"}]
        """.trimIndent()
      )
    }
    assertEquals(listOf(Wrapper("a"), Wrapper("b"), Wrapper("c")), service.getList())
  }

  @JsName("Set_return_type")
  @Test fun `Set return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("[]") }
    assertEquals(emptySet(), service.getSet())

    httpRequestHandler { respondJson("[-1]") }
    assertEquals(setOf(-1), service.getSet())

    httpRequestHandler { respondJson("[-1,0,1]") }
    assertEquals(setOf(-1, 0, 1), service.getSet())
  }

  @JsName("Map_return_type")
  @Test fun `Map return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("{}") }
    assertEquals(emptyMap(), service.getMap())

    httpRequestHandler {
      respondJson(
        """
          {"id1":{"id":"id1","payload":1,"children":[]}}
        """.trimIndent()
      )
    }
    assertEquals(
      mapOf(
        "id1" to Node(id = "id1", payload = 1, children = emptyList())
      ),
      service.getMap()
    )

    httpRequestHandler {
      respondJson(
        """
          {"id1":{"id":"id1","payload":1,"children":[]},"id2":{"id":"id2","payload":2,"children":[]},"id3":{"id":"id3","payload":3,"children":[]}}
        """.trimIndent()
      )
    }
    assertEquals(
      mapOf(
        "id1" to Node(id = "id1", payload = 1, children = emptyList()),
        "id2" to Node(id = "id2", payload = 2, children = emptyList()),
        "id3" to Node(id = "id3", payload = 3, children = emptyList())
      ),
      service.getMap()
    )
  }

  @JsName("MapEntry_return_type")
  @Test fun `MapEntry return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { respondJson("{\"key\":true}") }

    assertEquals(
      object : Map.Entry<String, Boolean> {
        override val key = "key"
        override val value = true
        override fun equals(other: Any?): Boolean {
          return other is Map.Entry<*, *> && other.key == key && other.value == value
        }
      },
      service.getMapEntry()
    )
  }

  @JsName("Pair_return_type")
  @Test fun `Pair return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler {
      respondJson(
        """
          {"first":1000,"second":{"id":"id","payload":1,"children":[]}}
        """.trimIndent()
      )
    }
    assertEquals(
      1_000L to Node(id = "id", payload = 1, children = emptyList()),
      service.getPair()
    )
  }

  @JsName("Triple_return_type")
  @Test fun `Triple return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler {
      respondJson(
        """
          {"first":"first","second":{"value":"second"},"third":-10.0}
        """.trimIndent()
      )
    }
    assertEquals(Triple("first", Wrapper("second"), -10.0), service.getTriple())
  }

  @JsName("Nullable_return_type")
  @Test fun `Nullable return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("null") }

    assertNull(service.getNullableString())
    assertNull(service.getNullableIntArray())
    assertNull(service.getNullableSerializable())
    assertNull(service.getNullableList())
    assertNull(service.getNullableMap())
  }

  @JsName("Deserialized_body_return_type_throws_on_HTTP_error")
  @Test fun `Deserialized body return type throws on HTTP error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = ByteReadChannel.Empty,
        status = HttpStatusCode.BadRequest,
        headers = headers
      )
    }
    val exception = assertThrows<HttpResponseException> { service.getString() }

    assertEquals(HttpStatusCode.BadRequest, exception.response.status)
    assertEquals(headers, exception.response.headers)
    assertArrayEquals(ByteArray(0), exception.response.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, exception.response.protocol)
    assertEquals(HttpMethod.Get, exception.response.request.method)
    assertEquals("${BASE_URL}getString", exception.response.request.url.toString())
  }

  @JsName("Deserialized_body_return_type_throws_on_failure")
  @Test fun `Deserialized body return type throws on failure`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { throw IOException("oops") }
    assertThrows<IOException>("oops") { service.getString() }
  }

  @JsName("HttpBody_return_type")
  @Test fun `HttpBody return type`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("\"12345\"") }
    assertEquals(HttpBody("12345"), service.getStringHttpBody())

    httpRequestHandler { respondJson("\"12345\"") }
    assertEquals(HttpBody("12345"), service.getStringNullableHttpBody())

    httpRequestHandler { respondJson(ByteArray(0)) }
    assertNull(service.getStringNullableHttpBody())
  }

  @JsName("HttpBody_return_type_throws_on_HTTP_error")
  @Test fun `HttpBody return type throws on HTTP error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = ByteReadChannel.Empty,
        status = HttpStatusCode.BadRequest,
        headers = headers
      )
    }
    val exception = assertThrows<HttpResponseException> { service.getStringHttpBody() }

    assertEquals(HttpStatusCode.BadRequest, exception.response.status)
    assertEquals(headers, exception.response.headers)
    assertArrayEquals(ByteArray(0), exception.response.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, exception.response.protocol)
    assertEquals(HttpMethod.Get, exception.response.request.method)
    assertEquals("${BASE_URL}getStringHttpBody", exception.response.request.url.toString())
  }

  @JsName("HttpBody_return_type_throws_on_failure")
  @Test fun `HttpBody return type throws on failure`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { throw IOException("oops") }
    assertThrows<IOException>("oops") { service.getStringHttpBody() }
  }

  @JsName("HttpResult_return_type_on_success")
  @Test fun `HttpResult return type on success`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, "application/json")
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = "\"12345\"",
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val result: HttpResult<String> = service.getStringHttpResult()

    assertIs<HttpResponse.Success<*>>(result)
    assertEquals(HttpStatusCode.OK, result.status)
    assertEquals(headers, result.headers)
    assertEquals("12345", result.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, result.protocol)
    assertEquals(HttpMethod.Get, result.request.method)
    assertEquals("${BASE_URL}getStringHttpResult", result.request.url.toString())
  }

  @JsName("HttpResult_return_type_on_HTTP_error")
  @Test fun `HttpResult return type on HTTP error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = ByteReadChannel(ByteArray(1) { 0 }),
        status = HttpStatusCode.InternalServerError,
        headers = headers
      )
    }
    val result: HttpResult<String> = service.getStringHttpResult()

    assertIs<HttpResponse.Error>(result)
    assertEquals(HttpStatusCode.InternalServerError, result.status)
    assertEquals(headers, result.headers)
    assertArrayEquals(ByteArray(1) { 0 }, result.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, result.protocol)
    assertEquals(HttpMethod.Get, result.request.method)
    assertEquals("${BASE_URL}getStringHttpResult", result.request.url.toString())
  }

  @JsName("HttpResult_return_type_on_failure")
  @Test fun `HttpResult return type on failure`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { throw IOException("oops") }
    val result: HttpResult<String> = service.getStringHttpResult()

    assertIs<HttpResult.Failure>(result)
    assertIs<IOException>(result.exception)
    assertEquals("oops", result.exception.message)
    assertEquals(HttpMethod.Get, result.request.method)
    assertEquals("${BASE_URL}getStringHttpResult", result.request.url.toString())
  }

  @JsName("HttpResult_with_Unit_type_argument_ignores_success_body")
  @Test fun `HttpResult with Unit type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Image.GIF.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val result: HttpResult<Unit> = service.getUnitHttpResult()

    assertIs<HttpResponse.Success<*>>(result)
    assertSame(Unit, result.body)
  }

  @JsName("HttpResult_with_wildcard_type_argument_ignores_success_body")
  @Test fun `HttpResult with wildcard type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Any.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val result: HttpResult<*> = service.getWildcardHttpResult()

    assertIs<HttpResponse.Success<*>>(result)
    assertSame(Unit, result.body)
  }

  @JsName("HttpResult_with_HttpBody_type_argument")
  @Test fun `HttpResult with HttpBody type argument`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson(content = "true") }

    with(service.getHttpBodyHttpResult()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertEquals(HttpBody(true), body)
    }

    with(service.getNullableHttpBodyHttpResult()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertEquals(HttpBody(true), body)
    }

    httpRequestHandler { respondEmpty() }

    with(service.getNullableHttpBodyHttpResult()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertNull(body)
    }
  }

  @JsName("HttpResponse_return_type_on_success")
  @Test fun `HttpResponse return type on success`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, "application/json")
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = "\"12345\"",
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val response: HttpResponse<String> = service.getStringHttpResponse()

    assertIs<HttpResponse.Success<*>>(response)
    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals(headers, response.headers)
    assertEquals("12345", response.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, response.protocol)
    assertEquals(HttpMethod.Get, response.request.method)
    assertEquals("${BASE_URL}getStringHttpResponse", response.request.url.toString())
  }

  @JsName("HttpResponse_return_type_on_HTTP_error")
  @Test fun `HttpResponse return type on HTTP error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = ByteReadChannel(ByteArray(1) { 1 }),
        status = HttpStatusCode.InternalServerError,
        headers = headers
      )
    }
    val response: HttpResponse<String> = service.getStringHttpResponse()

    assertIs<HttpResponse.Error>(response)
    assertEquals(HttpStatusCode.InternalServerError, response.status)
    assertEquals(headers, response.headers)
    assertArrayEquals(ByteArray(1) { 1 }, response.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, response.protocol)
    assertEquals(HttpMethod.Get, response.request.method)
    assertEquals("${BASE_URL}getStringHttpResponse", response.request.url.toString())
  }

  @JsName("HttpResponse_return_type_throws_on_failure")
  @Test fun `HttpResponse return type throws on failure`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { throw IOException("oops") }
    assertThrows<IOException>("oops") { service.getStringHttpResponse() }
  }

  @JsName("HttpResponse_with_Unit_type_argument_ignores_success_body")
  @Test fun `HttpResponse with Unit type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Image.GIF.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val response: HttpResponse<Unit> = service.getUnitHttpResponse()

    assertIs<HttpResponse.Success<*>>(response)
    assertSame(Unit, response.body)
  }

  @JsName("HttpResponse_with_wildcard_type_argument_ignores_success_body")
  @Test fun `HttpResponse with wildcard type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Any.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val response: HttpResponse<*> = service.getWildcardHttpResponse()

    assertIs<HttpResponse.Success<*>>(response)
    assertSame(Unit, response.body)
  }

  @JsName("HttpResponse_with_HttpBody_type_argument")
  @Test fun `HttpResponse with HttpBody type argument`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson(content = "true") }

    with(service.getHttpBodyHttpResponse()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertEquals(HttpBody(true), body)
    }

    with(service.getNullableHttpBodyHttpResponse()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertEquals(HttpBody(true), body)
    }

    httpRequestHandler { respondEmpty() }

    with(service.getNullableHttpBodyHttpResponse()) {
      assertIs<HttpResponse.Success<*>>(this)
      assertNull(body)
    }
  }

  @JsName("HttpResponseSuccess_return_type_on_success")
  @Test fun `HttpResponseSuccess return type on success`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, "application/json")
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = "\"12345\"",
        status = HttpStatusCode.OK,
        headers = headers
      )
    }
    val success: HttpResponse.Success<String> = service.getStringHttpResponseSuccess()

    assertEquals(HttpStatusCode.OK, success.status)
    assertEquals(headers, success.headers)
    assertEquals("12345", success.body)
    assertEquals(HttpProtocolVersion.HTTP_1_1, success.protocol)
    assertEquals(HttpMethod.Get, success.request.method)
    assertEquals("${BASE_URL}getStringHttpResponseSuccess", success.request.url.toString())
  }

  @JsName("HttpResponseSuccess_return_type_throws_on_HTTP_error")
  @Test fun `HttpResponseSuccess return type throws on HTTP error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append("ExpectedHeaderName", "ExpectedHeaderValue")
    }
    httpRequestHandler {
      respond(
        content = ByteReadChannel.Empty,
        status = HttpStatusCode.InternalServerError,
        headers = headers
      )
    }

    assertThrows<HttpResponseException> { service.getStringHttpResponseSuccess() }
  }

  @JsName("HttpResponseSuccess_return_type_throws_on_failure")
  @Test fun `HttpResponseSuccess return type throws on failure`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    httpRequestHandler { throw IOException("oops") }
    assertThrows<IOException>("oops") { service.getStringHttpResponseSuccess() }
  }

  @JsName("HttpResponseSuccess_with_Unit_type_argument_ignores_success_body")
  @Test fun `HttpResponseSuccess with Unit type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Image.GIF.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }

    assertSame(Unit, service.getUnitHttpResponseSuccess().body)
  }

  @JsName("HttpResponseSuccess_with_wildcard_type_argument_ignores_success_body")
  @Test fun `HttpResponseSuccess with wildcard type argument ignores success body`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    val headers = buildHeaders {
      append(HttpHeaders.ContentType, ContentType.Any.toString())
    }
    httpRequestHandler {
      respond(
        content = Random.nextBytes(10),
        status = HttpStatusCode.OK,
        headers = headers
      )
    }

    assertSame(Unit, service.getWildcardHttpResponseSuccess().body)
  }

  @JsName("HttpResponseSuccess_with_HttpBody_type_argument")
  @Test fun `HttpResponseSuccess with HttpBody type argument`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson(content = "true") }

    assertEquals(HttpBody(true), service.getHttpBodyHttpResponseSuccess().body)
    assertEquals(HttpBody(true), service.getNullableHttpBodyHttpResponseSuccess().body)

    httpRequestHandler { respondEmpty() }

    assertNull(service.getNullableHttpBodyHttpResponseSuccess().body)
  }

  @JsName("No_serializer_for_response_Content_Type_error")
  @Test fun `No serializer for response Content-Type error`() = runHttpTest {
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    // Missing Content-Type response header

    httpRequestHandler { respond("true", headers = Headers.Empty) }

    assertThrows<IllegalStateException>(
      message = "No suitable HttpBodySerializer found for reading Content-Type: 'null'"
    ) {
      service.getString()
    }

    service.getStringHttpResult().let { result ->
      assertIs<HttpResult.Failure>(result)
      assertIs<IllegalStateException>(result.exception)
      assertEquals(
        "No suitable HttpBodySerializer found for reading Content-Type: 'null'",
        result.exception.message
      )
    }

    // Unhandled Content-Type response header

    httpRequestHandler {
      respond(
        "true",
        headers = buildHeaders {
          append(HttpHeaders.ContentType, "image/gif")
        }
      )
    }

    assertThrows<IllegalStateException>(
      message = "No suitable HttpBodySerializer found for reading Content-Type: 'image/gif'"
    ) {
      service.getString()
    }

    service.getStringHttpResult().let { result ->
      assertIs<HttpResult.Failure>(result)
      assertIs<IllegalStateException>(result.exception)
      assertEquals(
        "No suitable HttpBodySerializer found for reading Content-Type: 'image/gif'",
        result.exception.message
      )
    }
  }

  @JsName("Response_body_serialization_error")
  @Test fun `Response body serialization error`() = runHttpTest {
    val throwingSerializer = object : HttpBodySerializer {
      override fun canWrite(contentType: ContentType?) = true
      override fun canRead(contentType: ContentType?) = true

      override fun <T> write(
        serializationStrategy: SerializationStrategy<T>,
        body: T,
        contentType: ContentType?
      ): OutgoingContent = throw SerializationException("oops")

      override suspend fun <T> read(
        deserializationStrategy: DeserializationStrategy<T>,
        body: ByteReadChannel,
        contentType: ContentType?
      ): T = throw SerializationException("oops")
    }
    val service = ResponseBodiesTestService(BASE_URL, httpClient, listOf(throwingSerializer))

    assertThrows<SerializationException>(message = "oops") { service.getString() }

    val result = service.getStringHttpResult()
    assertIs<HttpResult.Failure>(result)
    assertIs<SerializationException>(result.exception)
    assertEquals("oops", result.exception.message)
  }
}
