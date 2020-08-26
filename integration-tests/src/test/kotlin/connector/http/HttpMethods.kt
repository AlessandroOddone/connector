package connector.http

import connector.Service
import connector.util.assertHttpLogMatches
import connector.util.runTest
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
    @Test fun `@DELETE`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.delete()
        assertHttpLogMatches { hasMethod(HttpMethod.Delete) }
    }

    @Test fun `@GET`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.get()
        assertHttpLogMatches { hasMethod(HttpMethod.Get) }
    }

    @Test fun `@HEAD`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.head()
        assertHttpLogMatches { hasMethod(HttpMethod.Head) }
    }

    @Test fun `@OPTIONS`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.options()
        assertHttpLogMatches { hasMethod(HttpMethod.Options) }
    }

    @Test fun `@PATCH`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.patch()
        assertHttpLogMatches { hasMethod(HttpMethod.Patch) }
    }

    @Test fun `@POST`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.post()
        assertHttpLogMatches { hasMethod(HttpMethod.Post) }
    }

    @Test fun `@PUT`() = runTest {
        val service = HttpMethodsTestService(BASE_URL, httpClient, emptyList())
        service.put()
        assertHttpLogMatches { hasMethod(HttpMethod.Put) }
    }
}
