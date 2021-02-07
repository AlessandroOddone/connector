package dev.aoddon.connector.http

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface HttpContentSerializer {
  public fun canWrite(contentType: ContentType): Boolean

  public fun canRead(contentType: ContentType?): Boolean

  public fun <T> write(
    serializationStrategy: SerializationStrategy<T>,
    content: T,
    contentType: ContentType
  ): OutgoingContent

  public suspend fun <T> read(
    deserializationStrategy: DeserializationStrategy<T>,
    content: ByteReadChannel,
    contentType: ContentType?
  ): T
}
