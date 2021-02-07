package dev.aoddon.connector.codegen.util

import java.lang.StringBuilder

internal fun String.escape(): String {
  val stringBuilder = StringBuilder()
  forEach { char ->
    stringBuilder.append(
      when (char) {
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\'' -> "\\\'"
        '\"' -> "\\\""
        '\\' -> "\\\\"
        '$' -> "\\$"
        else -> char
      }
    )
  }
  return stringBuilder.toString()
}

internal fun String.noBreakingSpaces() = replace(' ', '·')
