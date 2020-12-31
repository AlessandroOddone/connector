package connector.http

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface HttpBodySerializer {
  public fun canWrite(contentType: ContentType): Boolean

  public fun canRead(contentType: ContentType?): Boolean

  public suspend fun <T> write(
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
