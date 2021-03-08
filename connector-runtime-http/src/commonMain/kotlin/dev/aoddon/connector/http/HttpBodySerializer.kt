package dev.aoddon.connector.http

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface HttpBodySerializer {
  public fun canWrite(contentType: ContentType): Boolean

  public fun canRead(contentType: ContentType?): Boolean

  public fun <T> write(
    serializationStrategy: SerializationStrategy<T>,
    body: T,
    contentType: ContentType
  ): OutgoingContent

  public suspend fun <T> read(
    deserializationStrategy: DeserializationStrategy<T>,
    body: ByteReadChannel,
    contentType: ContentType?
  ): T
}
