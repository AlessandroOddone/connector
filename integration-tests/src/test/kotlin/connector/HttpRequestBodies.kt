package connector

import connector.http.Body
import connector.http.HttpBody
import connector.http.JsonBody
import connector.http.POST
import connector.test.util.assertThrows
import connector.util.JsonBodySerializer
import connector.util.assertHttpLogMatches
import connector.util.hasRequestBody
import connector.util.runHttpTest
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

private val BASE_URL = Url("https://requestBodies/")

@Service interface HttpRequestBodiesTestService {
  @POST("postBoolean") suspend fun postBoolean(@JsonBody body: Boolean)
  @POST("postByte") suspend fun postByte(@JsonBody body: Byte)
  @POST("postChar") suspend fun postChar(@JsonBody body: Char)
  @POST("postDouble") suspend fun postDouble(@JsonBody body: Double)
  @POST("postFloat") suspend fun postFloat(@JsonBody body: Float)
  @POST("postInt") suspend fun postInt(@JsonBody body: Int)
  @POST("postLong") suspend fun postLong(@JsonBody body: Long)
  @POST("postShort") suspend fun postShort(@JsonBody body: Short)
  @POST("postString") suspend fun postString(@JsonBody body: String)

  @POST("postBooleanArray") suspend fun postBooleanArray(@JsonBody body: BooleanArray)
  @POST("postByteArray") suspend fun postByteArray(@JsonBody body: ByteArray)
  @POST("postCharArray") suspend fun postCharArray(@JsonBody body: CharArray)
  @POST("postDoubleArray") suspend fun postDoubleArray(@JsonBody body: DoubleArray)
  @POST("postFloatArray") suspend fun postFloatArray(@JsonBody body: FloatArray)
  @POST("postIntArray") suspend fun postIntArray(@JsonBody body: IntArray)
  @POST("postLongArray") suspend fun postLongArray(@JsonBody body: LongArray)
  @POST("postShortArray") suspend fun postShortArray(@JsonBody body: ShortArray)

  @POST("postSerializable") suspend fun postSerializable(@JsonBody body: Node)
  @POST("postSerializableWithGeneric") suspend fun postSerializableWithGeneric(@JsonBody body: Wrapper<String>)
  @POST("postJsonElement") suspend fun postJsonElement(@JsonBody body: JsonElement)

  @POST("postArray") suspend fun postArray(@JsonBody body: Array<Node>)
  @POST("postList") suspend fun postList(@JsonBody body: List<Wrapper<String>>)
  @POST("postSet") suspend fun postSet(@JsonBody body: Set<Int>)
  @POST("postMap") suspend fun postMap(@JsonBody body: Map<String, Node>)
  @POST("postMapEntry") suspend fun postMapEntry(@JsonBody body: Map.Entry<String, Boolean>)
  @POST("postPair") suspend fun postPair(@JsonBody body: Pair<Long, Node>)
  @POST("postTriple") suspend fun postTriple(@JsonBody body: Triple<String, Wrapper<String>, Double>)

  @POST("postNullableString") suspend fun postNullableString(@JsonBody body: String?)
  @POST("postNullableIntArray") suspend fun postNullableIntArray(@JsonBody body: IntArray?)
  @POST("postNullableSerializable") suspend fun postNullableSerializable(@JsonBody body: Node?)
  @POST("postNullableList") suspend fun postNullableList(@JsonBody body: List<Wrapper<String>>?)
  @POST("postNullableMap") suspend fun postNullableMap(@JsonBody body: Map<String, Node>?)

  @POST("postGif") suspend fun postGif(@Body("image/gif") body: String)

  @POST("postHttpBody") suspend fun postHttpBody(@JsonBody body: HttpBody<String>)
  @POST("postNullableHttpBody") suspend fun postNullableHttpBody(@JsonBody body: HttpBody<String>?)
}

class HttpRequestBodies {
  @Test fun `Boolean @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postBoolean(true)
    service.postBoolean(false)
    assertHttpLogMatches(
      { hasRequestBody("true") },
      { hasRequestBody("false") }
    )
  }

  @Test fun `Byte @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postByte(10)
    assertHttpLogMatches { hasRequestBody("10") }
  }

  @Test fun `Char @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postChar('a')
    assertHttpLogMatches { hasRequestBody("\"a\"") }
  }

  @Test fun `Double @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postDouble(-10.7)
    assertHttpLogMatches { hasRequestBody("-10.7") }
  }

  @Test fun `Float @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postFloat(2.4f)
    assertHttpLogMatches { hasRequestBody("2.4") }
  }

  @Test fun `Int @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postInt(-10)
    assertHttpLogMatches { hasRequestBody("-10") }
  }

  @Test fun `Long @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postLong(10)
    assertHttpLogMatches { hasRequestBody("10") }
  }

  @Test fun `Short @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postShort(0)
    assertHttpLogMatches { hasRequestBody("0") }
  }

  @Test fun `String @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postString("aString")
    assertHttpLogMatches { hasRequestBody("\"aString\"") }
  }

  @Test fun `BooleanArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postBooleanArray(booleanArrayOf())
    service.postBooleanArray(booleanArrayOf(true))
    service.postBooleanArray(booleanArrayOf(true, false))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[true]") },
      { hasRequestBody("[true,false]") }
    )
  }

  @Test fun `ByteArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postByteArray(byteArrayOf())
    service.postByteArray(byteArrayOf(-10))
    service.postByteArray(byteArrayOf(10, -11))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[-10]") },
      { hasRequestBody("[10,-11]") }
    )
  }

  @Test fun `CharArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postCharArray(charArrayOf())
    service.postCharArray(charArrayOf('a'))
    service.postCharArray(charArrayOf('a', 'b'))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[\"a\"]") },
      { hasRequestBody("[\"a\",\"b\"]") }
    )
  }

  @Test fun `DoubleArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postDoubleArray(doubleArrayOf())
    service.postDoubleArray(doubleArrayOf(10.7))
    service.postDoubleArray(doubleArrayOf(10.7, -10.8))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[10.7]") },
      { hasRequestBody("[10.7,-10.8]") }
    )
  }

  @Test fun `FloatArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postFloatArray(floatArrayOf())
    service.postFloatArray(floatArrayOf(-2.4f))
    service.postFloatArray(floatArrayOf(2.4f, 2.5f))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[-2.4]") },
      { hasRequestBody("[2.4,2.5]") }
    )
  }

  @Test fun `IntArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postIntArray(intArrayOf())
    service.postIntArray(intArrayOf(10))
    service.postIntArray(intArrayOf(10, 11))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[10]") },
      { hasRequestBody("[10,11]") }
    )
  }

  @Test fun `LongArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postLongArray(longArrayOf())
    service.postLongArray(longArrayOf(-10))
    service.postLongArray(longArrayOf(10, -11))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[-10]") },
      { hasRequestBody("[10,-11]") }
    )
  }

  @Test fun `ShortArray @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postShortArray(shortArrayOf())
    service.postShortArray(shortArrayOf(10))
    service.postShortArray(shortArrayOf(-10, 11))
    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[10]") },
      { hasRequestBody("[-10,11]") }
    )
  }

  @Test fun `@Serializable @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
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
      hasRequestBody(
        """
          {"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}
        """.trimIndent()
      )
    }
  }

  @Test fun `@Serializable with generic argument @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postSerializableWithGeneric(Wrapper("aString"))
    assertHttpLogMatches {
      hasRequestBody(
        """
          {"value":"aString"}
        """.trimIndent()
      )
    }
  }

  @Test fun `JsonElement @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    val jsonObject = buildJsonObject {
      put("id", JsonPrimitive("1"))
      put("values", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))
    }

    service.postJsonElement(JsonPrimitive("text"))
    service.postJsonElement(JsonPrimitive(-1_000))
    service.postJsonElement(jsonObject)
    service.postJsonElement(JsonArray(listOf(jsonObject, JsonPrimitive(-9.9f))))

    assertHttpLogMatches(
      { hasRequestBody("\"text\"") },
      { hasRequestBody("-1000") },
      {
        hasRequestBody(
          """
          {"id":"1","values":[1,2]}
          """.trimIndent()
        )
      },
      {
        hasRequestBody(
          """
          [{"id":"1","values":[1,2]},-9.9]
          """.trimIndent()
        )
      },
    )
  }

  @Test fun `Array @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    val fistChild = Node(id = "2", payload = 200, children = emptyList())
    val secondChild = Node(id = "3", payload = 300, children = emptyList())
    val root = Node(id = "1", payload = 100, children = listOf(fistChild, secondChild))

    service.postArray(arrayOf())
    service.postArray(arrayOf(root))
    service.postArray(arrayOf(root, fistChild, secondChild))

    assertHttpLogMatches(
      { hasRequestBody("[]") },
      {
        hasRequestBody(
          """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]}]
          """.trimIndent()
        )
      },
      {
        hasRequestBody(
          """
          [{"id":"1","payload":100,"children":[{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]},{"id":"2","payload":200,"children":[]},{"id":"3","payload":300,"children":[]}]
          """.trimIndent()
        )
      },
    )
  }

  @Test fun `List @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.postList(emptyList())
    service.postList(listOf(Wrapper("a")))
    service.postList(listOf(Wrapper("a"), Wrapper("b"), Wrapper("c")))

    assertHttpLogMatches(
      { hasRequestBody("[]") },
      {
        hasRequestBody(
          """
            [{"value":"a"}]
          """.trimIndent()
        )
      },
      {
        hasRequestBody(
          """
            [{"value":"a"},{"value":"b"},{"value":"c"}]
          """.trimIndent()
        )
      },
    )
  }

  @Test fun `Set @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.postSet(emptySet())
    service.postSet(setOf(-1))
    service.postSet(setOf(-1, 0, 1))

    assertHttpLogMatches(
      { hasRequestBody("[]") },
      { hasRequestBody("[-1]") },
      { hasRequestBody("[-1,0,1]") },
    )
  }

  @Test fun `Map @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

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
      { hasRequestBody("{}") },
      {
        hasRequestBody(
          """
            {"id1":{"id":"id1","payload":1,"children":[]}}
          """.trimIndent()
        )
      },
      {
        hasRequestBody(
          """
            {"id1":{"id":"id1","payload":1,"children":[]},"id2":{"id":"id2","payload":2,"children":[]},"id3":{"id":"id3","payload":3,"children":[]}}
          """.trimIndent()
        )
      },
    )
  }

  @Test fun `MapEntry @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postMapEntry(
      object : Map.Entry<String, Boolean> {
        override val key = "key"
        override val value = true
      }
    )
    assertHttpLogMatches {
      hasRequestBody("{\"key\":true}")
    }
  }

  @Test fun `Pair @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postPair(1_000L to Node(id = "id", payload = 1, children = emptyList()))
    assertHttpLogMatches {
      hasRequestBody(
        """
          {"first":1000,"second":{"id":"id","payload":1,"children":[]}}
        """.trimIndent()
      )
    }
  }

  @Test fun `Triple @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    service.postTriple(Triple("first", Wrapper("second"), -10.0))
    assertHttpLogMatches {
      hasRequestBody(
        """
          {"first":"first","second":{"value":"second"},"third":-10.0}
        """.trimIndent()
      )
    }
  }

  @Test fun `Null @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.postNullableString(null)
    service.postNullableIntArray(null)
    service.postNullableSerializable(null)
    service.postNullableList(null)
    service.postNullableMap(null)

    assertHttpLogMatches(
      { hasRequestBody("null") },
      { hasRequestBody("null") },
      { hasRequestBody("null") },
      { hasRequestBody("null") },
      { hasRequestBody("null") },
    )
  }

  @Test fun `HttpBody @Body`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.postHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(HttpBody("12345"))
    service.postNullableHttpBody(null)

    assertHttpLogMatches(
      { hasRequestBody(text = "\"12345\"") },
      { hasRequestBody(text = "\"12345\"") },
      { hasRequestBody(ByteArray(0)) },
    )
  }

  @Test fun `No serializer for @Body Content-Type error`() = runHttpTest {
    val service = HttpRequestBodiesTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))
    assertThrows<IllegalStateException>(
      message = "No suitable HttpBodySerializer found for writing Content-Type: 'image/gif'"
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
