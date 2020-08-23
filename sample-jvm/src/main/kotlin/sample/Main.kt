package sample

import connector.http.HttpBodySerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.ContentTypeMatcher
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val BASE_URL = Url("https://sample/")

fun main() {
    val service: SampleService = SampleService(BASE_URL, httpClient, listOf(jsonBodySerializer))
    GlobalScope.launch(Dispatchers.Unconfined) {
        service.get()
        service.post(
            userId = "1234",
            postId = "9876",
            baseUrl = "h1",
            header2 = Wrapper("h2"),
            query1 = "q1",
            query2 = "q2",
            body = Wrapper(listOf("message 1", "message 2"))
        )
        service.post(
            mapOf(
                "a" to listOf(
                    listOf(
                        buildJsonObject {
                            put("1", JsonPrimitive("message 1"))
                        },
                        buildJsonObject {
                            put("2", JsonPrimitive("message 2"))
                        }
                    ),
                    listOf(
                        buildJsonObject {
                            put("a", JsonPrimitive("message a"))
                        },
                        buildJsonObject {
                            put("b", JsonPrimitive("message b"))
                        }
                    )
                ),
                "b" to emptyList()
            )
        )
        service.postIgnoringResponseBody(null)
        service.postReturningNullableUnit(null)
    }
}

internal val httpClient = HttpClient(MockEngine) {
    install(Logging) {
        level = LogLevel.ALL
        logger = object : Logger {
            override fun log(message: String) {
                println(message)
            }
        }
    }
    engine {
        addHandler { request ->
            val responseHeaders = headersOf(
                "Content-Type" to listOf(ContentType.Application.Json.toString())
            )
            if (request.url.toString().contains("nullable", ignoreCase = true)) {
                respond(ByteReadChannel.Empty)
            } else {
                respond("{\"text\":\"abc\"}", headers = responseHeaders)
            }
        }
    }
}

private val jsonBodySerializer = object : HttpBodySerializer {
    private val json = Json.Default

    override fun canWrite(contentType: ContentType) = JsonContentTypeMatcher.match(contentType)

    override fun canRead(contentType: ContentType?): Boolean {
        contentType ?: return false
        return JsonContentTypeMatcher.match(contentType)
    }

    override suspend fun <T> write(
        serializationStrategy: SerializationStrategy<T>,
        body: T,
        contentType: ContentType
    ): OutgoingContent {
        return TextContent(json.encodeToString(serializationStrategy, body), contentType)
    }

    override suspend fun <T> read(
        deserializationStrategy: DeserializationStrategy<T>,
        body: Input,
        contentType: ContentType?
    ): T {
        val text = body.readText()
        return json.decodeFromString(deserializationStrategy, text)
    }
}

private object JsonContentTypeMatcher : ContentTypeMatcher {
    override fun match(contentType: ContentType): Boolean {
        if (ContentType.Application.Json.match(contentType)) {
            return true
        }
        val value = contentType.withoutParameters().toString()
        return value.startsWith("application/") && value.endsWith("+json")
    }
}
