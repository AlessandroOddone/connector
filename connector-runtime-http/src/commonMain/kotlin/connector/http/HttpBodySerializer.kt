package connector.http

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.core.Input
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

interface HttpBodySerializer {
    fun canWrite(contentType: ContentType): Boolean

    fun canRead(contentType: ContentType?): Boolean

    suspend fun <T> write(
        serializationStrategy: SerializationStrategy<T>,
        body: T,
        contentType: ContentType
    ): OutgoingContent

    suspend fun <T> read(
        deserializationStrategy: DeserializationStrategy<T>,
        body: Input,
        contentType: ContentType?
    ): T
}
