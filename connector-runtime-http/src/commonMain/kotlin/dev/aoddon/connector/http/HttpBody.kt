package dev.aoddon.connector.http

public class HttpBody<out T>(public val value: T) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return other is HttpBody<*> && value == other.value
  }

  override fun hashCode(): Int = value?.hashCode() ?: 0

  override fun toString(): String = "HttpBody($value)"
}
