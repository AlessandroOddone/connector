package connector

import connector.http.Body
import connector.http.HttpBody
import connector.http.POST
import connector.test.util.assertThrows
import connector.util.HttpLogEntry
import connector.util.JsonContentSerializer
import connector.util.assertHttpLogMatches
import connector.util.runHttpTest
import io.ktor.http.ContentType
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

private val BASE_URL = Url("https://requestBodies/")
private const val JSON = "application/json"

@Service interface HttpRequestBodiesTestService {
  @POST("postBoolean") suspend fun postBoolean(@Body(JSON) body: Boolean)
  @POST("postByte") suspend fun postByte(@Body(JSON) body: Byte)
  @POST("postChar") suspend fun postChar(@Body(JSON) body: Char)
  @POST("postDouble") suspend fun postDouble(@Body(JSON) body: Double)
  @POST("postFloat") suspend fun postFloat(@Body(JSON) body: Float)
  @POST("postInt") suspend fun postInt(@Body(JSON) body: Int)
  @POST("postLong") suspend fun postLong(@Body(JSON) body: Long)
  @POST("postShort") suspend fun postShort(@Body(JSON) body: Short)
  @POST("postString") suspend fun postString(@Body(JSON) body: String)

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

  @POST("postHttpBody") suspend fun postHttpBody(@Body(JSON) body: HttpBody<String>)
  @POST("postNullableHttpBody") suspend fun postNullableHttpBody(@Body(JSON) body: HttpBody<String>?)
}

class HttpRequestBodies {
  @Test fun `Boolean @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postBoolean(true)
    service.postBoolean(false)
    assertHttpLogMatches(
      { hasJsonRequestBody("true") },
      { hasJsonRequestBody("false") }
    )
  }

  @Test fun `Byte @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postByte(10)
    assertHttpLogMatches { hasJsonRequestBody("10") }
  }

  @Test fun `Char @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postChar('a')
    assertHttpLogMatches { hasJsonRequestBody("\"a\"") }
  }

  @Test fun `Double @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postDouble(-10.7)
    assertHttpLogMatches { hasJsonRequestBody("-10.7") }
  }

  @Test fun `Float @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postFloat(2.4f)
    assertHttpLogMatches { hasJsonRequestBody("2.4") }
  }

  @Test fun `Int @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postInt(-10)
    assertHttpLogMatches { hasJsonRequestBody("-10") }
  }

  @Test fun `Long @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postLong(10)
    assertHttpLogMatches { hasJsonRequestBody("10") }
  }

  @Test fun `Short @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postShort(0)
    assertHttpLogMatches { hasJsonRequestBody("0") }
  }

  @Test fun `String @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postString("aString")
    assertHttpLogMatches { hasJsonRequestBody("\"aString\"") }
  }

  @Test fun `BooleanArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postBooleanArray(booleanArrayOf())
    service.postBooleanArray(booleanArrayOf(true))
    service.postBooleanArray(booleanArrayOf(true, false))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[true]") },
      { hasJsonRequestBody("[true,false]") }
    )
  }

  @Test fun `ByteArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postByteArray(byteArrayOf())
    service.postByteArray(byteArrayOf(-10))
    service.postByteArray(byteArrayOf(10, -11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-10]") },
      { hasJsonRequestBody("[10,-11]") }
    )
  }

  @Test fun `CharArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postCharArray(charArrayOf())
    service.postCharArray(charArrayOf('a'))
    service.postCharArray(charArrayOf('a', 'b'))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[\"a\"]") },
      { hasJsonRequestBody("[\"a\",\"b\"]") }
    )
  }

  @Test fun `DoubleArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postDoubleArray(doubleArrayOf())
    service.postDoubleArray(doubleArrayOf(10.7))
    service.postDoubleArray(doubleArrayOf(10.7, -10.8))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10.7]") },
      { hasJsonRequestBody("[10.7,-10.8]") }
    )
  }

  @Test fun `FloatArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postFloatArray(floatArrayOf())
    service.postFloatArray(floatArrayOf(-2.4f))
    service.postFloatArray(floatArrayOf(2.4f, 2.5f))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-2.4]") },
      { hasJsonRequestBody("[2.4,2.5]") }
    )
  }

  @Test fun `IntArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postIntArray(intArrayOf())
    service.postIntArray(intArrayOf(10))
    service.postIntArray(intArrayOf(10, 11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10]") },
      { hasJsonRequestBody("[10,11]") }
    )
  }

  @Test fun `LongArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postLongArray(longArrayOf())
    service.postLongArray(longArrayOf(-10))
    service.postLongArray(longArrayOf(10, -11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-10]") },
      { hasJsonRequestBody("[10,-11]") }
    )
  }

  @Test fun `ShortArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postShortArray(shortArrayOf())
    service.postShortArray(shortArrayOf(10))
    service.postShortArray(shortArrayOf(-10, 11))
    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[10]") },
      { hasJsonRequestBody("[-10,11]") }
    )
  }

  @Test fun `@Serializable @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
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

  @Test fun `@Serializable with generic argument @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postSerializableWithGeneric(Wrapper("aString"))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"value":"aString"}
        """.trimIndent()
      )
    }
  }

  @Test fun `JsonElement @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
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

  @Test fun `Array @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
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

  @Test fun `List @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))

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

  @Test fun `Set @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))

    service.postSet(emptySet())
    service.postSet(setOf(-1))
    service.postSet(setOf(-1, 0, 1))

    assertHttpLogMatches(
      { hasJsonRequestBody("[]") },
      { hasJsonRequestBody("[-1]") },
      { hasJsonRequestBody("[-1,0,1]") },
    )
  }

  @Test fun `Map @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))

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

  @Test fun `MapEntry @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
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

  @Test fun `Pair @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postPair(1_000L to Node(id = "id", payload = 1, children = emptyList()))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"first":1000,"second":{"id":"id","payload":1,"children":[]}}
        """.trimIndent()
      )
    }
  }

  @Test fun `Triple @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    service.postTriple(Triple("first", Wrapper("second"), -10.0))
    assertHttpLogMatches {
      hasJsonRequestBody(
        """
          {"first":"first","second":{"value":"second"},"third":-10.0}
        """.trimIndent()
      )
    }
  }

  @Test fun `Null @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))

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

  @Test fun `HttpBody @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))

    service.postHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(null)

    assertHttpLogMatches(
      { hasJsonRequestBody(text = "\"12345\"") },
      { hasJsonRequestBody(text = "\"12345\"") },
      { hasRequestBody(ByteArray(0), contentType = null) },
    )
  }

  @Test fun `No serializer for @Body Content-Type error`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonContentSerializer))
    assertThrows<IllegalStateException>(
      message = "No suitable HttpContentSerializer found for writing Content-Type: 'image/gif'"
    ) {
      service.postGif("gif")
    }
  }
}

@Serializable
data class Node(
  val id: String,
  val payload: Int,
  val children: List<Node>
)

@Serializable
data class Wrapper<T>(val value: T) {
  override fun toString() = value.toString()
}

private fun HttpLogEntry.MatcherBuilder.hasJsonRequestBody(bytes: ByteArray) {
  return hasRequestBody(bytes, ContentType.Application.Json)
}

private fun HttpLogEntry.MatcherBuilder.hasJsonRequestBody(text: String) {
  return hasJsonRequestBody(text.encodeToByteArray())
}
