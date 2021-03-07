package dev.aoddon.connector.util

import kotlinx.serialization.Serializable

@Serializable
data class Node(
  val id: String,
  val payload: Int,
  val children: List<Node>
)

@Serializable
data class Wrapper<T>(val value: T) {
  override fun toString() = value.toString()
}
