package connector

import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import connector.util.runTestCompilation
import org.junit.Test

class ServiceValidation {
  @Test fun `@Service abstract class target is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
          package test

          import connector.*
          import connector.http.*

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

        import connector.*
        import connector.http.*

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

        import connector.*
        import connector.http.*

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

        import connector.*
        import connector.http.*

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

      import connector.*
      import connector.http.*

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

      import connector.*
      import connector.http.*

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

      import connector.*
      import connector.http.*

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

  @Test fun `Multiple HTTP methods are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") @HEAD("head") suspend fun getOrHead()
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Multiple HTTP method annotations are not allowed." atLine 7
      )
    }
  }

  @Test fun `Non-suspension function is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

      import connector.*
      import connector.http.*

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

      import connector.*
      import connector.http.*

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

  @Test fun `Dynamic query parameters in the relative URL are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

  @Test fun `Function parameters must be annotated`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(notAnnotated: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Function parameter must have a valid connector annotation." atLine 7)
    }
  }

  @Test fun `Multiple annotations on the same function parameter are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get/{id}") suspend fun get(@Path("id") @Query("id") id: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Multiple connector annotations are not allowed on the same parameter." atLine 7
      )
    }
  }

  @Test fun `@Path name must match a parameter in the URL template`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET suspend fun get(@URL url: String, @Path("id") id: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@GET URL does not define a dynamic path parameter matching 'id'." atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @GET requests`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @HEAD("head") suspend fun head(@Body("*/*") body: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in HEAD requests." atLine 7)
    }
  }

  @Test fun `@Body is not allowed in @OPTIONS requests`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @OPTIONS("options") suspend fun options(@Body("*/*") body: String): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("@Body is not allowed in OPTIONS requests." atLine 7)
    }
  }

  @Test fun `Multiple @Body are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

  @Test fun `Multiple @URL are not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

  @Test fun `Invalid Content-Type format`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

  @Test fun `Invalid @Path parameter name format`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("invalidProtocol://a/b") suspend fun get(): String
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("URL protocol must be HTTP or HTTPS. Found: 'invalidProtocol'." atLine 7)
    }
  }

  @Test fun `Static Content-Type header is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Header("Content-Type") contentType: String)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Content-Type header cannot be defined via @Header, but only via @Body." atLine 7)
    }
  }

  @Test fun `Static Content-Length header is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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

  @Test fun `Nullable Unit is not a valid return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: TypeThatDoesNotExist)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid @Body type: '<Error>'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 7)
    }
  }

  @Test fun `Unknown return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): TypeThatDoesNotExist
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Invalid return type: '<Error>'. $EXPECTED_RETURN_TYPES_MESSAGE_PART" atLine 7)
    }
  }

  @Test fun `Unknown @Body generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<TypeThatDoesNotExist>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART" atLine 7
      )
    }
  }

  @Test fun `Unknown return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): Map<
          TypeThatDoesNotExist, 
          OtherTypeThatDoesNotExist>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART" atLine 8,
        "Invalid type argument: '<Error>'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART" atLine 9
      )
    }
  }

  @Test fun `Unit is not a valid @Body generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<Unit>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `Unit is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<Unit>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'kotlin.Unit'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `Star is not a valid @Body generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<*>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. $EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART" atLine 7
      )
    }
  }

  @Test fun `Star is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<*>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: '*'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody is not a valid @Body generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(@Body("*/*") body: List<HttpBody<String>>)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpBody<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'connector.http.HttpBody'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResult is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResult<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'connector.http.HttpResult'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResponse is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResponse<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'connector.http.HttpResponse'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpResponseSuccess is not a valid return type generic argument`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @GET("get") suspend fun get(): List<HttpResponse.Success<String>>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'connector.http.HttpResponse.Success'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 7
      )
    }
  }

  @Test fun `HttpBody with Unit type argument is not allowed as @Body`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 8
      )
    }
  }

  @Test fun `HttpBody with non-@Serializable type argument is not allowed as @Body`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @JsonBody payload: Payload
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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResult<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'connector.http.HttpResult'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpResponse @Body is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResponse<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'connector.http.HttpResponse'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpResponseSuccess @Body is not allowed`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      @Service interface TestApi {
        @POST("post") suspend fun post(
          @Body("*/*") body: HttpResponse.Success<String>
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Body type: 'connector.http.HttpResponse.Success'. $EXPECTED_BODY_TYPES_MESSAGE_PART" atLine 8
      )
    }
  }

  @Test fun `HttpBody with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpBody<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResult with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResult<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponse with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `HttpResponseSuccess with non-@Serializable type argument is not allowed as return type`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

      data class Payload(val value: String)

      @Service interface TestApi {
        @GET("get") suspend fun get(): HttpResponse.Success<Payload>
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid type argument: 'test.Payload'. " +
          EXPECTED_TYPE_ARGUMENTS_INCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART atLine 9
      )
    }
  }

  @Test fun `Nullable Unit is not allowed as type argument of HttpResult`() {
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
    val sourceFile = kotlin(
      "Test.kt",
      """
      package test

      import connector.*
      import connector.http.*

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
}

private const val EXPECTED_BODY_TYPES_MESSAGE_PART =
  "Expected either a @Serializable type, connector.http.HttpBody, or a built-in serializable type " +
    "(kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, " +
    "kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, " +
    "kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, " +
    "kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_RETURN_TYPES_MESSAGE_PART =
  "Expected either a @Serializable type, kotlin.Unit, connector.http.HttpBody, connector.http.HttpResult, " +
    "connector.http.HttpResponse, connector.http.HttpResponse.Success, or a built-in serializable type " +
    "(kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, " +
    "kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, " +
    "kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, " +
    "kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_TYPE_ARGUMENTS_EXCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART =
  "Expected either a @Serializable type or a built-in serializable type (kotlin.Boolean, kotlin.Byte, kotlin.Char, " +
    "kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, kotlin.String, kotlin.Pair, kotlin.Triple, " +
    "kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, kotlin.ByteArray, kotlin.CharArray, " +
    "kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, kotlin.ShortArray, kotlin.StringArray, " +
    "kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"

private const val EXPECTED_TYPE_ARGUMENTS_INCLUDING_UNIT_AND_HTTP_BODY_MESSAGE_PART =
  "Expected either a @Serializable type, kotlin.Unit, connector.http.HttpBody, or a built-in serializable type " +
    "(kotlin.Boolean, kotlin.Byte, kotlin.Char, kotlin.Double, kotlin.Float, kotlin.Int, kotlin.Long, kotlin.Short, " +
    "kotlin.String, kotlin.Pair, kotlin.Triple, kotlin.collections.Map.Entry, kotlin.Array, kotlin.BooleanArray, " +
    "kotlin.ByteArray, kotlin.CharArray, kotlin.DoubleArray, kotlin.FloatArray, kotlin.IntArray, kotlin.LongArray, " +
    "kotlin.ShortArray, kotlin.StringArray, kotlin.collections.List, kotlin.collections.Map, kotlin.collections.Set)"
