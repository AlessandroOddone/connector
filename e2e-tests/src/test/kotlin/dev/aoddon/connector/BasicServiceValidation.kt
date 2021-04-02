package dev.aoddon.connector

import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import dev.aoddon.connector.http.EXPECTED_RETURN_TYPES_MESSAGE_PART
import dev.aoddon.connector.util.runTestCompilation
import org.junit.Test

class ServiceValidation {
  @Test fun `@Service abstract class target is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
          package test

          import dev.aoddon.connector.*
          import dev.aoddon.connector.http.*

          @Service abstract class TestApi {
            @GET("get") abstract suspend fun get(): String
          }
          """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Service target must be a top-level interface." atLine 6)
    }
  }

  @Test fun `@Service target must be top-level`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
        package test

        import dev.aoddon.connector.*
        import dev.aoddon.connector.http.*

        object TopLevel {
          @Service interface TestApi {
            @GET("get") suspend fun get(): String
          }
        }
        """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Service target must be a top-level interface." atLine 7)
    }
  }

  @Test fun `Supertypes are not allowed for @Service interfaces`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
        package test

        import dev.aoddon.connector.*
        import dev.aoddon.connector.http.*

        interface SuperType

        @Service interface TestApi : SuperType {
          @GET("get") suspend fun get(): String
        }
        """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Service interfaces cannot have supertypes." atLine 8)
    }
  }

  @Test fun `Type parameters are not allowed for @Service interfaces`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
        package test

        import dev.aoddon.connector.*
        import dev.aoddon.connector.http.*

        @Service interface TestApi<T> {
          @GET("get") suspend fun get(): String
        }
        """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Service interfaces cannot have type parameters." atLine 6)
    }
  }

  @Test fun `Abstract property is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        val s: String

        @GET("get") suspend fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Properties are not allowed in @Service interfaces." atLine 7)
    }
  }

  @Test fun `Property with getter is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        val s: String get() = ""

        @GET("get") suspend fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Properties are not allowed in @Service interfaces." atLine 7)
    }
  }

  @Test fun `Function missing HTTP method is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        suspend fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "All functions in @Service interfaces must be annotated with an HTTP method." atLine 7
      )
    }
  }

  @Test fun `Non-suspension function is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "All functions in @Service interfaces must be suspension functions." atLine 7
      )
    }
  }

  @Test fun `Function with a default implementation is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): String = ""
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Functions with a body are not allowed in @Service interfaces." atLine 7)
    }
  }

  @Test fun `Function with type parameters is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun <T> get(): T
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Functions with type parameters are not allowed in @Service interfaces." atLine 7,
        "Invalid return type: 'test.TestApi.get.T'. $EXPECTED_RETURN_TYPES_MESSAGE_PART" atLine 7,
      )
    }
  }

  @Test fun `Function parameters must be annotated`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(notAnnotated: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "All parameters in a @Service function must have an appropriate Connector annotation." atLine 7
      )
    }
  }

  @Test fun `Multiple annotations on the same function parameter are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get/{id}") suspend fun get(@Path("id") @Query("id") id: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Multiple Connector annotations are not allowed on the same parameter." atLine 7
      )
    }
  }
}
