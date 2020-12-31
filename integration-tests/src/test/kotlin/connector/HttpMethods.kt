package connector

import connector.http.DELETE
import connector.http.GET
import connector.http.HEAD
import connector.http.OPTIONS
import connector.http.PATCH
import connector.http.POST
import connector.http.PUT
import connector.util.assertHttpLogMatches
import connector.util.runHttpTest
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
}

class HttpMethods {
  @Test fun `@DELETE`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.delete()
    assertHttpLogMatches { hasMethod(HttpMethod.Delete) }
  }

  @Test fun `@GET`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.get()
    assertHttpLogMatches { hasMethod(HttpMethod.Get) }
  }

  @Test fun `@HEAD`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.head()
    assertHttpLogMatches { hasMethod(HttpMethod.Head) }
  }

  @Test fun `@OPTIONS`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.options()
    assertHttpLogMatches { hasMethod(HttpMethod.Options) }
  }

  @Test fun `@PATCH`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.patch()
    assertHttpLogMatches { hasMethod(HttpMethod.Patch) }
  }

  @Test fun `@POST`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.post()
    assertHttpLogMatches { hasMethod(HttpMethod.Post) }
  }

  @Test fun `@PUT`() = runHttpTest {
    val service = HttpMethodsTestService(BASE_URL, httpClient)
    service.put()
    assertHttpLogMatches { hasMethod(HttpMethod.Put) }
  }
}
