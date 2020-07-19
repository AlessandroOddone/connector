package sample

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    val api = SampleApi(BASE_URL, httpClient)
    GlobalScope.launch(Dispatchers.Unconfined) {
        api.get()
        api.post(
            userId = "1234",
            postId = "9876",
            contentType = "application/json",
            header1 = "h1",
            header2 = "h2",
            query1 = "q1",
            query2 = "q2",
            body = Payload("message")
        )
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
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
    engine {
        addHandler {
            val responseHeaders = headersOf(
                "Content-Type" to listOf(ContentType.Application.Json.toString())
            )
            respond("{\"text\":\"abc\"}", headers = responseHeaders)
        }
    }
}

private val BASE_URL = Url("https://sample/")
