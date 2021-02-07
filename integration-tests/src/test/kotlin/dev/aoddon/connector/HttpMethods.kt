package dev.aoddon.connector

import dev.aoddon.connector.http.DELETE
import dev.aoddon.connector.http.GET
import dev.aoddon.connector.http.HEAD
import dev.aoddon.connector.http.HTTP
import dev.aoddon.connector.http.HttpResponse
import dev.aoddon.connector.http.HttpResult
import dev.aoddon.connector.http.OPTIONS
import dev.aoddon.connector.http.PATCH
import dev.aoddon.connector.http.POST
import dev.aoddon.connector.http.PUT
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.runHttpTest
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import org.junit.Test

private val BASE_URL = Url("https://methods/")

@Service interface HttpMethodsTestService {
  @DELETE("delete") suspend fun delete()
  @GET("get") suspend fun get()
  @HEAD("head") suspend fun head()
  @OPTIONS("options") suspend fun options()
  @PATCH("patch") suspend fun patch()
  @POST("post") suspend fun post()
  @PUT("put") suspend fun put()
  @HTTP(method = "CUSTOM", url = "customMethod") suspend fun customMethod()

  // Valid return types for @HEAD
  @HEAD("head") suspend fun headResult(): HttpResult<Unit>
  @HEAD("head") suspend fun headResponse(): HttpResponse<Unit>
  @HEAD("head") suspend fun headResponseSuccess(): HttpResponse.Success<Unit>
  @HEAD("head") suspend fun headResultStar(): HttpResult<*>
  @HEAD("head") suspend fun headResponseStar(): HttpResponse<*>
  @HEAD("head") suspend fun headResponseSuccessStar(): HttpResponse.Success<*>
}

class HttpMethods {
  @Test fun `@DELETE`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.delete()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Delete)
      hasUrl("${BASE_URL}delete")
    }
  }

  @Test fun `@GET`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.get()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Get)
      hasUrl("${BASE_URL}get")
    }
  }

  @Test fun `@HEAD`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)

    service.head()
    service.headResult()
    service.headResponse()
    service.headResponseSuccess()
    service.headResultStar()
    service.headResponseStar()
    service.headResponseSuccessStar()

    assertHttpLogMatches(
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      },
      {
        hasMethod(HttpMethod.Head)
        hasUrl("${BASE_URL}head")
      }
    )
  }

  @Test fun `@OPTIONS`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.options()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Options)
      hasUrl("${BASE_URL}options")
    }
  }

  @Test fun `@PATCH`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.patch()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Patch)
      hasUrl("${BASE_URL}patch")
    }
  }

  @Test fun `@POST`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.post()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Post)
      hasUrl("${BASE_URL}post")
    }
  }

  @Test fun `@PUT`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.put()
    assertHttpLogMatches {
      hasMethod(HttpMethod.Put)
      hasUrl("${BASE_URL}put")
    }
  }

  @Test fun `Custom method with @HTTP`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.customMethod()
    assertHttpLogMatches {
      hasMethod(HttpMethod("CUSTOM"))
      hasUrl("${BASE_URL}customMethod")
    }
  }
}
