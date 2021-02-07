package dev.aoddon.connector.util

import dev.aoddon.connector.http.HttpContentSerializer
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

object JsonContentSerializer : HttpContentSerializer {
  private val json = Json.Default

  override fun canWrite(contentType: ContentType) = contentType == ContentType.Application.Json

  override fun canRead(contentType: ContentType?) = contentType == ContentType.Application.Json

  override fun <T> write(
    serializationStrategy: SerializationStrategy<T>,
    content: T,
    contentType: ContentType
  ): OutgoingContent {
    return TextContent(json.encodeToString(serializationStrategy, content), contentType)
  }

  override suspend fun <T> read(
    deserializationStrategy: DeserializationStrategy<T>,
    content: ByteReadChannel,
    contentType: ContentType?
  ): T {
    val text = content.readRemaining().readText()
    return json.decodeFromString(deserializationStrategy, text)
  }
}
