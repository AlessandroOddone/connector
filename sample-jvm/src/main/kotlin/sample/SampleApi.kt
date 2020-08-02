package sample

import kotlinx.serialization.Serializable
import segment.API
import segment.http.Body
import segment.http.GET
import segment.http.Header
import segment.http.Headers
import segment.http.POST
import segment.http.Path
import segment.http.Query

@API interface SampleApi {
    @GET("get") suspend fun get(): Payload

    @POST("api/{userId}/posts/{postId}?locale=en_US")
    @Headers(
        "Content-Type: application/json",
        "Accept-Charset: utf-8"
    )
    suspend fun post(
        @Path("userId") userId: String,
        @Path("postId") postId: String,
        @Header("h1") header1: String,
        @Header("h2") header2: String,
        @Query("q1") query1: String,
        @Query("q2") query2: String,
        @Body body: Payload
    ): Payload
}

@Serializable data class Payload(val text: String)