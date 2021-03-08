package dev.aoddon.connector.http

import com.tschuchort.compiletesting.SourceFile
import dev.aoddon.connector.util.runTestCompilation
import org.junit.Test

class ContentValidationTest {
  @Test fun `Multiple @Body are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body1: String,
          @Body("*/*") body2: String, 
          @Body("*/*") body3: String
        ): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Multiple @Body parameters are not allowed." atLine 7)
    }
  }

  @Test fun `Invalid Content-Type format`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("application_json") body: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid Content-Type format: 'application_json'." atLine 8)
    }
  }

  @Test fun `Nullable Unit is not a valid return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): Unit?
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Nullable 'kotlin.Unit' is not allowed as the return type. Must be non-null." atLine 7)
    }
  }

  @Test fun `Unknown @Body type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: TypeThatDoesNotExist)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid @Body type: '<Error>'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 7)
    }
  }

  @Test fun `Unknown @Part type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: TypeThatDoesNotExist)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid @Part type: '<Error>'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9)
    }
  }

  @Test fun `Unknown return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): TypeThatDoesNotExist
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid return type: '<Error>'. $EXPECTED_RETURN_TYPES_MESSAGE_PART" atLine 7)
    }
  }

  @Test fun `Unit is not a valid @Body type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'kotlin.Unit'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 7
      )
    }
  }

  @Test fun `Unit is not a valid @Part type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Part type: 'kotlin.Unit'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @PartIterable type argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: List<Unit>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @PartMap value type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, Unit>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unknown @Body generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<TypeThatDoesNotExist>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 7
      )
    }
  }

  @Test fun `Unknown @Part generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi { 
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: List<TypeThatDoesNotExist>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unknown @PartIterable type argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi { 
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Set<TypeThatDoesNotExist>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unknown @PartMap value type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi { 
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, TypeThatDoesNotExist>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unknown return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): Map<
          TypeThatDoesNotExist, 
          OtherTypeThatDoesNotExist>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 8,
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @Body generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<Unit>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `Unit is not a valid @Part generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: List<Unit>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @PartIterable part type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Collection<List<Unit>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @PartMap value type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, List<Unit>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<Unit>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `Star is not a valid @Body generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<*>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 7
      )
    }
  }

  @Test fun `Star is not a valid @Part generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: List<*>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Star is not a valid @PartIterable type argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Iterable<*>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Star is not a valid @PartMap value type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, *>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Star is not a valid @PartIterable part type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Collection<List<*>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Star is not a valid @PartMap value type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, List<*>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Star is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<*>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody is not a valid @Body generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<HttpBody<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody is not a valid @Part generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: List<HttpBody<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpBody is not a valid @PartIterable part type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Iterable<List<HttpBody<String>>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpBody is not a valid @PartMap value type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, List<HttpBody<String>>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpBody is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpBody<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResult is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResult<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResult'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResponse is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResponse<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResponseSuccess is not a valid return type generic argument`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResponse.Success<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse.Success'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody with Unit type argument is not allowed as @Body`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpBody<Unit>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 8
      )
    }
  }

  @Test fun `HttpBody with non-@Serializable type argument is not allowed as @Body`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("application/json") payload: Payload
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'test.Payload'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 10
      )
    }
  }

  @Test fun `HttpResult @Body is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResult<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'dev.aoddon.connector.http.HttpResult'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpResult @Part is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: HttpResult<String>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Part type: 'dev.aoddon.connector.http.HttpResult'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `HttpResult @PartIterable type argument is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Collection<HttpResult<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResult'. " +
          EXPECTED_PART_TYPES_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResult @PartMap value type is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, HttpResult<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResult'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `HttpResponse @Body is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResponse<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'dev.aoddon.connector.http.HttpResponse'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpResponse @Part is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: HttpResponse<String>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Part type: 'dev.aoddon.connector.http.HttpResponse'. $EXPECTED_PART_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `HttpResponse @PartIterable type argument is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Collection<HttpResponse<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse'. " +
          EXPECTED_PART_TYPES_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponse @PartMap value type is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, HttpResponse<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `HttpResponseSuccess @Body is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResponse.Success<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'dev.aoddon.connector.http.HttpResponse.Success'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpResponseSuccess @Part is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@Part("*/*", "name") part: HttpResponse.Success<String>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Part type: 'dev.aoddon.connector.http.HttpResponse.Success'. " +
          EXPECTED_PART_TYPES_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponseSuccess @PartIterable type argument is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartIterable("*/*", "p") parts: Iterable<HttpResponse.Success<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse.Success'. " +
          EXPECTED_PART_TYPES_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponseSuccess @PartMap value type is not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("multipart")
        @Multipart
        suspend fun multipart(@PartMap("*/*") map: Map<String, HttpResponse.Success<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'dev.aoddon.connector.http.HttpResponse.Success'. " +
          EXPECTED_BODY_TYPES_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpBody with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpBody<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResult with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResult<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDE_UNIT_INCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponse with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDE_UNIT_INCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponseSuccess with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse.Success<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDE_UNIT_INCLUDE_HTTP_CONTENT_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `Nullable Unit is not allowed as type argument of HttpResult`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResult<Unit?>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Nullable 'kotlin.Unit' type argument is not allowed. Must be non-null." atLine 9
      )
    }
  }

  @Test fun `Nullable Unit is not allowed as type argument of HttpResponse`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse<Unit?>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Nullable 'kotlin.Unit' type argument is not allowed. Must be non-null." atLine 9
      )
    }
  }

  @Test fun `Nullable Unit is not allowed as type argument of HttpResponseSuccess`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse.Success<Unit?>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Nullable 'kotlin.Unit' type argument is not allowed. Must be non-null." atLine 9)
    }
  }

  @Test fun `Multiple @FormUrlEncoded are not allowed on a function`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        @FormUrlEncoded
        suspend fun b(
          @Field("f") field: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FormUrlEncoded is allowed at most once on a function." atLine 10,
      )
    }
  }

  @Test fun `Multiple @Multipart are not allowed on a function`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        @Multipart
        suspend fun b(
          @Part("*/*", "p") part : String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Multipart is allowed at most once on a function." atLine 10,
      )
    }
  }

  @Test fun `@FormUrlEncoded and @Multipart are not allowed together on the same function`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        @Multipart
        suspend fun a(
          @Field("f") field: String,
          @Part("*/*", "p") part : String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FormUrlEncoded and @Multipart are not allowed together on the same function." atLine 10,
      )
    }
  }

  @Test fun `@FormUrlEncoded is only allowed for HTTP methods that allow a request body`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @PATCH("path") 
        @FormUrlEncoded
        suspend fun patch(
          @Field("f") field: String,
        )
       
        @POST("path") 
        @FormUrlEncoded
        suspend fun post(
          @Field("f") field: String,
        )
        
        @PUT("path") 
        @FormUrlEncoded
        suspend fun put(
          @Field("f") field: String,
        )
        
        @HTTP("GET", "path") 
        @FormUrlEncoded
        suspend fun customGet(
          @Field("f") field: String,
        )

        @DELETE("path") 
        @FormUrlEncoded
        suspend fun delete(
          @Field("f") field: String,
        )

        @GET("path") 
        @FormUrlEncoded
        suspend fun get(
          @Field("f") field: String,
        )

        @HEAD("path") 
        @FormUrlEncoded
        suspend fun head(
          @Field("f") field: String,
        )

        @OPTIONS("path") 
        @FormUrlEncoded
        suspend fun options(
          @Field("f") field: String,
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FormUrlEncoded can only be used with HTTP methods that allow a request body, but found method: DELETE" atLine 33,
        "@FormUrlEncoded can only be used with HTTP methods that allow a request body, but found method: GET" atLine 39,
        "@FormUrlEncoded can only be used with HTTP methods that allow a request body, but found method: HEAD" atLine 45,
        "@FormUrlEncoded can only be used with HTTP methods that allow a request body, but found method: OPTIONS" atLine 51,
      )
    }
  }

  @Test fun `@Multipart is only allowed for HTTP methods that allow a request body`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @PATCH("path") 
        @Multipart
        suspend fun patch(
          @Part("*/*", "p") part: String,
        )
       
        @POST("path") 
        @Multipart
        suspend fun post(
          @Part("*/*", "p") part: String,
        )
        
        @PUT("path") 
        @Multipart
        suspend fun put(
          @Part("*/*", "p") part: String,
        )
        
        @HTTP("GET", "path") 
        @Multipart
        suspend fun customGet(
          @Part("*/*", "p") part: String,
        )

        @DELETE("path") 
        @Multipart
        suspend fun delete(
          @Part("*/*", "p") part: String,
        )

        @GET("path") 
        @Multipart
        suspend fun get(
          @Part("*/*", "p") part: String,
        )

        @HEAD("path") 
        @Multipart
        suspend fun head(
          @Part("*/*", "p") part: String,
        )

        @OPTIONS("path") 
        @Multipart
        suspend fun options(
          @Part("*/*", "p") part: String,
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Multipart can only be used with HTTP methods that allow a request body, but found method: DELETE" atLine 33,
        "@Multipart can only be used with HTTP methods that allow a request body, but found method: GET" atLine 39,
        "@Multipart can only be used with HTTP methods that allow a request body, but found method: HEAD" atLine 45,
        "@Multipart can only be used with HTTP methods that allow a request body, but found method: OPTIONS" atLine 51,
      )
    }
  }

  @Test fun `@Body is not allowed in @FormUrlEncoded requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        suspend fun post(
          @Field("f") field: String,
          @Body("text/plain") text: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Body is not allowed in @FormUrlEncoded requests." atLine 11
      )
    }
  }

  @Test fun `@Body is not allowed in @Multipart requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(
          @Part("*/*", "p") part: String,
          @Body("*/*") body: String,
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Body is not allowed in @Multipart requests." atLine 11,
      )
    }
  }

  @Test fun `@Field is only allowed in @FormUrlEncoded requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        suspend fun post(
          @Field("f") field: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Field can only be used in @FormUrlEncoded requests." atLine 9,
      )
    }
  }

  @Test fun `@FieldMap is only allowed in @FormUrlEncoded requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        suspend fun post(
          @FieldMap fields: StringValues
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FieldMap can only be used in @FormUrlEncoded requests." atLine 10
      )
    }
  }

  @Test fun `@FormUrlEncoded functions must have at least a @Field or @FieldMap parameter`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        suspend fun post()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FormUrlEncoded functions must have at least one @Field or @FieldMap parameter." atLine 9
      )
    }
  }

  @Test fun `@Part is only allowed in @Multipart requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        suspend fun post(
          @Part("*/*", "p") part: String
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Part can only be used in @Multipart requests." atLine 9,
      )
    }
  }

  @Test fun `@PartIterable is only allowed in @Multipart requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        suspend fun post(
          @PartIterable("*/*") parts: List<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartIterable can only be used in @Multipart requests." atLine 10,
      )
    }
  }

  @Test fun `@PartMap is only allowed in @Multipart requests`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        suspend fun post(
          @PartMap("*/*") parts: StringValues
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartMap can only be used in @Multipart requests." atLine 10,
      )
    }
  }

  @Test fun `@Multipart functions must have at least a @Part, @PartIterable, or @PartMap parameter`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Multipart functions must have at least one @Part, @PartIterable, or @PartMap parameter." atLine 9
      )
    }
  }

  @Test fun `@FieldMap type must be either Map or StringValues`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        suspend fun post(
          @FieldMap("f") valid1: Map<String, String>,
          @FieldMap("f") valid2: StringValues,
          @FieldMap("f") notValid: Pair<String, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FieldMap parameter type must be either 'kotlin.collections.Map' or 'io.ktor.util.StringValues'. " +
          "Found: 'kotlin.Pair'." atLine 14
      )
    }
  }

  @Test fun `@FieldMap keys must be strings`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        suspend fun post(
          @FieldMap("f") valid: Map<String, String>,
          @FieldMap("f") notValid1: Map<Int, String>,
          @FieldMap("f") notValid2: Map<StringWrapper, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FieldMap keys must be of type 'kotlin.String'. Found: 'kotlin.Int'." atLine 14,
        "@FieldMap keys must be of type 'kotlin.String'. Found: 'test.StringWrapper'." atLine 15,
      )
    }
  }

  @Test fun `@FieldMap keys must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      @Service interface TestApi {
        @POST("post") 
        @FormUrlEncoded
        suspend fun post(
          @FieldMap("f") valid: Map<String, String>,
          @FieldMap("f") notValid: Map<String?, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@FieldMap keys must be non-nullable." atLine 12
      )
    }
  }

  @Test fun `@PartIterable type must be a supported Iterable type`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(
          @PartIterable("*/*", "p") valid1: Iterable<String>,
          @PartIterable("*/*", "p") valid2: Collection<Boolean>,
          @PartIterable("*/*", "p") valid3: List<Int>,
          @PartIterable("*/*", "p") valid4: Set<List<String>>,
          @PartIterable("*/*", "p") notValid1: String,
          @PartIterable("*/*", "p") notValid2: ArrayList<String>,
        )
      }
      """
    )

    val expectedIterableTypes =
      "kotlin.collections.Collection, kotlin.collections.Iterable, kotlin.collections.List, kotlin.collections.Set"

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartIterable parameter type must be one of: $expectedIterableTypes. " +
          "Found: 'kotlin.String'." atLine 16,
        "@PartIterable parameter type must be one of: $expectedIterableTypes. " +
          "Found: 'kotlin.collections.ArrayList'." atLine 17
      )
    }
  }

  @Test fun `@PartMap type must be either Map, StringValues, or an Iterable of key-value pairs`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*
      import io.ktor.util.StringValues

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(
          @PartMap("*/*") valid1: Map<String, String>,
          @PartMap("*/*") valid2: StringValues,
          @PartMap("*/*") valid3: Collection<Pair<String, String>>,
          @PartMap("*/*") notValid1: Pair<String, String>,
          @PartMap("*/*") notValid2: LinkedHashMap<String, String>,
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartMap parameter type must be either 'kotlin.collections.Map', 'io.ktor.util.StringValues', " +
          "or an Iterable of key-value pairs. Found: 'kotlin.Pair'." atLine 15,

        "@PartMap parameter type must be either 'kotlin.collections.Map', 'io.ktor.util.StringValues', " +
          "or an Iterable of key-value pairs. Found: 'kotlin.collections.LinkedHashMap'." atLine 16
      )
    }
  }

  @Test fun `@PartMap Map keys must be strings`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(
          @PartMap("*/*") valid: Map<String, String>,
          @PartMap("*/*") notValid1: Map<Int, String>,
          @PartMap("*/*") notValid2: Map<StringWrapper, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartMap keys must be of type 'kotlin.String'. Found: 'kotlin.Int'." atLine 14,
        "@PartMap keys must be of type 'kotlin.String'. Found: 'test.StringWrapper'." atLine 15,
      )
    }
  }

  @Test fun `@PartMap Map keys must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import kotlin.collections.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(
          @PartMap("*/*") valid: Map<String, String>,
          @PartMap("*/*") notValid: Map<String?, String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartMap keys must be non-nullable." atLine 12
      )
    }
  }

  @Test fun `@Part must provide contentType and formFieldName for @Multipart(form-data) when the parameter type is NOT PartData`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart // form-data is the default
        suspend fun post(@Part(formFieldName = "p") p: String)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@Part(contentType = "*/*") p: String)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@Part p: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Part must provide a non-blank 'contentType' or use PartData as the parameter type." atLine 11,
        "When the @Multipart subtype is 'form-data' (the default), @Part must provide a non-blank 'formFieldName' or use PartData as the parameter type." atLine 15,
        "@Part must provide a non-blank 'contentType' or use PartData as the parameter type." atLine 19,
        "When the @Multipart subtype is 'form-data' (the default), @Part must provide a non-blank 'formFieldName' or use PartData as the parameter type." atLine 19
      )
    }
  }

  @Test fun `@PartIterable must provide contentType and formFieldName for @Multipart(form-data) when the iterable type argument is NOT PartData`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart // form-data is the default
        suspend fun post(@PartIterable(formFieldName = "p") parts: List<String>)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@PartIterable(contentType = "*/*") parts: Collection<String>)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@PartIterable parts: Set<String>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartIterable must provide a non-blank 'contentType' or use parts of type PartData." atLine 11,
        "When the @Multipart subtype is 'form-data' (the default), @PartIterable must provide a non-blank 'formFieldName' or use parts of type PartData." atLine 15,
        "@PartIterable must provide a non-blank 'contentType' or use parts of type PartData." atLine 19,
        "When the @Multipart subtype is 'form-data' (the default), @PartIterable must provide a non-blank 'formFieldName' or use parts of type PartData." atLine 19
      )
    }
  }

  @Test fun `@Part must NOT provide contentType and formFieldName when the parameter type is PartData`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.http.content.PartData

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(@Part(formFieldName = "p") p: PartData)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@Part(contentType = "*/*") p: PartData)
        
        @POST("post") 
        @Multipart("mixed")
        suspend fun post(@Part("*/*", "p") p: PartData)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Part must not define a 'formFieldName' when the parameter type is PartData." atLine 12,
        "@Part must not define a 'contentType' when the parameter type is PartData." atLine 16,
        "@Part must not define a 'contentType' when the parameter type is PartData." atLine 20,
        "@Part must not define a 'formFieldName' when the parameter type is PartData." atLine 20
      )
    }
  }

  @Test fun `@PartIterable must NOT provide contentType and formFieldName when the iterable type argument is PartData`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.http.content.PartData

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(@PartIterable(formFieldName = "p") parts: List<PartData>)
        
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@PartIterable(contentType = "*/*") parts: Collection<PartData>)
        
        @POST("post") 
        @Multipart("mixed")
        suspend fun post(@PartIterable("*/*", "p") parts: Set<PartData>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartIterable must not define a 'formFieldName' when the parts are of type PartData." atLine 12,
        "@PartIterable must not define a 'contentType' when the parts are of type PartData." atLine 16,
        "@PartIterable must not define a 'contentType' when the parts are of type PartData." atLine 20,
        "@PartIterable must not define a 'formFieldName' when the parts are of type PartData." atLine 20
      )
    }
  }

  @Test fun `@PartIterable must provide contentType when the iterable type argument is NOT PartData`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.util.StringValues

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart("form-data")
        suspend fun post(@PartIterable(formFieldName = "p") parts: List<String>)
        
        @POST("post") 
        @Multipart("mixed")
        suspend fun post(@PartIterable parts: Set<Boolean>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartIterable must provide a non-blank 'contentType' or use parts of type PartData." atLine 12,
        "@PartIterable must provide a non-blank 'contentType' or use parts of type PartData." atLine 16
      )
    }
  }

  @Test fun `@PartMap does not allow PartData values`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.http.content.PartData

      data class StringWrapper(val value: String)

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun post(@PartMap(contentType = "*/*") map: Map<String, PartData>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'io.ktor.http.content.PartData'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 12
      )
    }
  }

  @Test fun `@PartMap Iterable must have Pair items`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun validIterable(@PartMap(contentType = "*/*") iterable: Iterable<Pair<String, Boolean>>)

        @POST("post") 
        @Multipart
        suspend fun invalidIterable(@PartMap(contentType = "*/*") iterable: Iterable<Boolean>)
        
        @POST("post") 
        @Multipart
        suspend fun validCollection(@PartMap(contentType = "*/*") collection: Collection<Pair<String, String>>)
        
        @POST("post") 
        @Multipart
        suspend fun invalidCollection(@PartMap(contentType = "*/*") collection: Collection<String>)
        
        @POST("post") 
        @Multipart
        suspend fun validList(@PartMap(contentType = "*/*") list: List<Pair<String, Int>>)

        @POST("post") 
        @Multipart
        suspend fun invalidList(@PartMap(contentType = "*/*") list: List<Int>)
        
        @POST("post") 
        @Multipart
        suspend fun validSet(@PartMap(contentType = "*/*") set: Set<Pair<String, Long>>)
        
        @POST("post") 
        @Multipart
        suspend fun invalidSet(@PartMap(contentType = "*/*") set: Set<Long>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @PartMap Iterable type argument. Expected: 'kotlin.Pair'. Found: 'kotlin.Boolean'." atLine 13,
        "Invalid @PartMap Iterable type argument. Expected: 'kotlin.Pair'. Found: 'kotlin.String'." atLine 21,
        "Invalid @PartMap Iterable type argument. Expected: 'kotlin.Pair'. Found: 'kotlin.Int'." atLine 29,
        "Invalid @PartMap Iterable type argument. Expected: 'kotlin.Pair'. Found: 'kotlin.Long'." atLine 37
      )
    }
  }

  @Test fun `@PartMap Iterable Pair items must not be nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun valid(@PartMap(contentType = "*/*") iterable: Iterable<Pair<String, String>>)

        @POST("post") 
        @Multipart
        suspend fun invalid(@PartMap(contentType = "*/*") iterable: Iterable<Pair<String, String>?>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "The type argument of a @PartMap Iterable must be non-nullable." atLine 13
      )
    }
  }

  @Test fun `@PartMap key-value Pair must have String keys`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*

      @Service interface TestApi {
        @POST("post") 
        @Multipart
        suspend fun valid(@PartMap(contentType = "*/*") iterable: Iterable<Pair<String, String>>)

        @POST("post") 
        @Multipart
        suspend fun invalid(@PartMap(contentType = "*/*") iterable: Iterable<Pair<Int, String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@PartMap keys must be of type 'kotlin.String'. Found: 'kotlin.Int'." atLine 13
      )
    }
  }
}

private const val EXPECTED_BODY_TYPES_MESSAGE_PART =
  "Expected either a @Serializable type, dev.aoddon.connector.http.HttpBody, or a built-in serializable type " +
    "(kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, " +
    "kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, " +
    "kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, " +
    "kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_PART_TYPES_MESSAGE_PART =
  "Expected either a @Serializable type, dev.aoddon.connector.http.HttpBody, io.ktor.http.content.PartData, " +
    "io.ktor.http.content.PartData.BinaryItem, io.ktor.http.content.PartData.FileItem, " +
    "io.ktor.http.content.PartData.FormItem, or a built-in serializable type (kotlin.Boolean, kotlin.Byte, " +
    "kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, kotlin.String, kotlin.Pair, " +
    "kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, kotlin.ByteArray, " +
    "kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, kotlin.ShortArray, " +
    "kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

internal const val EXPECTED_RETURN_TYPES_MESSAGE_PART =
  "Expected either a @Serializable type, kotlin.Unit, dev.aoddon.connector.http.HttpBody, " +
    "dev.aoddon.connector.http.HttpResult, dev.aoddon.connector.http.HttpResponse, " +
    "dev.aoddon.connector.http.HttpResponse.Success, or a built-in serializable type " +
    "(kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, " +
    "kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, " +
    "kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, " +
    "kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_TYPE_ARGUMENTS_EXCLUDE_UNIT_EXCLUDE_HTTP_CONTENT_MESSAGE_PART =
  "Expected either a @Serializable type or a built-in serializable type (kotlin.Boolean, kotlin.Byte, kotlin.Char, " +
    "kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, kotlin.String, kotlin.Pair, kotlin.Triple, " +
    "kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, kotlin.ByteArray, kotlin.CharArray, " +
    "kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, kotlin.ShortArray, " +
    "kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_TYPE_ARGUMENTS_INCLUDE_UNIT_INCLUDE_HTTP_CONTENT_MESSAGE_PART =
  "Expected either a @Serializable type, kotlin.Unit, dev.aoddon.connector.http.HttpBody, " +
    "or a built-in serializable type (kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, " +
    "kotlin.Int, kotlin.Long, kotlin.Short, kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, " +
    "kotlin.Array, kotlin.BooleanArray, kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, " +
    "kotlin.IntArray, kotlin.LongArray, kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, " +
    "kotlin.collections.Map, kotlin.collections.Set)"
