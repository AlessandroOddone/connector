package dev.aoddon.connector.http.multipart

import io.ktor.client.utils.EmptyContent
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessage
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.writeFully
import io.ktor.utils.io.core.writeText
import io.ktor.utils.io.write
import io.ktor.utils.io.writeFully
import kotlin.random.Random

public fun MultipartOutgoingContent(
  block: MultipartOutgoingContentBuilder.() -> Unit
): OutgoingContent {
  val builder = MultipartOutgoingContentBuilder()
  builder.apply(block)
  return if (builder.parts.isEmpty()) EmptyContent else builder.build()
}

public class MultipartOutgoingContentBuilder internal constructor() {
  public var subtype: String = "mixed"
  internal val parts = mutableListOf<Part>()

  public fun appendPart(partData: PartData) {
    parts.add(partData.toPart())
  }

  public fun appendPart(content: OutgoingContent) {
    if (content !is OutgoingContent.NoContent) {
      parts.add(createPart(content))
    }
  }

  public fun appendFormPart(name: String, content: OutgoingContent) {
    if (content !is OutgoingContent.NoContent) {
      parts.add(createPart(content, formFieldName = name))
    }
  }

  internal fun build(): MultipartOutgoingContent {
    return MultipartOutgoingContent(
      subtype = subtype,
      parts = parts
    )
  }
}

internal class MultipartOutgoingContent internal constructor(
  subtype: String,
  private val parts: List<Part>
) : OutgoingContent.WriteChannelContent() {

  init {
    check(parts.isNotEmpty()) {
      "MultipartOutgoingContent must have at least one part."
    }
  }

  private val boundary: String = generateBoundary()
  override val contentType: ContentType = ContentType("multipart", subtype).withParameter("boundary", boundary)

  private val headerBytes = parts.map { part ->
    val headersBuilder = BytePacketBuilder()
    for ((key, values) in part.headers.entries()) {
      headersBuilder.writeText("$key: ${values.joinToString("; ")}")
      headersBuilder.writeFully(carriageReturnNewLineBytes)
    }
    headersBuilder.build().readBytes()
  }

  private val boundaryBytes = "--$boundary\r\n".toByteArray()
  private val lastBoundaryBytes = "--$boundary--\r\n\r\n".toByteArray()

  override suspend fun writeTo(channel: ByteWriteChannel) {
    try {
      parts.forEachIndexed { index, part ->
        channel.writeFully(boundaryBytes)
        channel.writeFully(headerBytes[index])
        channel.writeFully(carriageReturnNewLineBytes)
        part.bodyWriter(channel)
        channel.writeFully(carriageReturnNewLineBytes)
      }
      channel.writeFully(lastBoundaryBytes)
    } catch (cause: Throwable) {
      channel.close(cause)
    } finally {
      channel.close()
    }
  }

  override val contentLength: Long?

  private val bodyOverheadSize = lastBoundaryBytes.size
  private val partOverheadSize = carriageReturnNewLineBytes.size * 2 + boundaryBytes.size

  init {
    var totalContentLength: Long? = 0
    for (index in parts.indices) {
      val part = parts[index]
      val partBodySize = part.contentLength()
      if (partBodySize == null) {
        totalContentLength = null
        break
      }
      totalContentLength = totalContentLength
        ?.plus(headerBytes[index].size)
        ?.plus(partBodySize)
        ?.plus(partOverheadSize)
    }
    contentLength = totalContentLength?.plus(bodyOverheadSize)
  }
}

internal class Part(
  override val headers: Headers,
  val bodyWriter: suspend ByteWriteChannel.() -> Unit
) : HttpMessage

private fun generateBoundary(): String = buildString {
  repeat(32) {
    append(Random.nextInt().toString(16))
  }
}.take(70)

private fun createPart(body: OutgoingContent, formFieldName: String? = null): Part {
  val headers = buildHeaders {
    formFieldName?.let {
      append(
        HttpHeaders.ContentDisposition,
        ContentDisposition("form-data").withParameter("name", formFieldName).toString()
      )
    }
    body.contentType?.let { append(HttpHeaders.ContentType, it.toString()) }
    body.contentLength?.let { append(HttpHeaders.ContentLength, it.toString()) }
  }
  return Part(
    headers = headers,
    bodyWriter = when (body) {
      is OutgoingContent.ByteArrayContent -> {
        { writeFully(body.bytes()) }
      }
      is OutgoingContent.ReadChannelContent -> {
        { body.readFrom().copyTo(this) }
      }
      is OutgoingContent.WriteChannelContent -> {
        { body.writeTo(this) }
      }
      is OutgoingContent.NoContent, is OutgoingContent.ProtocolUpgrade -> {
        { /* write nothing */ }
      }
    }
  )
}

private fun PartData.toPart(): Part {
  return Part(
    headers = headers,
    bodyWriter = when (this) {
      is PartData.FormItem -> {
        { writeFully(value.toByteArray()) }
      }
      is PartData.FileItem -> {
        { provider().copyTo(this) }
      }
      is PartData.BinaryItem -> {
        { provider().copyTo(this) }
      }
    }
  )
}

private suspend fun Input.copyTo(channel: ByteWriteChannel) {
  if (this is ByteReadPacket) {
    channel.writePacket(this)
    return
  }

  while (!this@copyTo.endOfInput) {
    channel.write { freeSpace, startOffset, endExclusive ->
      this@copyTo.readAvailable(freeSpace, startOffset, endExclusive - startOffset).toInt()
    }
  }
}

private val carriageReturnNewLineBytes = "\r\n".toByteArray()
