package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.util.HttpLogEntry
import dev.aoddon.connector.util.JsonBodySerializer
import dev.aoddon.connector.util.Node
import dev.aoddon.connector.util.Wrapper
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.runHttpTest
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.utils.EmptyContent
import io.ktor.client.utils.buildHeaders
import io.ktor.http.Headers
import io.ktor.http.Url
import io.ktor.http.content.PartData
import io.ktor.http.parametersOf
import io.ktor.util.StringValues
import io.ktor.utils.io.core.ByteReadPacket
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

private val BASE_URL = Url("https://multipart/")
private const val JSON = "application/json"

@Service interface MultipartTestService {
  @POST("post")
  @Multipart
  suspend fun multipartFormStringField(@Part(JSON, "f") text: String)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringIterableFields(@PartIterable(JSON, "f") fields: Iterable<String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringCollectionFields(@PartIterable(JSON, "f") fields: Collection<String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringListFields(@PartIterable(JSON, "f") fields: List<String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringSetFields(@PartIterable(JSON, "f") fields: Set<String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormSerializableField(@Part(JSON, "f") node: Node)

  @POST("post")
  @Multipart
  suspend fun multipartFormSerializableIterableFields(@PartIterable(JSON, "f") fields: Iterable<Node>)

  @POST("post")
  @Multipart
  suspend fun multipartFormSerializableCollectionFields(@PartIterable(JSON, "f") fields: Collection<Node>)

  @POST("post")
  @Multipart
  suspend fun multipartFormSerializableListFields(@PartIterable(JSON, "f") fields: List<Node>)

  @POST("post")
  @Multipart
  suspend fun multipartFormSerializableSetFields(@PartIterable(JSON, "f") fields: Set<Node>)

  @POST("post")
  @Multipart
  suspend fun multipartFormHttpBodyField(@Part(JSON, "f") field: HttpBody<String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormHttpBodyIterableFields(
    @PartIterable(JSON, "f") fields: Iterable<HttpBody<Node>>
  )

  @POST("post")
  @Multipart
  suspend fun multipartFormHttpBodyCollectionFields(
    @PartIterable(JSON, "f") fields: Collection<HttpBody<Wrapper<Boolean>>>
  )

  @POST("post")
  @Multipart
  suspend fun multipartFormHttpBodyListFields(@PartIterable(JSON, "f") fields: List<HttpBody<String>>)

  @POST("post")
  @Multipart
  suspend fun multipartFormHttpBodySetFields(@PartIterable(JSON, "f") fields: Set<HttpBody<List<Int>>>)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringValues(@PartMap(JSON) stringValues: StringValues)

  @POST("post")
  @Multipart
  suspend fun multipartFormStringMap(@PartMap(JSON) map: Map<String, String>)

  @POST("post")
  @Multipart
  suspend fun multipartFormMapOfSerializable(@PartMap(JSON) map: Map<String, Node>)

  @POST("post")
  @Multipart
  suspend fun multipartFormMapOfHttpBody(@PartMap(JSON) map: Map<String, HttpBody<String>>)

  @POST("post")
  @Multipart
  suspend fun multipartFormListOfPairsWithStringValues(@PartMap(JSON) pairs: List<Pair<String, String>>)

  @POST("post")
  @Multipart
  suspend fun multipartFormListOfPairsWithSerializableValues(@PartMap(JSON) pairs: List<Pair<String, Node>>)

  @POST("post")
  @Multipart
  suspend fun multipartFormListOfPairsWithHttpBodyValues(@PartMap(JSON) pairs: List<Pair<String, HttpBody<Boolean>>>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedStringParameter(@Part(JSON) text: String)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedStringIterableParameter(@PartIterable(JSON) parts: Iterable<String>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedStringCollectionParameter(@PartIterable(JSON) parts: Collection<String>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedStringListParameter(@PartIterable(JSON) parts: List<String>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedStringSetParameter(@PartIterable(JSON) parts: Set<String>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedSerializableParameter(@Part(JSON) node: Node)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedSerializableIterableParameter(@PartIterable(JSON) parts: Iterable<Node>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedSerializableCollectionParameter(@PartIterable(JSON) parts: Collection<Node>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedSerializableListParameter(@PartIterable(JSON) parts: List<Node>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedSerializableSetParameter(@PartIterable(JSON) parts: Set<Node>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedHttpBodyParameter(@Part(JSON) parts: HttpBody<String>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedHttpBodyIterableParameter(@PartIterable(JSON) parts: Iterable<HttpBody<Node>>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedHttpBodyCollectionParameter(@PartIterable(JSON) parts: Collection<HttpBody<Wrapper<Boolean>>>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedHttpBodyListParameter(@PartIterable(JSON) fields: List<HttpBody<String>>)

  @POST("post")
  @Multipart("mixed")
  suspend fun multipartMixedHttpBodySetParameter(@PartIterable(JSON) fields: Set<HttpBody<List<Int>>>)

  @POST("post")
  @Multipart
  suspend fun partData(@Part partData: PartData)

  @POST("post")
  @Multipart
  suspend fun partDataBinaryItem(@Part partData: PartData.BinaryItem)

  @POST("post")
  @Multipart
  suspend fun partDataFileItem(@Part partData: PartData.FileItem)

  @POST("post")
  @Multipart
  suspend fun partDataFormItem(@Part partData: PartData.FormItem)

  @POST("post")
  @Multipart
  suspend fun partDataIterable(@PartIterable partData: Iterable<PartData>)

  @POST("post")
  @Multipart
  suspend fun partDataCollection(@PartIterable partData: Collection<PartData>)

  @POST("post")
  @Multipart
  suspend fun partDataList(@PartIterable partData: List<PartData>)

  @POST("post")
  @Multipart
  suspend fun partDataSet(@PartIterable partData: Set<PartData>)

  @POST("post")
  @Multipart
  suspend fun partDataBinaryItemIterable(@PartIterable partData: Iterable<PartData.BinaryItem>)

  @POST("post")
  @Multipart
  suspend fun partDataBinaryItemCollection(@PartIterable partData: Collection<PartData.BinaryItem>)

  @POST("post")
  @Multipart
  suspend fun partDataBinaryItemList(@PartIterable partData: List<PartData.BinaryItem>)

  @POST("post")
  @Multipart
  suspend fun partDataBinaryItemSet(@PartIterable partData: Set<PartData.BinaryItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFileItemIterable(@PartIterable partData: Iterable<PartData.FileItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFileItemCollection(@PartIterable partData: Collection<PartData.FileItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFileItemList(@PartIterable partData: List<PartData.FileItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFileItemSet(@PartIterable partData: Set<PartData.FileItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFormItemIterable(@PartIterable partData: Iterable<PartData.FormItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFormItemCollection(@PartIterable partData: Collection<PartData.FormItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFormItemList(@PartIterable partData: List<PartData.FormItem>)

  @POST("post")
  @Multipart
  suspend fun partDataFormItemSet(@PartIterable partData: Set<PartData.FormItem>)

  @POST("post")
  @Multipart
  suspend fun multipartFormMultipleParameters(
    @Part(JSON, "f1") text: String,
    @Part(JSON, "f1") node: Node,
    @PartIterable(JSON, "f2") textList: List<String>,
    @PartIterable(JSON, "f2") nodeSet: Set<Node>,
    @PartMap(JSON) map: Map<String, Wrapper<Boolean>>,
    @PartMap(JSON) stringValues: StringValues,
    @Part partData: PartData,
    @PartIterable partDataList: List<PartData>,
    @Part(JSON, "f3") httpBody: HttpBody<List<Boolean>>,
    @PartIterable(JSON, "f4") httpBodyList: List<HttpBody<String>>,
    @PartMap(JSON) httpBodyMap: Map<String, HttpBody<Set<Node>>>,
    @PartMap(JSON) httpBodyMultimap: List<Pair<String, HttpBody<Boolean>>>
  )

  @POST("post")
  @Multipart
  suspend fun multipartFormNullableTypes(
    @Part(JSON, "f1") text: String?,
    @Part(JSON, "f1") node: Node?,
    @PartIterable(JSON, "f2") textList: List<String?>?,
    @PartIterable(JSON, "f2") nodeSet: Set<Node?>?,
    @PartMap(JSON) map: Map<String, Wrapper<Boolean?>?>?,
    @PartMap(JSON) stringValues: StringValues?,
    @Part partData: PartData?,
    @PartIterable partDataList: List<PartData?>?,
    @Part(JSON, "f3") httpBody: HttpBody<List<Boolean?>?>?,
    @PartIterable(JSON, "f4") httpBodyList: List<HttpBody<String?>?>?,
    @PartMap(JSON) httpBodyMap: Map<String, HttpBody<Set<Node?>?>?>?,
    @PartMap(JSON) httpBodyMultimap: List<Pair<String, HttpBody<Boolean?>?>>?
  )

  @POST("post")
  @Multipart
  suspend fun multipartFormPotentiallyEmpty(
    @Part(JSON, "f1") httpBody: HttpBody<String>?,
    @PartIterable(JSON, "f2") list: List<String>?,
    @PartMap(JSON) map: Map<String, String>?,
    @PartMap(JSON) stringValues: StringValues?
  )

  @POST("post")
  @Multipart(subtype = "mixed")
  suspend fun multipartMixedMultipleParameters(
    @Part(JSON) text: String?,
    @Part(JSON) node: Node?,
    @PartIterable(JSON) textList: List<String?>?,
    @PartIterable(JSON) nodeSet: Set<Node?>?,
    @PartMap(JSON) map: Map<String, Wrapper<Boolean?>?>?,
    @PartMap(JSON) stringValues: StringValues?,
    @Part partData: PartData?,
    @PartIterable partDataList: List<PartData?>?,
    @Part(JSON) httpBody: HttpBody<List<Boolean?>?>?,
    @PartIterable(JSON) httpBodyList: List<HttpBody<String?>?>?,
    @PartMap(JSON) httpBodyMap: Map<String, HttpBody<Set<Node?>?>?>?,
    @PartMap(JSON) httpBodyMultimap: List<Pair<String, HttpBody<Boolean?>?>>?
  )

  @POST("post")
  @Multipart(subtype = "mixed")
  suspend fun multipartMixedPotentiallyEmpty(
    @Part(JSON) httpBody: HttpBody<String>?,
    @PartIterable(JSON) list: List<String>?,
    @PartMap(JSON) map: Map<String, String>?,
    @PartMap(JSON) stringValues: StringValues?
  )
}

class MultipartTest {
  @Test fun `@Multipart form String @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormStringField("value")

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"value\""
        )
      )
    )
  }

  @Test fun `@Multipart form String @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormStringIterableFields(emptyList())
    service.multipartFormStringCollectionFields(listOf("1", "2", "3"))
    service.multipartFormStringListFields(listOf("4", "5", "6"))
    service.multipartFormStringSetFields(setOf("7"))

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"1\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"2\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"3\""
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"4\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"5\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"6\""
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"7\""
        )
      )
    )
  }

  @Test fun `@Multipart form Serializable @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormSerializableField(
      Node(id = "1", payload = 1, children = emptyList())
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"1\",\"payload\":1,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@Multipart form Serializable @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormSerializableIterableFields(emptyList())
    service.multipartFormSerializableCollectionFields(
      listOf(
        Node(id = "1", payload = 1, children = emptyList()),
        Node(id = "2", payload = 2, children = emptyList()),
        Node(id = "3", payload = 3, children = emptyList())
      )
    )
    service.multipartFormSerializableListFields(
      listOf(
        Node(id = "4", payload = 4, children = emptyList()),
        Node(id = "5", payload = 5, children = emptyList()),
        Node(id = "6", payload = 6, children = emptyList())
      )
    )
    service.multipartFormSerializableSetFields(
      setOf(
        Node(id = "7", payload = 7, children = emptyList()),
      )
    )

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"1\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"2\",\"payload\":2,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"3\",\"payload\":3,\"children\":[]}"
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"4\",\"payload\":4,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"5\",\"payload\":5,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"6\",\"payload\":6,\"children\":[]}"
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"id\":\"7\",\"payload\":7,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@Multipart form HttpBody @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormHttpBodyField(HttpBody("value"))

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"value\""
        )
      )
    )
  }

  @Test fun `@Multipart form HttpBody @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormHttpBodyIterableFields(emptyList())
    service.multipartFormHttpBodyCollectionFields(
      listOf(
        HttpBody(Wrapper(true)),
        HttpBody(Wrapper(false))
      )
    )
    service.multipartFormHttpBodyListFields(
      listOf(
        HttpBody("a"),
        HttpBody("b"),
        HttpBody("c")
      )
    )
    service.multipartFormHttpBodySetFields(
      setOf(
        HttpBody(listOf(1, 2, 3)),
      )
    )

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"value\":true}"
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "{\"value\":false}"
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"b\""
        ),
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "\"c\""
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f",
          contentType = JSON,
          content = "[1,2,3]"
        )
      )
    )
  }

  @Test fun `@PartMap StringValues`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormStringValues(
      parametersOf(
        "key1" to listOf("a", "b"),
        "key2" to listOf("c"),
        "key3" to emptyList()
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"b\""
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "\"c\""
        )
      )
    )
  }

  @Test fun `@PartMap Map with String values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormStringMap(
      mapOf(
        "key1" to "a",
        "key2" to "b"
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "\"b\""
        )
      )
    )
  }

  @Test fun `@PartMap Map with Serializable values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormMapOfSerializable(
      mapOf(
        "key1" to Node("id1", 1, emptyList()),
        "key2" to Node("id2", 2, emptyList())
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "{\"id\":\"id1\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "{\"id\":\"id2\",\"payload\":2,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@PartMap Map with HttpBody values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormMapOfHttpBody(
      mapOf(
        "key1" to HttpBody("a"),
        "key2" to HttpBody("b")
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "\"b\""
        )
      )
    )
  }

  @Test fun `@PartMap List of Pairs with String values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormListOfPairsWithStringValues(
      listOf(
        "key1" to "a",
        "key2" to "b",
        "key1" to "c",
        "key2" to "d",
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "\"b\""
        ),
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "\"c\""
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "\"d\""
        )
      )
    )
  }

  @Test fun `@PartMap List of Pairs with Serializable values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormListOfPairsWithSerializableValues(
      listOf(
        "key1" to Node("a", 1, emptyList()),
        "key2" to Node("b", 2, emptyList()),
        "key1" to Node("c", 3, emptyList()),
        "key2" to Node("d", 4, emptyList()),
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "{\"id\":\"a\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "{\"id\":\"b\",\"payload\":2,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "{\"id\":\"c\",\"payload\":3,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "{\"id\":\"d\",\"payload\":4,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@PartMap List of Pairs with HttpBody values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormListOfPairsWithHttpBodyValues(
      listOf(
        "key1" to HttpBody(true),
        "key2" to HttpBody(false),
        "key1" to HttpBody(false),
        "key2" to HttpBody(true),
      )
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "true"
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "false"
        ),
        TextPart(
          formFieldName = "key1",
          contentType = JSON,
          content = "false"
        ),
        TextPart(
          formFieldName = "key2",
          contentType = JSON,
          content = "true"
        )
      )
    )
  }

  @Test fun `@Multipart mixed String @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedStringParameter("value")

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"value\""
        )
      )
    )
  }

  @Test fun `@Multipart mixed String @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedStringIterableParameter(emptyList())
    service.multipartMixedStringCollectionParameter(listOf("1", "2", "3"))
    service.multipartMixedStringListParameter(listOf("4", "5", "6"))
    service.multipartMixedStringSetParameter(setOf("7"))

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"1\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"2\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"3\""
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"4\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"5\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"6\""
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"7\""
        )
      )
    )
  }

  @Test fun `@Multipart mixed Serializable @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedSerializableParameter(
      Node(id = "1", payload = 1, children = emptyList())
    )

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"1\",\"payload\":1,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@Multipart mixed Serializable @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedSerializableIterableParameter(emptyList())
    service.multipartMixedSerializableCollectionParameter(
      listOf(
        Node(id = "1", payload = 1, children = emptyList()),
        Node(id = "2", payload = 2, children = emptyList()),
        Node(id = "3", payload = 3, children = emptyList())
      )
    )
    service.multipartMixedSerializableListParameter(
      listOf(
        Node(id = "4", payload = 4, children = emptyList()),
        Node(id = "5", payload = 5, children = emptyList()),
        Node(id = "6", payload = 6, children = emptyList())
      )
    )
    service.multipartMixedSerializableSetParameter(
      setOf(
        Node(id = "7", payload = 7, children = emptyList()),
      )
    )

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"1\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"2\",\"payload\":2,\"children\":[]}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"3\",\"payload\":3,\"children\":[]}"
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"4\",\"payload\":4,\"children\":[]}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"5\",\"payload\":5,\"children\":[]}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"6\",\"payload\":6,\"children\":[]}"
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"7\",\"payload\":7,\"children\":[]}"
        )
      )
    )
  }

  @Test fun `@Multipart mixed HttpBody @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedHttpBodyParameter(HttpBody("value"))

    assertEquals(1, httpLog.size)
    httpLog.last().assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"value\""
        )
      )
    )
  }

  @Test fun `@Multipart mixed HttpBody @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartMixedHttpBodyIterableParameter(emptyList())
    service.multipartMixedHttpBodyCollectionParameter(
      listOf(
        HttpBody(Wrapper(true)),
        HttpBody(Wrapper(false))
      )
    )
    service.multipartMixedHttpBodyListParameter(
      listOf(
        HttpBody("a"),
        HttpBody("b"),
        HttpBody("c")
      )
    )
    service.multipartMixedHttpBodySetParameter(
      setOf(
        HttpBody(listOf(1, 2, 3)),
      )
    )

    assertEquals(4, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"value\":true}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"value\":false}"
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"a\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"b\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"c\""
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "[1,2,3]"
        )
      )
    )
  }

  @Test fun `PartData BinaryItem @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    val partData = PartData.BinaryItem(
      provider = { ByteReadPacket("value".encodeToByteArray()) },
      dispose = {},
      partHeaders = buildHeaders {
        append("a", "1")
        append("b", "2")
      }
    )

    service.partData(partData)
    service.partDataBinaryItem(partData)

    assertEquals(2, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )
  }

  @Test fun `PartData FileItem @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    val partData = PartData.FileItem(
      provider = { ByteReadPacket("value".encodeToByteArray()) },
      dispose = {},
      partHeaders = buildHeaders {
        append("a", "1")
        append("b", "2")
      }
    )

    service.partData(partData)
    service.partDataFileItem(partData)

    assertEquals(2, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )
  }

  @Test fun `PartData FormItem @Part`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    val partData = PartData.FormItem(
      value = "value",
      dispose = {},
      partHeaders = buildHeaders {
        append("a", "1")
        append("b", "2")
      }
    )

    service.partData(partData)
    service.partDataFormItem(partData)

    assertEquals(2, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("a: 1", "b: 2"),
          content = "value"
        )
      )
    )
  }

  @Test fun `PartData @PartIterable`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    val binaryPart = PartData.BinaryItem(
      provider = { ByteReadPacket("binary".encodeToByteArray()) },
      dispose = {},
      partHeaders = buildHeaders {
        append("binary1", "1")
        append("binary2", "2")
      }
    )
    val filePart = PartData.FileItem(
      provider = { ByteReadPacket("file".encodeToByteArray()) },
      dispose = {},
      partHeaders = buildHeaders {
        append("file1", "1")
        append("file2", "2")
      }
    )
    val formPart = PartData.FormItem(
      value = "form",
      dispose = {},
      partHeaders = buildHeaders {
        append("form1", "1")
        append("form2", "2")
      }
    )

    service.partDataIterable(emptyList())
    service.partDataCollection(listOf(binaryPart, filePart, formPart))
    service.partDataList(listOf(binaryPart, formPart))
    service.partDataSet(setOf(filePart))

    service.partDataBinaryItemIterable(emptyList())
    service.partDataBinaryItemCollection(listOf(binaryPart))
    service.partDataBinaryItemList(listOf(binaryPart))
    service.partDataBinaryItemSet(setOf(binaryPart))

    service.partDataFileItemIterable(emptyList())
    service.partDataFileItemCollection(listOf(filePart))
    service.partDataFileItemList(listOf(filePart))
    service.partDataFileItemSet(setOf(filePart))

    service.partDataFormItemIterable(emptyList())
    service.partDataFormItemCollection(listOf(formPart))
    service.partDataFormItemList(listOf(formPart))
    service.partDataFormItemSet(setOf(formPart))

    assertEquals(16, httpLog.size)

    assertEquals(EmptyContent, httpLog[0].requestBody)

    httpLog[1].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("binary1: 1", "binary2: 2"),
          content = "binary"
        ),
        TextPart(
          headers = listOf("file1: 1", "file2: 2"),
          content = "file"
        ),
        TextPart(
          headers = listOf("form1: 1", "form2: 2"),
          content = "form"
        )
      )
    )

    httpLog[2].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("binary1: 1", "binary2: 2"),
          content = "binary"
        ),
        TextPart(
          headers = listOf("form1: 1", "form2: 2"),
          content = "form"
        )
      )
    )

    httpLog[3].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("file1: 1", "file2: 2"),
          content = "file"
        )
      )
    )

    assertEquals(EmptyContent, httpLog[4].requestBody)

    httpLog[5].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("binary1: 1", "binary2: 2"),
          content = "binary"
        )
      )
    )

    httpLog[6].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("binary1: 1", "binary2: 2"),
          content = "binary"
        )
      )
    )

    httpLog[7].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("binary1: 1", "binary2: 2"),
          content = "binary"
        )
      )
    )

    assertEquals(EmptyContent, httpLog[8].requestBody)

    httpLog[9].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("file1: 1", "file2: 2"),
          content = "file"
        )
      )
    )

    httpLog[10].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("file1: 1", "file2: 2"),
          content = "file"
        )
      )
    )

    httpLog[11].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("file1: 1", "file2: 2"),
          content = "file"
        )
      )
    )

    assertEquals(EmptyContent, httpLog[12].requestBody)

    httpLog[13].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("form1: 1", "form2: 2"),
          content = "form"
        )
      )
    )

    httpLog[14].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("form1: 1", "form2: 2"),
          content = "form"
        )
      )
    )

    httpLog[15].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          headers = listOf("form1: 1", "form2: 2"),
          content = "form"
        )
      )
    )
  }

  @Test fun `@Multipart form with multiple parameters`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    fun node(id: String) = Node(id, 1, emptyList())

    service.multipartFormMultipleParameters(
      text = "text",
      node = node("node"),
      textList = listOf("textListItem"),
      nodeSet = setOf(node("nodeSetItem")),
      map = mapOf("wrapperTrue" to Wrapper(true), "wrapperFalse" to Wrapper(false)),
      stringValues = parametersOf(
        "stringValueName1" to listOf("stringValue1"),
        "stringValueName2" to listOf("stringValue2"),
      ),
      partData = PartData.BinaryItem(
        provider = { ByteReadPacket("PartData".encodeToByteArray()) },
        dispose = {},
        partHeaders = Headers.Empty
      ),
      partDataList = listOf(
        PartData.BinaryItem(
          provider = { ByteReadPacket("PartData.BinaryItem".encodeToByteArray()) },
          dispose = {},
          partHeaders = Headers.Empty
        ),
        PartData.FileItem(
          provider = { ByteReadPacket("PartData.FileItem".encodeToByteArray()) },
          dispose = {},
          partHeaders = Headers.Empty
        ),
        PartData.FormItem(
          value = "PartData.FormItem",
          dispose = {},
          partHeaders = Headers.Empty
        )
      ),
      httpBody = HttpBody(listOf(true, false)),
      httpBodyList = listOf(
        HttpBody("HttpBody1"),
        HttpBody("HttpBody2")
      ),
      httpBodyMap = mapOf(
        "nodeSetKey" to HttpBody(setOf(node("nodeSetItem"))),
        "emptySetKey" to HttpBody(emptySet())
      ),
      httpBodyMultimap = listOf(
        "pairKey1" to HttpBody(true),
        "pairKey1" to HttpBody(false),
        "pairKey2" to HttpBody(true)
      )
    )

    assertEquals(1, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      subtype = "form-data",
      parts = listOf(
        TextPart(
          formFieldName = "f1",
          contentType = JSON,
          content = "\"text\""
        ),
        TextPart(
          formFieldName = "f1",
          contentType = JSON,
          content = "{\"id\":\"node\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "f2",
          contentType = JSON,
          content = "\"textListItem\""
        ),
        TextPart(
          formFieldName = "f2",
          contentType = JSON,
          content = "{\"id\":\"nodeSetItem\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "wrapperTrue",
          contentType = JSON,
          content = "{\"value\":true}"
        ),
        TextPart(
          formFieldName = "wrapperFalse",
          contentType = JSON,
          content = "{\"value\":false}"
        ),
        TextPart(
          formFieldName = "stringValueName1",
          contentType = JSON,
          content = "\"stringValue1\""
        ),
        TextPart(
          formFieldName = "stringValueName2",
          contentType = JSON,
          content = "\"stringValue2\""
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.BinaryItem"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.FileItem"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.FormItem"
        ),
        TextPart(
          formFieldName = "f3",
          contentType = JSON,
          content = "[true,false]"
        ),
        TextPart(
          formFieldName = "f4",
          contentType = JSON,
          content = "\"HttpBody1\""
        ),
        TextPart(
          formFieldName = "f4",
          contentType = JSON,
          content = "\"HttpBody2\""
        ),
        TextPart(
          formFieldName = "nodeSetKey",
          contentType = JSON,
          content = "[{\"id\":\"nodeSetItem\",\"payload\":1,\"children\":[]}]"
        ),
        TextPart(
          formFieldName = "emptySetKey",
          contentType = JSON,
          content = "[]"
        ),
        TextPart(
          formFieldName = "pairKey1",
          contentType = JSON,
          content = "true"
        ),
        TextPart(
          formFieldName = "pairKey1",
          contentType = JSON,
          content = "false"
        ),
        TextPart(
          formFieldName = "pairKey2",
          contentType = JSON,
          content = "true"
        )
      )
    )
  }

  @Test fun `@Multipart mixed with multiple parameters`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    fun node(id: String) = Node(id, 1, emptyList())

    service.multipartMixedMultipleParameters(
      text = "text",
      node = node("node"),
      textList = listOf("textListItem"),
      nodeSet = setOf(node("nodeSetItem")),
      map = mapOf("wrapperTrue" to Wrapper(true), "wrapperFalse" to Wrapper(false)),
      stringValues = parametersOf(
        "stringValueName1" to listOf("stringValue1"),
        "stringValueName2" to listOf("stringValue2"),
      ),
      partData = PartData.BinaryItem(
        provider = { ByteReadPacket("PartData".encodeToByteArray()) },
        dispose = {},
        partHeaders = Headers.Empty
      ),
      partDataList = listOf(
        PartData.BinaryItem(
          provider = { ByteReadPacket("PartData.BinaryItem".encodeToByteArray()) },
          dispose = {},
          partHeaders = Headers.Empty
        ),
        PartData.FileItem(
          provider = { ByteReadPacket("PartData.FileItem".encodeToByteArray()) },
          dispose = {},
          partHeaders = Headers.Empty
        ),
        PartData.FormItem(
          value = "PartData.FormItem",
          dispose = {},
          partHeaders = Headers.Empty
        )
      ),
      httpBody = HttpBody(listOf(true, false)),
      httpBodyList = listOf(
        HttpBody("HttpBody1"),
        HttpBody("HttpBody2")
      ),
      httpBodyMap = mapOf(
        "nodeSetKey" to HttpBody(setOf(node("nodeSetItem"))),
        "emptySetKey" to HttpBody(emptySet())
      ),
      httpBodyMultimap = listOf(
        "pairKey1" to HttpBody(true),
        "pairKey1" to HttpBody(false),
        "pairKey2" to HttpBody(true)
      )
    )

    assertEquals(1, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      subtype = "mixed",
      parts = listOf(
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"text\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"node\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"textListItem\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "{\"id\":\"nodeSetItem\",\"payload\":1,\"children\":[]}"
        ),
        TextPart(
          formFieldName = "wrapperTrue",
          contentType = JSON,
          content = "{\"value\":true}"
        ),
        TextPart(
          formFieldName = "wrapperFalse",
          contentType = JSON,
          content = "{\"value\":false}"
        ),
        TextPart(
          formFieldName = "stringValueName1",
          contentType = JSON,
          content = "\"stringValue1\""
        ),
        TextPart(
          formFieldName = "stringValueName2",
          contentType = JSON,
          content = "\"stringValue2\""
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.BinaryItem"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.FileItem"
        ),
        TextPart(
          headers = emptyList(),
          content = "PartData.FormItem"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "[true,false]"
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"HttpBody1\""
        ),
        TextPart(
          formFieldName = null,
          contentType = JSON,
          content = "\"HttpBody2\""
        ),
        TextPart(
          formFieldName = "nodeSetKey",
          contentType = JSON,
          content = "[{\"id\":\"nodeSetItem\",\"payload\":1,\"children\":[]}]"
        ),
        TextPart(
          formFieldName = "emptySetKey",
          contentType = JSON,
          content = "[]"
        ),
        TextPart(
          formFieldName = "pairKey1",
          contentType = JSON,
          content = "true"
        ),
        TextPart(
          formFieldName = "pairKey1",
          contentType = JSON,
          content = "false"
        ),
        TextPart(
          formFieldName = "pairKey2",
          contentType = JSON,
          content = "true"
        )
      )
    )
  }

  @Test fun `@Multipart null arguments`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormNullableTypes(
      text = null,
      node = null,
      textList = null,
      nodeSet = null,
      map = null,
      stringValues = null,
      partData = null,
      partDataList = null,
      httpBody = null,
      httpBodyList = null,
      httpBodyMap = null,
      httpBodyMultimap = null
    )
    service.multipartMixedMultipleParameters(
      text = null,
      node = null,
      textList = null,
      nodeSet = null,
      map = null,
      stringValues = null,
      partData = null,
      partDataList = null,
      httpBody = null,
      httpBodyList = null,
      httpBodyMap = null,
      httpBodyMultimap = null
    )

    assertEquals(2, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      "form-data",
      listOf(
        // "text"
        TextPart("f1", JSON, "null"),
        // "node"
        TextPart("f1", JSON, "null")
      )
    )

    httpLog[1].assertHasMultipartContent(
      "mixed",
      listOf(
        // "text"
        TextPart(null, JSON, "null"),
        // "node"
        TextPart(null, JSON, "null")
      )
    )
  }

  @Test fun `@Multipart collections with null values`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormNullableTypes(
      text = null,
      node = null,
      textList = listOf("a", null, "b"),
      nodeSet = setOf(null, Node("id", 1, emptyList())),
      map = mapOf(
        "key1" to null,
        "key2" to Wrapper(true),
        "key3" to null,
        "key4" to Wrapper(false)
      ),
      stringValues = parametersOf("p1" to emptyList()),
      partData = null,
      partDataList = listOf(
        null,
        PartData.FormItem(
          value = "PartData.FormItem",
          dispose = {},
          partHeaders = Headers.Empty
        ),
        null
      ),
      httpBody = null,
      httpBodyList = listOf(null, HttpBody("httpBody"), null),
      httpBodyMap = mapOf(
        "httpBodyMapKey1" to null,
        "httpBodyMapKey2" to HttpBody(null),
        "httpBodyMapKey3" to HttpBody(emptySet()),
        "httpBodyMapKey4" to HttpBody(setOf(null, Node("id", 1, emptyList()), null))
      ),
      httpBodyMultimap = listOf(
        "httpBodyMultimapKey1" to null,
        "httpBodyMultimapKey1" to HttpBody(null)
      )
    )
    service.multipartMixedMultipleParameters(
      text = null,
      node = null,
      textList = listOf("a", null, "b"),
      nodeSet = setOf(null, Node("id", 1, emptyList())),
      map = mapOf(
        "key1" to null,
        "key2" to Wrapper(true),
        "key3" to null,
        "key4" to Wrapper(false)
      ),
      stringValues = parametersOf("p1" to emptyList()),
      partData = null,
      partDataList = listOf(
        null,
        PartData.FormItem(
          value = "PartData.FormItem",
          dispose = {},
          partHeaders = Headers.Empty
        ),
        null
      ),
      httpBody = null,
      httpBodyList = listOf(null, HttpBody("httpBody"), null),
      httpBodyMap = mapOf(
        "httpBodyMapKey1" to null,
        "httpBodyMapKey2" to HttpBody(null),
        "httpBodyMapKey3" to HttpBody(emptySet()),
        "httpBodyMapKey4" to HttpBody(setOf(null, Node("id", 1, emptyList()), null))
      ),
      httpBodyMultimap = listOf(
        "httpBodyMultimapKey1" to null,
        "httpBodyMultimapKey1" to HttpBody(null)
      )
    )

    assertEquals(2, httpLog.size)

    httpLog[0].assertHasMultipartContent(
      "form-data",
      listOf(
        // "text"
        TextPart("f1", JSON, "null"),
        // "node"
        TextPart("f1", JSON, "null"),
        // "textList"
        TextPart("f2", JSON, "\"a\""),
        TextPart("f2", JSON, "null"),
        TextPart("f2", JSON, "\"b\""),
        // "nodeSet"
        TextPart("f2", JSON, "null"),
        TextPart("f2", JSON, "{\"id\":\"id\",\"payload\":1,\"children\":[]}"),
        // "map"
        TextPart("key1", JSON, "null"),
        TextPart("key2", JSON, "{\"value\":true}"),
        TextPart("key3", JSON, "null"),
        TextPart("key4", JSON, "{\"value\":false}"),
        // "partDataList"
        TextPart(
          headers = emptyList(),
          content = "PartData.FormItem"
        ),
        // "httpBodyList"
        TextPart("f4", JSON, "\"httpBody\""),
        // "httpBodyMap"
        TextPart("httpBodyMapKey2", JSON, "null"),
        TextPart("httpBodyMapKey3", JSON, "[]"),
        TextPart("httpBodyMapKey4", JSON, "[null,{\"id\":\"id\",\"payload\":1,\"children\":[]}]"),
        // "httpBodyMultimap"
        TextPart("httpBodyMultimapKey1", JSON, "null")
      )
    )

    httpLog[1].assertHasMultipartContent(
      "mixed",
      listOf(
        // "text"
        TextPart(null, JSON, "null"),
        // "node"
        TextPart(null, JSON, "null"),
        // "textList"
        TextPart(null, JSON, "\"a\""),
        TextPart(null, JSON, "null"),
        TextPart(null, JSON, "\"b\""),
        // "nodeSet"
        TextPart(null, JSON, "null"),
        TextPart(null, JSON, "{\"id\":\"id\",\"payload\":1,\"children\":[]}"),
        // "map"
        TextPart("key1", JSON, "null"),
        TextPart("key2", JSON, "{\"value\":true}"),
        TextPart("key3", JSON, "null"),
        TextPart("key4", JSON, "{\"value\":false}"),
        // "partDataList"
        TextPart(
          headers = emptyList(),
          content = "PartData.FormItem"
        ),
        // "httpBodyList"
        TextPart(null, JSON, "\"httpBody\""),
        // "httpBodyMap"
        TextPart("httpBodyMapKey2", JSON, "null"),
        TextPart("httpBodyMapKey3", JSON, "[]"),
        TextPart("httpBodyMapKey4", JSON, "[null,{\"id\":\"id\",\"payload\":1,\"children\":[]}]"),
        // "httpBodyMultimap"
        TextPart("httpBodyMultimapKey1", JSON, "null")
      )
    )
  }

  @Test fun `Empty request body when all parts are missing`() = runHttpTest {
    val service = MultipartTestService(BASE_URL, httpClient, listOf(JsonBodySerializer))

    service.multipartFormPotentiallyEmpty(null, null, null, null)
    service.multipartFormPotentiallyEmpty(null, emptyList(), emptyMap(), StringValues.Empty)
    service.multipartFormPotentiallyEmpty(null, null, null, null)
    service.multipartFormPotentiallyEmpty(null, emptyList(), emptyMap(), StringValues.Empty)

    service.multipartMixedPotentiallyEmpty(null, null, null, null)
    service.multipartMixedPotentiallyEmpty(null, emptyList(), emptyMap(), StringValues.Empty)
    service.multipartMixedPotentiallyEmpty(null, null, null, null)
    service.multipartMixedPotentiallyEmpty(null, emptyList(), emptyMap(), StringValues.Empty)

    assertHttpLogMatches(
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) },
      { hasRequestBody(ByteArray(0), null) }
    )
  }
}

private fun HttpLogEntry.assertHasMultipartContent(
  subtype: String,
  parts: List<TextPart>
) {
  assert(parts.isNotEmpty()) { "Multipart body parts must not be empty" }

  val actualContentType = requestBody.contentType
  assert(actualContentType?.contentType == "multipart" && actualContentType.contentSubtype == subtype) {
    "Multipart request Content-Type must be 'multipart/$subtype'. Found: '$actualContentType'"
  }
  actualContentType!!

  val boundaryParameters = actualContentType.parameters.filter { it.name == "boundary" }
  assert(boundaryParameters.singleOrNull()?.value?.isNotBlank() == true) {
    "Multipart request Content-Type must define exactly one 'boundary' parameter with a non-blank value. Found: '$actualContentType'"
  }

  val boundary = boundaryParameters.single().value
  val expectedRequestBodyText = buildString {
    parts.forEach {
      append("--$boundary")
      append("\r\n")
      it.headers.forEach { header ->
        append(header)
        append("\r\n")
      }
      append("\r\n")
      append(it.content)
      append("\r\n")
    }
    append("--$boundary--")
    append("\r\n")
    append("\r\n")
  }
  assertEquals(
    expectedRequestBodyText,
    runBlocking { requestBody.toByteArray().decodeToString() }
  )
}

private data class TextPart(
  val headers: List<String>,
  val content: String
)

private fun TextPart(
  formFieldName: String?,
  contentType: String,
  content: String
): TextPart {
  val headers = mutableListOf<String>().apply {
    if (formFieldName != null) {
      add("Content-Disposition: form-data; name=$formFieldName")
    }
    add("Content-Type: $contentType")
    add("Content-Length: ${content.length}")
  }
  return TextPart(headers, content)
}
