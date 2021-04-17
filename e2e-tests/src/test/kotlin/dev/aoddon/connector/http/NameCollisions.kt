package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.util.JsonBodySerializer
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.hasRequestBody
import dev.aoddon.connector.util.respondJson
import dev.aoddon.connector.util.runHttpTest
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import org.junit.Test
import kotlin.test.assertEquals

private val BASE_URL = Url("https://name/collisions/")

@Service interface NameCollisionsTestService {
  @POST("test")
  suspend fun nameCollisions(
    @Query("q") baseUrl: String,
    @Query("q") baseUrl_: String,
    @Query("q") baseUrl__: String,
    @Query("q") httpClient: String,
    @Query("q") httpClient_: String,
    @Query("q") httpClient__: String,
    @Query("q") httpBodySerializers: String,
    @Query("q") httpBodySerializers_: String,
    @Query("q") httpBodySerializers__: String,
    @Query("q") httpInterceptors: String,
    @Query("q") httpInterceptors_: String,
    @Query("q") httpInterceptors__: String,
    @Query("q") urlBuilder: String,
    @Query("q") urlBuilder_: String,
    @Query("q") urlBuilder__: String,
    @Query("q") encodedPath: String,
    @Query("q") encodedPath_: String,
    @Query("q") encodedPath__: String,
    @Query("q") parameters: String,
    @Query("q") parameters_: List<String>,
    @QueryMap parameters__: Map<String, List<String?>?>,
    @QueryName parameters___: String,
    @QueryName parameters____: Set<String>,
    @HeaderMap headers: Map<String, String>,
    @HeaderMap headers_: Map<String, String>,
    @HeaderMap headers__: Map<String, String>,
    @Body("application/json") requestBody: String,
    @Query("q") requestBody_: String,
    @Query("q") requestBody__: String,
    @Query("q") contentType: String,
    @Query("q") contentType_: String,
    @Query("q") contentType__: String,
    @Query("q") request: String,
    @Query("q") request_: String,
    @Query("q") request__: String,
    @Query("q") success: String,
    @Query("q") success_: String,
    @Query("q") success__: String,
    @Query("q") result: String,
    @Query("q") result_: String,
    @Query("q") result__: String,
  ): String

  @GET("test?")
  suspend fun nameCollisionsTrailingQuery(
    @Header("h") trailingQuery: String,
    @Header("h") trailingQuery_: String,
    @Header("h") trailingQuery__: String,
  )

  @GET("test#fragment")
  suspend fun nameCollisionsFragment(
    @Query("q") fragment: String,
    @Query("q") fragment_: String,
    @Query("q") fragment__: String,
  )

  @POST("test")
  suspend fun nameCollisionsReturningResult(
    @Query("q") deserializedBody: String,
    @Query("q") deserializedBody_: String,
    @Query("q") deserializedBody__: String,
  ): HttpResult<String>

  @POST("test")
  @FormUrlEncoded
  suspend fun formUrlEncodedNameCollisions(
    @Field("f") parametersBuilder: String,
    @Field("f") parametersBuilder_: String,
    @Field("f") parametersBuilder__: String,
  )

  @POST("test")
  @Multipart("mixed")
  suspend fun multipartNameCollisions(
    @Part("application/json", "f") subtype: String,
    @Part("application/json", "f") subtype_: String,
    @Part("application/json", "f") subtype__: String,
    @Part("application/json", "f") contentType: String,
    @Part("application/json", "f") contentType_: String,
    @Part("application/json", "f") contentType__: String,
  )
}

class NameCollisionsTest {
  @Test fun `Name collisions are tolerated`() = runHttpTest {
    val service = NameCollisionsTestService(BASE_URL, httpClient, listOf(JsonBodySerializer()))

    httpRequestHandler { respondJson("\"result\"") }

    service.nameCollisions(
      baseUrl = "value",
      baseUrl_ = "value",
      baseUrl__ = "value",
      httpClient = "value",
      httpClient_ = "value",
      httpClient__ = "value",
      httpBodySerializers = "value",
      httpBodySerializers_ = "value",
      httpBodySerializers__ = "value",
      httpInterceptors = "value",
      httpInterceptors_ = "value",
      httpInterceptors__ = "value",
      urlBuilder = "value",
      urlBuilder_ = "value",
      urlBuilder__ = "value",
      encodedPath = "value",
      encodedPath_ = "value",
      encodedPath__ = "value",
      parameters = "value",
      parameters_ = listOf("value"),
      parameters__ = mapOf("q" to listOf("value")),
      parameters___ = "queryName1",
      parameters____ = setOf("queryName2", "queryName3"),
      headers = mapOf("h1" to "value"),
      headers_ = mapOf("h2" to "value"),
      headers__ = mapOf("h3" to "value"),
      requestBody = "value",
      requestBody_ = "value",
      requestBody__ = "value",
      contentType = "value",
      contentType_ = "value",
      contentType__ = "value",
      request = "value",
      request_ = "value",
      request__ = "value",
      success = "value",
      success_ = "value",
      success__ = "value",
      result = "value",
      result_ = "value",
      result__ = "value",
    )
    service.nameCollisionsTrailingQuery("value", "value", "value")
    service.nameCollisionsFragment("value", "value", "value")
    service.nameCollisionsReturningResult("value", "value", "value")
      .let { result ->
        result as HttpResponse.Success<*>
        assertEquals("result", result.body)
      }
    service.formUrlEncodedNameCollisions("value", "value", "value")
    service.multipartNameCollisions("value", "value", "value", "value", "value", "value")

    assertHttpLogMatches(
      {
        hasUrl("${BASE_URL}test?q=value${"&q=value".repeat(34)}&queryName1&queryName2&queryName3")
        hasRequestHeaders(
          headersOf(
            "h1" to listOf("value"),
            "h2" to listOf("value"),
            "h3" to listOf("value"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
        hasRequestBody("\"value\"", ContentType.Application.Json)
      },
      { // trailing query
        hasUrl("${BASE_URL}test?")
        hasRequestHeaders(
          headersOf(
            "h" to listOf("value", "value", "value"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      { // fragment
        hasUrl("${BASE_URL}test?q=value&q=value&q=value#fragment")
      },
      { // returning result
        hasUrl("${BASE_URL}test?q=value&q=value&q=value")
      },
      { // form URL encoded
        hasUrl("${BASE_URL}test")
        hasRequestBody(
          "f=value&f=value&f=value",
          ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8)
        )
      },
      { // multipart
        hasUrl("${BASE_URL}test")
      }
    )

    httpLog.last().assertHasMultipartContent(
      "mixed",
      listOf(
        TextPart("f", "application/json", "\"value\""),
        TextPart("f", "application/json", "\"value\""),
        TextPart("f", "application/json", "\"value\""),
        TextPart("f", "application/json", "\"value\""),
        TextPart("f", "application/json", "\"value\""),
        TextPart("f", "application/json", "\"value\"")
      )
    )
  }
}
