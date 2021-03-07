package dev.aoddon.connector.http

import com.tschuchort.compiletesting.SourceFile
import dev.aoddon.connector.util.runTestCompilation
import org.junit.Test

class MethodsValidationTest {
  @Test fun `Multiple HTTP methods are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") @HEAD("head") suspend fun getOrHead()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Multiple HTTP method annotations are not allowed. Found: GET, HEAD" atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @GET requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(@Body("*/*") body: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in GET requests." atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @DELETE requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @DELETE("delete") suspend fun delete(@Body("*/*") body: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in DELETE requests." atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @HEAD requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @HEAD("head") suspend fun head(@Body("*/*") body: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in HEAD requests." atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @OPTIONS requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @OPTIONS("options") suspend fun options(@Body("*/*") body: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in OPTIONS requests." atLine 7)
    }
  }

  @Test fun `@HEAD invalid success body types`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      @Service interface TestApi {
        @HEAD("head") suspend fun head1(): String
        @HEAD("head") suspend fun head2(): HttpResult<String>
        @HEAD("head") suspend fun head3(): HttpResponse<String>
        @HEAD("head") suspend fun head4(): HttpResponse.Success<String>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type." atLine 8,
        "@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type." atLine 9,
        "@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type." atLine 10,
        "@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type." atLine 11,
      )
    }
  }
}
