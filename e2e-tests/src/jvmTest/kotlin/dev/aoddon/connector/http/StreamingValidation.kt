package dev.aoddon.connector.http

import com.tschuchort.compiletesting.SourceFile
import dev.aoddon.connector.util.runTestCompilation
import kotlin.test.Test

class StreamingValidationTest {
  @Test fun `Multiple @Streaming parameters are not allowed`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun multipleConsumers(
          @Streaming consumer1: suspend (ByteReadChannel) -> Unit,
          @Streaming consumer2: suspend (ByteReadChannel) -> Unit
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors("Multiple @Streaming parameters are not allowed." atLine 9)
    }
  }

  @Test fun `@Streaming type must be a suspending lambda`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: (ByteReadChannel) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: '(io.ktor.utils.io.ByteReadChannel) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: (suspend (ByteReadChannel) -> Unit)?)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: '(suspend (io.ktor.utils.io.ByteReadChannel) -> kotlin.Unit)?'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda must have a single valid parameter`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid1(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun valid2(@Streaming consumer: suspend (HttpResult<ByteReadChannel>) -> Unit)

        @GET("get") 
        suspend fun valid3(@Streaming consumer: suspend (HttpResponse<ByteReadChannel>) -> Unit)

        @GET("get") 
        suspend fun valid4(@Streaming consumer: suspend (HttpResponse.Success<ByteReadChannel>) -> Unit)
        
        @GET("get") 
        suspend fun invalidLambdaParameter(@Streaming consumer: suspend (ByteArray) -> Unit)
        
        @GET("get") 
        suspend fun extraLambdaParameter(
          @Streaming consumer: suspend (ByteReadChannel, ByteReadChannel) -> Unit
        )
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: 'suspend (kotlin.ByteArray) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 21,
        "Invalid @Streaming parameter type: 'suspend (io.ktor.utils.io.ByteReadChannel, " +
          "io.ktor.utils.io.ByteReadChannel) -> kotlin.Unit'. $EXPECTED_STREAMING_TYPE_MESSAGE_PART" atLine 25
      )
    }
  }

  @Test fun `@Streaming lambda ByteReadChannel parameter must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: suspend (ByteReadChannel?) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: 'suspend (io.ktor.utils.io.ByteReadChannel?) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda HttpResult parameter must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (HttpResult<ByteReadChannel>) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: suspend (HttpResult<ByteReadChannel>?) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: " +
          "'suspend (dev.aoddon.connector.http.HttpResult<io.ktor.utils.io.ByteReadChannel>?) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda HttpResponse parameter must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (HttpResponse<ByteReadChannel>) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: suspend (HttpResponse<ByteReadChannel>?) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: " +
          "'suspend (dev.aoddon.connector.http.HttpResponse<io.ktor.utils.io.ByteReadChannel>?) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda HttpResponseSuccess parameter must be non-nullable`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (HttpResponse.Success<ByteReadChannel>) -> Unit)
        
        @GET("get") 
        suspend fun invalid(@Streaming consumer: suspend (HttpResponse.Success<ByteReadChannel>?) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: " +
          "'suspend (dev.aoddon.connector.http.HttpResponse.Success<io.ktor.utils.io.ByteReadChannel>?) -> " +
          "kotlin.Unit'. $EXPECTED_STREAMING_TYPE_MESSAGE_PART" atLine 12
      )
    }
  }

  @Test fun `@Streaming lambda must not have a receiver`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun invalid1(@Streaming consumer: suspend ByteReadChannel.() -> Unit)

        @GET("get") 
        suspend fun invalid2(@Streaming consumer: suspend Any?.(ByteReadChannel) -> Unit)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "Invalid @Streaming parameter type: 'suspend io.ktor.utils.io.ByteReadChannel.() -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 12,
        "Invalid @Streaming parameter type: 'suspend kotlin.Any?.(io.ktor.utils.io.ByteReadChannel) -> kotlin.Unit'. " +
          EXPECTED_STREAMING_TYPE_MESSAGE_PART atLine 15,
      )
    }
  }

  @Test fun `@Streaming lambda return type must be the same type returned by the function`() {
    val sourceFile = SourceFile.kotlin(
      "Test.kt",
      """
      package test

      import dev.aoddon.connector.*
      import dev.aoddon.connector.http.*
      import io.ktor.utils.io.ByteReadChannel

      @Service interface TestApi {
        @GET("get") 
        suspend fun valid1(@Streaming consumer: suspend (ByteReadChannel) -> Unit)
        
        @GET("get") 
        suspend fun valid2(@Streaming consumer: suspend (ByteReadChannel) -> Boolean): Boolean
        
        @GET("get") 
        suspend fun invalid1(@Streaming consumer: suspend (ByteReadChannel) -> Unit): Boolean
        
        @GET("get") 
        suspend fun invalid2(@Streaming consumer: suspend (ByteReadChannel) -> Boolean)
      }
      """
    )

    sourceFile.runTestCompilation {
      assertKspErrors(
        "@Streaming lambda return type must match the function return type." atLine 15,
        "@Streaming lambda return type must match the function return type." atLine 18
      )
    }
  }
}

private const val EXPECTED_STREAMING_TYPE_MESSAGE_PART = "Expected a non-nullable suspending lambda " +
  "with no receiver and exactly one of the following (non-nullable) parameters: ByteReadChannel, " +
  "HttpResult<ByteReadChannel>, HttpResponse<ByteReadChannel>, or HttpResponse.Success<ByteReadChannel>."
