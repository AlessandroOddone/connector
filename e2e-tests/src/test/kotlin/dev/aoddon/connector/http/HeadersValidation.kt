package dev.aoddon.connector.http

import com.tschuchort.compiletesting.SourceFile
import dev.aoddon.connector.util.runTestCompilation
import org.junit.Test

class HeadersValidationTest {
  @Test fun `Static Content-Type header is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post")
        @Headers("Content-Type: application/json")
        suspend fun post()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Content-Type header cannot be defined via @Headers, but only via @Body." atLine 8)
    }
  }

  @Test fun `Dynamic Content-Type header is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Header("Content-Type") contentType: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Content-Type header cannot be defined via @Header." atLine 7)
    }
  }

  @Test fun `Static Content-Length header is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post")
        @Headers("Content-Length: 0")
        suspend fun post()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Content-Length header cannot be defined via @Headers." atLine 8)
    }
  }

  @Test fun `Dynamic Content-Length header is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Header("Content-Length") contentLength: Int)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Content-Length header cannot be defined via @Header." atLine 7)
    }
  }

  @Test fun `Invalid static header format`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post")
        @Headers("invalidFormat")
        suspend fun post()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Headers values must be formatted as '<name>: <value>'. Found: 'invalidFormat'." atLine 8)
    }
  }

  @Test fun `@HeaderMap type must be either Map or StringValues`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @GET("get") 
        suspend fun post(
          @HeaderMap("h") valid1: Map<String, String>,
          @HeaderMap("h") valid2: StringValues,
          @HeaderMap("h") notValid: Pair<String, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@HeaderMap parameter must be of a supported Map, StringValues, or Iterable (of key-value pairs) type. " +
          "Found: 'kotlin.Pair'." atLine 13
      )
    }
  }

  @Test fun `@HeaderMap keys must be strings`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @GET("get") 
        suspend fun post(
          @HeaderMap("h") valid: Map<String, String>,
          @HeaderMap("h") notValid1: Map<Int, String>,
          @HeaderMap("h") notValid2: Map<StringWrapper, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@HeaderMap keys must be of type 'kotlin.String'. Found: 'kotlin.Int'." atLine 13,
        "@HeaderMap keys must be of type 'kotlin.String'. Found: 'test.StringWrapper'." atLine 14,
      )
    }
  }

  @Test fun `@HeaderMap keys must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      @Service interface TestApi {
        @GET("get") 
        suspend fun post(
          @HeaderMap("h") valid: Map<String, String>,
          @HeaderMap("h") notValid: Map<String?, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@HeaderMap keys must be non-nullable." atLine 11
      )
    }
  }

  @Test fun `Format errors`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      @Service interface TestApi {
        @GET("get") 
        @Headers("has space:value")
        @Headers(
          "has,delimiter:value",
          "name:inv\nalid"
        )
        suspend fun post(
          @Header("a\tb") h1: String,
          @Header("has;delimiter") h2: String,
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Header name contains the illegal character ' ' (code 32)" atLine 9,
        "Header name contains the illegal character ',' (code 44)" atLine 10,
        "Header value contains the illegal character '\\n' (code 10)" atLine 10,
        "Header name contains the illegal character '\\t' (code 9)" atLine 15,
        "Header name contains the illegal character ';' (code 59)" atLine 16
      )
    }
  }
}
