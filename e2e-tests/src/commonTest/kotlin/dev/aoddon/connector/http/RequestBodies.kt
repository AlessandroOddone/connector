package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.test.util.assertIs
import dev.aoddon.connector.test.util.assertThrows
import dev.aoddon.connector.util.HttpLogEntry
import dev.aoddon.connector.util.JsonBodySerializer
import dev.aoddon.connector.util.Node
import dev.aoddon.connector.util.Wrapper
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.hasRequestBody
import dev.aoddon.connector.util.runHttpTest
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals

private val BASE_URL = Url("https://requestBodies/")
private const val JSON = "application/json"

@Service interface RequestBodiesTestService {
  @POST("postBoolean") suspend fun postBoolean(@Body(JSON) body: Boolean)

  @POST("postByte") suspend fun postByte(@Body(JSON) body: Byte)

  @POST("postChar") suspend fun postChar(@Body(JSON) body: Char)

  @POST("postDouble") suspend fun postDouble(@Body(JSON) body: Double)

  @POST("postFloat") suspend fun postFloat(@Body(JSON) body: Float)

  @POST("postInt") suspend fun postInt(@Body(JSON) body: Int)

  @POST("postLong") suspend fun postLong(@Body(JSON) body: Long)

  @POST("postShort") suspend fun postShort(@Body(JSON) body: Short)

  @POST("postString") suspend fun postString(@Body(JSON) body: String)

  @POST("postString") suspend fun postStringResult(@Body(JSON) body: String): HttpResult<*>

  @POST("postBooleanArray") suspend fun postBooleanArray(@Body(JSON) body: BooleanArray)

  @POST("postByteArray") suspend fun postByteArray(@Body(JSON) body: ByteArray)

  @POST("postCharArray") suspend fun postCharArray(@Body(JSON) body: CharArray)

  @POST("postDoubleArray") suspend fun postDoubleArray(@Body(JSON) body: DoubleArray)

  @POST("postFloatArray") suspend fun postFloatArray(@Body(JSON) body: FloatArray)

  @POST("postIntArray") suspend fun postIntArray(@Body(JSON) body: IntArray)

  @POST("postLongArray") suspend fun postLongArray(@Body(JSON) body: LongArray)

  @POST("postShortArray") suspend fun postShortArray(@Body(JSON) body: ShortArray)

  @POST("postSerializable") suspend fun postSerializable(@Body(JSON) body: Node)

  @POST("postSerializableWithGeneric") suspend fun postSerializableWithGeneric(@Body(JSON) body: Wrapper<String>)

  @POST("postJsonElement") suspend fun postJsonElement(@Body(JSON) body: JsonElement)

  @POST("postArray") suspend fun postArray(@Body(JSON) body: Array<Node>)

  @POST("postList") suspend fun postList(@Body(JSON) body: List<Wrapper<String>>)

  @POST("postSet") suspend fun postSet(@Body(JSON) body: Set<Int>)

  @POST("postMap") suspend fun postMap(@Body(JSON) body: Map<String, Node>)

  @POST("postMapEntry") suspend fun postMapEntry(@Body(JSON) body: Map.Entry<String, Boolean>)

  @POST("postPair") suspend fun postPair(@Body(JSON) body: Pair<Long, Node>)

  @POST("postTriple") suspend fun postTriple(@Body(JSON) body: Triple<String, Wrapper<String>, Double>)

  @POST("postNullableString") suspend fun postNullableString(@Body(JSON) body: String?)

  @POST("postNullableIntArray") suspend fun postNullableIntArray(@Body(JSON) body: IntArray?)

  @POST("postNullableSerializable") suspend fun postNullableSerializable(@Body(JSON) body: Node?)

  @POST("postNullableList") suspend fun postNullableList(@Body(JSON) body: List<Wrapper<String>>?)

  @POST("postNullableMap") suspend fun postNullableMap(@Body(JSON) body: Map<String, Node>?)

  @POST("postGif") suspend fun postGif(@Body("image/gif") body: String)

  @POST("postGif") suspend fun postGifResult(@Body("image/gif") body: String): HttpResult<*>

  @POST("postContentTypeWithCharset") suspend fun postContentTypeWithCharset(
    @Body("$JSON; charset=UTF-8") body: String
  )

  @POST("postHttpBody") suspend fun postHttpBody(@Body(JSON) body: HttpBody<String>)

  @POST("postNullableHttpBody") suspend fun postNullableHttpBody(@Body(JSON) body: HttpBody<String>?)

  @POST("postBodyWithoutContentType") suspend fun postBodyWithoutContentType(@Body body: String)

  @POST("postBodyWithEmptyContentType")
  suspend fun postBodyWithEmptyContentType(@Body(contentType = "") body: String)
}

class RequestBodiesTest {
  @JsName("Boolean_Body")
  @Test fun `Boolean @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postBoolean(true)
    service.postBoolean(false)
    assertHttpLogMatches(
      { hasJsonRequestBody("true") },
      { hasJsonRequestBody("false") }
    )
  }

  @JsName("Byte_Body")
  @Test fun `Byte @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postByte(10)
    assertHttpLogMatches { hasJsonRequestBody("10") }
  }

  @JsName("Char_Body")
  @Test fun `Char @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postChar('a')
    assertHttpLogMatches { hasJsonRequestBody("\"a\"") }
  }

  @JsName("Double_Body")
  @Test fun `Double @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postDouble(-10.7)
    assertHttpLogMatches { hasJsonRequestBody("-10.7") }
  }

  @JsName("Float_Body")
  @Test fun `Float @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postFloat(2.4f)
    assertHttpLogMatches { hasJsonRequestBody("2.4") }
  }

  @JsName("Int_Body")
  @Test fun `Int @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postInt(-10)
    assertHttpLogMatches { hasJsonRequestBody("-10") }
  }

  @JsName("Long_Body")
  @Test fun `Long @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postLong(10)
    assertHttpLogMatches { hasJsonRequestBody("10") }
  }

  @JsName("Short_Body")
  @Test fun `Short @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postShort(0)
    assertHttpLogMatches { hasJsonRequestBody("0") }
  }

  @JsName("String_Body")
  @Test fun `String @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postString("aString")
    assertHttpLogMatches { hasJsonRequestBody("\"aString\"") }
  }

  @JsName("BooleanArray_Body")
  @Test fun `BooleanArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postBooleanArray(booleanArrayOf())
    service.postBooleanArray(booleanArrayOf(true))
    service.postBooleanArray(booleanArrayOf(true, false))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[true]") },
      { hasJsonRequestBody("[true,false]") }
    )
  }

  @JsName("ByteArray_Body")
  @Test fun `ByteArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postByteArray(byteArrayOf())
    service.postByteArray(byteArrayOf(-10))
    service.postByteArray(byteArrayOf(10, -11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-10]") },
      { hasJsonRequestBody("[10,-11]") }
    )
  }

  @JsName("CharArray_Body")
  @Test fun `CharArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postCharArray(charArrayOf())
    service.postCharArray(charArrayOf('a'))
    service.postCharArray(charArrayOf('a', 'b'))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[\"a\"]") },
      { hasJsonRequestBody("[\"a\",\"b\"]") }
    )
  }

  @JsName("DoubleArray_Body")
  @Test fun `DoubleArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postDoubleArray(doubleArrayOf())
    service.postDoubleArray(doubleArrayOf(10.7))
    service.postDoubleArray(doubleArrayOf(10.7, -10.8))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10.7]") },
      { hasJsonRequestBody("[10.7,-10.8]") }
    )
  }

  @JsName("FloatArray_Body")
  @Test fun `FloatArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postFloatArray(floatArrayOf())
    service.postFloatArray(floatArrayOf(-2.4f))
    service.postFloatArray(floatArrayOf(2.4f, 2.5f))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-2.4]") },
      { hasJsonRequestBody("[2.4,2.5]") }
    )
  }

  @JsName("IntArray_Body")
  @Test fun `IntArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postIntArray(intArrayOf())
    service.postIntArray(intArrayOf(10))
    service.postIntArray(intArrayOf(10, 11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10]") },
      { hasJsonRequestBody("[10,11]") }
    )
  }

  @JsName("LongArray_Body")
  @Test fun `LongArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postLongArray(longArrayOf())
    service.postLongArray(longArrayOf(-10))
    service.postLongArray(longArrayOf(10, -11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-10]") },
      { hasJsonRequestBody("[10,-11]") }
    )
  }

  @JsName("ShortArray_Body")
  @Test fun `ShortArray @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postShortArray(shortArrayOf())
    service.postShortArray(shortArrayOf(10))
    service.postShortArray(shortArrayOf(-10, 11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10]") },
      { hasJsonRequestBody("[-10,11]") }
    )
  }

  @JsName("Serializable_Body")
  @Test fun `@Serializable @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    val root = Node(
      id = "1",
      payload = 100,
      children = listOf(
        Node(id = "2", payload = 200, children = emptyList()),
        Node(id = "3", payload = 300, children = emptyList()),
      )
    )
    service.postSerializable(root)
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}
        """.trimIndent()
      )
    }
  }

  @JsName("Serializable_with_generic_argument_Body")
  @Test fun `@Serializable with generic argument @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postSerializableWithGeneric(Wrapper("aString"))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"value":"aString"}
        """.trimIndent()
      )
    }
  }

  @JsName("JsonElement_Body")
  @Test fun `JsonElement @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    val jsonObject = buildJsonObject {
      put("id", JsonPrimitive("1"))
      put("values", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))
    }

    service.postJsonElement(JsonPrimitive("text"))
    service.postJsonElement(JsonPrimitive(-1_000))
    service.postJsonElement(jsonObject)
    service.postJsonElement(JsonArray(listOf(jsonObject, JsonPrimitive(-9.9f))))

    assertHttpLogMatches(
      { hasJsonRequestBody("\"text\"") },
      { hasJsonRequestBody("-1000") },
      {
        hasJsonRequestBody(
          """
          {"id":"1","values":[1,2]}
          """.trimIndent()
        )
      },
      {
        hasJsonRequestBody(
          """
          [{"id":"1","values":[1,2]},-9.9]
          """.trimIndent()
        )
      },
    )
  }

  @JsName("Array_Body")
  @Test fun `Array @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    val fistChild = Node(id = "2", payload = 200, children = emptyList())
    val secondChild = Node(id = "3", payload = 300, children = emptyList())
    val root = Node(id = "1", payload = 100, children = listOf(fistChild, secondChild))

    service.postArray(arrayOf())
    service.postArray(arrayOf(root))
    service.postArray(arrayOf(root, fistChild, secondChild))

    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      {
        hasJsonRequestBody(
          """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}]
          """.trimIndent()
        )
      },
      {
        hasJsonRequestBody(
          """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]},{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]
          """.trimIndent()
        )
      },
    )
  }

  @JsName("List_Body")
  @Test fun `List @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postList(emptyList())
    service.postList(listOf(Wrapper("a")))
    service.postList(listOf(Wrapper("a"), Wrapper("b"), Wrapper("c")))

    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      {
        hasJsonRequestBody(
          """
            [{"value":"a"}]
          """.trimIndent()
        )
      },
      {
        hasJsonRequestBody(
          """
            [{"value":"a"},{"value":"b"},{"value":"c"}]
          """.trimIndent()
        )
      },
    )
  }

  @JsName("Set_Body")
  @Test fun `Set @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postSet(emptySet())
    service.postSet(setOf(-1))
    service.postSet(setOf(-1, 0, 1))

    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-1]") },
      { hasJsonRequestBody("[-1,0,1]") },
    )
  }

  @JsName("Map_Body")
  @Test fun `Map @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postMap(emptyMap())
    service.postMap(
      mapOf(
        "id1" to Node(id = "id1", payload = 1, children = emptyList())
      )
    )
    service.postMap(
      mapOf(
        "id1" to Node(id = "id1", payload = 1, children = emptyList()),
        "id2" to Node(id = "id2", payload = 2, children = emptyList()),
        "id3" to Node(id = "id3", payload = 3, children = emptyList())
      )
    )

    assertHttpLogMatches(
      { hasJsonRequestBody("{}") },
      {
        hasJsonRequestBody(
          """
            {"id1":{"id":"id1","payload":1,"children":[]}}
          """.trimIndent()
        )
      },
      {
        hasJsonRequestBody(
          """
            {"id1":{"id":"id1","payload":1,"children":[]},"id2":{"id":"id2","payload":2,"children":[]},"id3":{"id":"id3","payload":3,"children":[]}}
          """.trimIndent()
        )
      },
    )
  }

  @JsName("MapEntry_Body")
  @Test fun `MapEntry @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postMapEntry(
      object : Map.Entry<String, Boolean> {
        override val key = "key"
        override val value = true
      }
    )
    assertHttpLogMatches {
      hasJsonRequestBody("{\"key\":true}")
    }
  }

  @JsName("Pair_Body")
  @Test fun `Pair @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postPair(1_000L to Node(id = "id", payload = 1, children = emptyList()))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"first":1000,"second":{"id":"id","payload":1,"children":[]}}
        """.trimIndent()
      )
    }
  }

  @JsName("Triple_Body")
  @Test fun `Triple @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    service.postTriple(Triple("first", Wrapper("second"), -10.0))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"first":"first","second":{"value":"second"},"third":-10.0}
        """.trimIndent()
      )
    }
  }

  @JsName("Null_Body")
  @Test fun `Null @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postNullableString(null)
    service.postNullableIntArray(null)
    service.postNullableSerializable(null)
    service.postNullableList(null)
    service.postNullableMap(null)

    assertHttpLogMatches(
      { hasJsonRequestBody("null") },
      { hasJsonRequestBody("null") },
      { hasJsonRequestBody("null") },
      { hasJsonRequestBody("null") },
      { hasJsonRequestBody("null") },
    )
  }

  @JsName("HttpBody_Body")
  @Test fun `HttpBody @Body`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(null)

    assertHttpLogMatches(
      { hasJsonRequestBody(text = "\"12345\"") },
      { hasJsonRequestBody(text = "\"12345\"") },
      { hasRequestBody(ByteArray(0), contentType = null) },
    )
  }

  @JsName("No_serializer_for_Body_Content_Type_error")
  @Test fun `No serializer for @Body Content-Type error`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))
    val expectedErrorMessage = "No suitable HttpBodySerializer found for writing Content-Type: 'image/gif'"

    assertThrows<IllegalStateException>(message = expectedErrorMessage) {
      service.postGif("gif")
    }

    val result = service.postGifResult("gif")
    assertIs<HttpResult.Failure>(result)
    assertIs<IllegalStateException>(result.exception)
    assertEquals(expectedErrorMessage, result.exception.message)
  }

  @JsName("Body_Content_Type_with_Charset_parameter")
  @Test fun `@Body Content-Type with Charset parameter`() = runHttpTest {
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    service.postContentTypeWithCharset("text")

    assertHttpLogMatches {
      hasRequestBody("\"text\"", ContentType.Application.Json.withCharset(Charsets.UTF_8))
    }
  }

  @JsName("Body_serialization_error")
  @Test fun `@Body serialization error`() = runHttpTest {
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
    val service = RequestBodiesTestService(BASE_URL, httpClient, listOf(throwingSerializer))

    assertThrows<SerializationException>(message = "oops") { service.postString("s") }

    val result = service.postStringResult("s")
    assertIs<HttpResult.Failure>(result)
    assertIs<SerializationException>(result.exception)
    assertEquals("oops", result.exception.message)
  }

  @JsName("Body_without_a_Content_Type")
  @Test fun `@Body without a Content-Type`() = runHttpTest {
    val service = RequestBodiesTestService(
      BASE_URL,
      httpClient,
      listOf(JsonBodySerializer(contentTypeMatcher = { true }))
    )

    service.postBodyWithoutContentType("12345")

    assertHttpLogMatches {
      hasJsonRequestBody(text = "\"12345\"")
    }
  }

  @JsName("Body_with_an_empty_Content_Type")
  @Test fun `@Body with an empty Content-Type`() = runHttpTest {
    val service = RequestBodiesTestService(
      BASE_URL,
      httpClient,
      listOf(JsonBodySerializer(contentTypeMatcher = { true }))
    )

    service.postBodyWithEmptyContentType("12345")

    assertHttpLogMatches {
      hasJsonRequestBody(text = "\"12345\"")
    }
  }
}

private fun HttpLogEntry.MatcherBuilder.hasJsonRequestBody(bytes: ByteArray) {
  return hasRequestBody(bytes, ContentType.Application.Json)
}

private fun HttpLogEntry.MatcherBuilder.hasJsonRequestBody(text: String) {
  return hasJsonRequestBody(text.encodeToByteArray())
}
