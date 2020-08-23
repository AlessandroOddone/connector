package sample

import connector.Service
import connector.http.Body
import connector.http.GET
import connector.http.Header
import connector.http.Headers
import connector.http.JsonBody
import connector.http.POST
import connector.http.Path
import connector.http.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

@Service
interface SampleService {
    @GET("get")
    suspend fun get(): Payload

    @GET("getNullable")
    suspend fun getNullable(): Payload?

    @POST("api/{userId}/posts/{postId}?locale=en_US")
    @Headers("Accept-Charset: utf-8")
    suspend fun post(
        @Path("userId") userId: String,
        @Path("postId") postId: String,
        @Header("h1") baseUrl: String,
        @Header("h2") header2: Wrapper<String>?,
        @Query("q1") query1: String,
        @Query("q2") query2: String,
        @JsonBody body: Wrapper<List<String>>?
    ): Payload

    @POST("post")
    suspend fun post(
        @Body("application/json; charset=utf-8") body: Map<String, List<List<JsonObject>>>
    ): JsonElement

    @POST("post")
    suspend fun postIgnoringResponseBody(@JsonBody body: String?)

    @POST("post")
    suspend fun postReturningNullableUnit(@JsonBody body: String?): Unit?
}

@Serializable
data class Payload(val text: String)

@Serializable
data class Wrapper<T>(val value: T) {
    override fun toString() = value.toString()
}
