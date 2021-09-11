package dev.aoddon.connector.util

import dev.aoddon.connector.http.HttpBodySerializer
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

class JsonBodySerializer(
  private val contentTypeMatcher: (ContentType?) -> Boolean = { contentType ->
    contentType?.match(ContentType.Application.Json) == true
  }
) : HttpBodySerializer {
  private val json = Json.Default

  override fun canWrite(contentType: ContentType?) = contentTypeMatcher(contentType)

  override fun canRead(contentType: ContentType?) = contentTypeMatcher(contentType)

  override fun <T> write(
    serializationStrategy: SerializationStrategy<T>,
    body: T,
    contentType: ContentType?
  ): OutgoingContent {
    return TextContent(
      text = json.encodeToString(serializationStrategy, body),
      contentType = contentType ?: ContentType.Application.Json
    )
  }

  override suspend fun <T> read(
    deserializationStrategy: DeserializationStrategy<T>,
    body: ByteReadChannel,
    contentType: ContentType?
  ): T {
    val text = body.readRemaining().readText()
    return json.decodeFromString(deserializationStrategy, text)
  }
}
