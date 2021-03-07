package dev.aoddon.connector.http

import com.tschuchort.compiletesting.SourceFile
import dev.aoddon.connector.util.runTestCompilation
import org.junit.Test

class UrlsValidationTest {
  @Test fun `Dynamic query parameters in the relative URL are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get?param1={p1}&param2={p2}") suspend fun get(
          @Query("p1") p1: String,
          @Query("p2") p2: String
        ): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Dynamic query parameters must be provided via @Query function parameters. " +
          "Found in the query string: {p1}, {p2}" atLine 7
      )
    }
  }

  @Test fun `@Path name must match a parameter in the URL template`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("path") suspend fun get(@Path("id") id: String): String
        @HTTP("CUSTOM", "path") suspend fun custom(@Path("p") p: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@GET URL does not define a dynamic path parameter matching 'id'." atLine 7,
        "@HTTP URL does not define a dynamic path parameter matching 'p'." atLine 8
      )
    }
  }

  @Test fun `@Path is not allowed when the URL is provided via @URL`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET suspend fun get(@URL url: String, @Path("id") id: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@GET URL does not define a dynamic path parameter matching 'id'." atLine 7)
    }
  }

  @Test fun `Multiple @URL are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET suspend fun get(@URL url1: String, @URL url2: String, @URL url3: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Multiple @URL parameters are not allowed." atLine 7)
    }
  }

  @Test fun `Not providing a URL, either statically or dynamically, is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET suspend fun get(): String
        @HTTP("CUSTOM") suspend fun custom()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "URL must be provided either by @GET or via @URL." atLine 7,
        "URL must be provided either by @HTTP or via @URL." atLine 8
      )
    }
  }

  @Test fun `Providing a URL both statically and dynamically is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(@URL url: String): String
        @HTTP("CUSTOM", "custom") suspend fun custom(@URL url: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "URL cannot be provided by both @GET and @URL." atLine 7,
        "URL cannot be provided by both @HTTP and @URL." atLine 8
      )
    }
  }

  @Test fun `Multiple @Path with the same name are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get/{id}/{id}") suspend fun get(@Path("id") id1: String, @Path("id") id2: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Path 'id' is defined 2 times, but at most once is allowed." atLine 7)
    }
  }

  @Test fun `@Path must be provided for all path parameters in the relative URL`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get/{id}") suspend fun get(): String
        @HTTP("CUSTOM", "custom/{p}") suspend fun custom()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Missing @Path for 'id', which is defined in the @GET URL." atLine 7,
        "Missing @Path for 'p', which is defined in the @HTTP URL." atLine 8
      )
    }
  }

  @Test fun `Invalid @Path parameter name format`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("get/{1234}") suspend fun get(
          @Path("1234") p: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid @Path parameter name '1234'. Expected format: [a-zA-Z][a-zA-Z0-9_-]*" atLine 8)
    }
  }

  @Test fun `Nullable type is not allowed for @Path parameter`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get/{id}") suspend fun get(
          @Path("id") id: String?
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Nullable types are not allowed for @Path parameters." atLine 8)
    }
  }

  @Test fun `Nullable type is not allowed for @URL parameter`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET suspend fun get(
          @URL url: String?
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Nullable types are not allowed for @URL parameters." atLine 8)
    }
  }

  @Test fun `Invalid URL protocol`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("invalidProtocol://a/b") suspend fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("URL protocol must be HTTP or HTTPS. Found: 'invalidProtocol'." atLine 7)
    }
  }

  @Test fun `@QueryMap type must be either Map or StringValues`() {
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
          @QueryMap("q") valid1: Map<String, String>,
          @QueryMap("q") valid2: StringValues,
          @QueryMap("q") notValid: Pair<String, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@QueryMap parameter type must be either 'kotlin.collections.Map' or 'io.ktor.util.StringValues'. " +
          "Found: 'kotlin.Pair'." atLine 13
      )
    }
  }

  @Test fun `@QueryMap keys must be strings`() {
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
          @QueryMap("q") valid: Map<String, String>,
          @QueryMap("q") notValid1: Map<Int, String>,
          @QueryMap("q") notValid2: Map<StringWrapper, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@QueryMap keys must be of type 'kotlin.String'. Found: 'kotlin.Int'." atLine 13,
        "@QueryMap keys must be of type 'kotlin.String'. Found: 'test.StringWrapper'." atLine 14,
      )
    }
  }

  @Test fun `@QueryMap keys must be non-nullable`() {
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
          @QueryMap("q") valid: Map<String, String>,
          @QueryMap("q") notValid: Map<String?, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@QueryMap keys must be non-nullable." atLine 11
      )
    }
  }
}
