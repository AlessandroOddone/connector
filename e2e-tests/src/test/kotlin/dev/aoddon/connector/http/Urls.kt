package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.URL
import dev.aoddon.connector.test.util.assertThrows
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.runHttpTest
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parametersOf
import io.ktor.util.StringValues
import org.junit.Test

private val BASE_URL = Url("https://urls/base/")

@Service interface UrlsTestService {
  @GET("relative/path") suspend fun relativePath()

  @GET("/absolute/path") suspend fun absolutePath()

  @GET("//protocol/relative/path") suspend fun protocolRelativePath()

  @GET("https://full/url") suspend fun fullUrl()

  @GET("string/path/parameters/p1/{p1}/p2/{p2}")
  suspend fun stringPathParameters(
    @Path("p1") pathParam1: String,
    @Path("p2") pathParam2: String
  )

  @GET("any/path/parameters/p1/{p1}/p2/{p2}")
  suspend fun anyPathParameters(
    @Path("p1") pathParam1: Any,
    @Path("p2") pathParam2: Any
  )

  @GET("fruit/all{fruitName}s")
  suspend fun nonFullSegmentPathParameter(@Path("fruitName") fruit: String)

  // path/[1 space]{param}[100 spaces]/etc
  @GET("path/ {param}                                                                                                    /etc")
  suspend fun pathParameterInSegmentContainingLotsOfExtraWhitespaces(
    @Path("param") parameter: String
  )

  @GET("\$pa\$th\$/\$cont\$aining\$/\$dol\$lar\$/\$si\$gns\$/\${p}\$/")
  suspend fun pathContainingDollarSigns(
    @Path("p") parameter: String
  )

  @GET("\$pa\tth\b/\ncont\raining\'/\\a\"ll\"/\\es\'cape\$/seq\bue\rnces\t/\n{p}\$/")
  suspend fun pathContainingAllEscapeSequences(
    @Path("p") parameter: String
  )

  @GET("multiple/{sameName}/path/{sameName}/parameters/{sameName}")
  suspend fun multiplePathParametersSameName(@Path("sameName") pathParam: String)

  @GET("string/path/parameters/p1/{p1}/p2/{p2,p3}")
  suspend fun invalidPathParameterSurroundedByBraces(@Path("p1") pathParam1: String)

  @GET("string/query/parameters")
  suspend fun stringQueryParameters(
    @Query("q1") queryParam1: String,
    @Query("q2") queryParam2: String
  )

  @GET("string/query/names")
  suspend fun stringQueryNames(
    @QueryName queryName1: String,
    @QueryName queryName2: String
  )

  @GET("nullable/and/not/nullable/any/query/parameters")
  suspend fun nullableAndNotNullableAnyQueryParameters(
    @Query("q1") queryParam1: Any?,
    @Query("q2") queryParam2: Any
  )

  @GET("nullable/and/not/nullable/any/query/names")
  suspend fun nullableAndNotNullableAnyQueryNames(
    @QueryName queryName1: Any?,
    @QueryName queryName2: Any
  )

  @GET("optional/query/parameters")
  suspend fun optionalQueryParameters(
    @Query("q1") queryParam1: Any?,
    @Query("q2") queryParam2: Any?,
    @QueryName queryName1: Any?,
    @QueryName queryName2: Any?
  )

  @GET("mix/static/and/dynamic/query/parameters?q1=static1&q2=static2&staticQueryName")
  suspend fun mixStaticAndDynamicQueryParameters(
    @Query("q3") queryParam3: String,
    @Query("q4") queryParam4: String,
    @QueryName dynamicQueryName: String
  )

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun multipleQueryParametersSameName(
    @Query("q") queryParam1: String,
    @Query("q") queryParam2: String,
  )

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun iterableOfStringQueryParametersWithSameName(@Query("q") q: Iterable<String>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun iterableOfStringQueryNames(@QueryName names: Iterable<String>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun iterableOfAnyQueryParametersWithSameName(@Query("q") q: Iterable<Any>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun iterableOfAnyQueryNames(@QueryName names: Iterable<Any>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun collectionOfStringQueryParametersWithSameName(@Query("q") q: Collection<Any>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun collectionOfStringQueryNames(@QueryName names: Collection<Any>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun collectionOfAnyQueryParametersWithSameName(@Query("q") q: Collection<Any>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun collectionOfAnyQueryNames(@QueryName names: Collection<Any>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun listOfStringQueryParametersWithSameName(@Query("q") q: List<String>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun listOfStringQueryNames(@QueryName names: List<String>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun listOfAnyQueryParametersWithSameName(@Query("q") q: List<Any>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun listOfAnyQueryNames(@QueryName names: List<Any>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun setOfStringQueryParametersWithSameName(@Query("q") q: Set<String>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun setOfStringQueryNames(@QueryName names: Set<String>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun setOfAnyQueryParametersWithSameName(@Query("q") q: Set<Any>)

  @GET("multiple/query/names?key=value&name1")
  suspend fun setOfAnyQueryNames(@QueryName names: Set<Any>)

  @GET("multiple/query/parameters/same/name?q=10")
  suspend fun queryParameterIterableNullableTypes(
    @Query("q") q1: Iterable<String>?,
    @Query("q") q2: Collection<Any>?,
    @Query("q") q3: List<String?>,
    @Query("q") q4: Set<Any?>,
    @Query("q") q5: List<String?>?,
    @Query("q") q6: Iterable<Any?>?,
    @QueryName qn1: Iterable<String>?,
    @QueryName qn2: Collection<Any>?,
    @QueryName qn3: List<String?>,
    @QueryName qn4: Set<Any?>,
    @QueryName qn5: List<String?>?,
    @QueryName qn6: Iterable<Any?>?,
    @QueryMap stringValues: StringValues?,
    @QueryMap map1: Map<String, List<String?>?>?,
    @QueryMap map2: Map<String, Collection<Any?>?>?,
    @QueryMap map3: List<Pair<String, Collection<String?>?>>?,
    @QueryMap map4: List<Pair<String, Collection<Any?>?>>?
  )

  @GET("query/parameter/with/question/mark")
  suspend fun queryParameterWithQuestionMark(@Query("q?") queryParam: String)

  @GET("static/query/parameter/with/no/value?queryParameter")
  suspend fun staticQueryParameterWithNoValue()

  @GET("trailing/question/mark/no/query/parameters?")
  suspend fun trailingQuestionMarkNoQueryParameters()

  @GET suspend fun dynamicStringUrl(@URL url: String)

  @GET suspend fun dynamicKtorUrl(@URL url: Url)

  @GET suspend fun dynamicAnyUrl(@URL url: Any)

  @GET suspend fun dynamicUrlAndDynamicQueryParameter(
    @URL url: Any,
    @Query("dynamic") dynamicQueryParam: Any
  )

  @GET("get?q=static")
  suspend fun queryParameterStringMap(@QueryMap map: Map<String, String>)

  @GET("get?q=static")
  suspend fun queryParameterAnyMap(@QueryMap map: Map<String, Any>)

  @GET("get?q=static")
  suspend fun queryParameterMapOfIterableString(@QueryMap map: Map<String, Iterable<String>>)

  @GET("get?q=static")
  suspend fun queryParameterMapOfIterableAny(@QueryMap map: Map<String, Iterable<Any>>)

  @GET("get?q=static")
  suspend fun queryMapStringValues(@QueryMap stringValues: StringValues)

  @GET("get?q=static")
  suspend fun queryMapParameters(@QueryMap parameters: Parameters)

  @GET("get?q=static")
  suspend fun queryMapHeaders(@QueryMap headers: Headers)

  @GET("get?q=static")
  suspend fun queryMapListOfPairsWithStringValues(@QueryMap entries: List<Pair<String, String>>)

  @GET("get?q=static")
  suspend fun queryMapListOfPairsWithAnyValues(@QueryMap entries: List<Pair<String, Any>>)

  @GET("get?q=static")
  suspend fun queryMapListOfPairsWithIterableValues(@QueryMap entries: List<Pair<String, Iterable<Any>>>)
}

class UrlsTest {
  @Test fun `Relative path`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.relativePath()
    assertHttpLogMatches { hasUrl("https://urls/base/relative/path") }
  }

  @Test fun `Absolute path`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.absolutePath()
    assertHttpLogMatches { hasUrl("https://urls/absolute/path") }
  }

  @Test fun `Protocol-relative path`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.protocolRelativePath()
    assertHttpLogMatches { hasUrl("https://protocol/relative/path") }
  }

  @Test fun `Full URL`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.fullUrl()
    assertHttpLogMatches { hasUrl("https://full/url") }
  }

  @Test fun `String @Path parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.stringPathParameters(
      pathParam1 = "value1",
      pathParam2 = "value2"
    )
    assertHttpLogMatches { hasUrl("https://urls/base/string/path/parameters/p1/value1/p2/value2") }
  }

  @Test fun `'toString' of @Path object argument is used as the path parameter value`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.anyPathParameters(
      pathParam1 = object : Any() {
        override fun toString() = "value1"
      },
      pathParam2 = object : Any() {
        override fun toString() = "value2"
      }
    )
    assertHttpLogMatches { hasUrl("https://urls/base/any/path/parameters/p1/value1/p2/value2") }
  }

  @Test fun `@Path parameter that is not a full path segment`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.nonFullSegmentPathParameter(fruit = "Banana")
    assertHttpLogMatches { hasUrl("https://urls/base/fruit/allBananas") }
  }

  @Test fun `Multiple segments in @Path argument`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.stringPathParameters(
      pathParam1 = "multiple/segments/v1",
      pathParam2 = "v2"
    )
    assertHttpLogMatches { hasUrl("https://urls/base/string/path/parameters/p1/multiple/segments/v1/p2/v2") }
  }

  @Test fun `Multiple @Path parameters with the same name`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.multiplePathParametersSameName(pathParam = "same")
    assertHttpLogMatches { hasUrl("https://urls/base/multiple/same/path/same/parameters/same") }
  }

  @Test fun `String @Query parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.stringQueryParameters(
      queryParam1 = "value1",
      queryParam2 = "value2"
    )
    assertHttpLogMatches { hasUrl("https://urls/base/string/query/parameters?q1=value1&q2=value2") }
  }

  @Test fun `String @QueryName parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.stringQueryNames(
      queryName1 = "name1",
      queryName2 = "name2"
    )
    assertHttpLogMatches { hasUrl("https://urls/base/string/query/names?name1&name2") }
  }

  @Test fun `'toString' of @Query object argument is used as the query parameter value`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.nullableAndNotNullableAnyQueryParameters(
      queryParam1 = object : Any() {
        override fun toString() = "value1"
      },
      queryParam2 = object : Any() {
        override fun toString() = "value2"
      }
    )
    assertHttpLogMatches {
      hasUrl("https://urls/base/nullable/and/not/nullable/any/query/parameters?q1=value1&q2=value2")
    }
  }

  @Test fun `'toString' of @QueryName object argument is used as the query parameter name`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.nullableAndNotNullableAnyQueryNames(
      queryName1 = object : Any() {
        override fun toString() = "name1"
      },
      queryName2 = object : Any() {
        override fun toString() = "name2"
      }
    )
    assertHttpLogMatches { hasUrl("https://urls/base/nullable/and/not/nullable/any/query/names?name1&name2") }
  }

  @Test fun `If the @Query argument is null, the query parameter is omitted`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.nullableAndNotNullableAnyQueryParameters(
      queryParam1 = null,
      queryParam2 = object : Any() {
        override fun toString() = "value2"
      }
    )
    assertHttpLogMatches { hasUrl("https://urls/base/nullable/and/not/nullable/any/query/parameters?q2=value2") }
  }

  @Test fun `If the @QueryName argument is null, the query parameter is omitted`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.nullableAndNotNullableAnyQueryNames(
      queryName1 = null,
      queryName2 = object : Any() {
        override fun toString() = "name2"
      }
    )
    assertHttpLogMatches { hasUrl("https://urls/base/nullable/and/not/nullable/any/query/names?name2") }
  }

  @Test fun `If all @Query and @QueryName arguments are null, the query string is omitted`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.optionalQueryParameters(
      queryParam1 = null,
      queryParam2 = null,
      queryName1 = null,
      queryName2 = null
    )
    assertHttpLogMatches { hasUrl("https://urls/base/optional/query/parameters") }
  }

  @Test fun `Can mix static and dynamic query parameters`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.mixStaticAndDynamicQueryParameters(
      queryParam3 = "dynamic3",
      queryParam4 = "dynamic4",
      dynamicQueryName = "dynamicQueryName"
    )
    assertHttpLogMatches {
      hasUrl("https://urls/base/mix/static/and/dynamic/query/parameters?q1=static1&q2=static2&staticQueryName&q3=dynamic3&q4=dynamic4&dynamicQueryName")
    }
  }

  @Test fun `Multiple query parameters with the same name, both static and dynamic`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.multipleQueryParametersSameName(queryParam1 = "20", queryParam2 = "30")
    assertHttpLogMatches { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30") }
  }

  @Test fun `Static query parameter with no value`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.staticQueryParameterWithNoValue()
    assertHttpLogMatches { hasUrl("https://urls/base/static/query/parameter/with/no/value?queryParameter") }
  }

  @Test fun `Trailing question mark with no query parameters`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.trailingQuestionMarkNoQueryParameters()
    assertHttpLogMatches { hasUrl("https://urls/base/trailing/question/mark/no/query/parameters?") }
  }

  @Test fun `String @URL parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicStringUrl("test/path?q1=v1&q2=v2")
    assertHttpLogMatches { hasUrl("https://urls/base/test/path?q1=v1&q2=v2") }
  }

  @Test fun `Ktor Url @URL parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    val url = URLBuilder()
      .apply {
        protocol = URLProtocol.HTTPS
        host = "host"
        path("p1", "p2", "p3")
        with(parameters) {
          append("q1", "v1")
          append("q2", "v2")
          append("q3", "v3")
        }
      }
      .build()

    service.dynamicKtorUrl(url)
    assertHttpLogMatches { hasUrl(url) }
  }

  @Test fun `'toString' of @URL object argument is used as the request URL`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicAnyUrl(
      object : Any() {
        override fun toString() = "test/path?q1=v1&q2=v2"
      }
    )
    assertHttpLogMatches { hasUrl("https://urls/base/test/path?q1=v1&q2=v2") }
  }

  @Test fun `@Query parameter is appended to @URL`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicUrlAndDynamicQueryParameter(
      url = "path?static=s",
      dynamicQueryParam = "d"
    )
    assertHttpLogMatches { hasUrl("https://urls/base/path?static=s&dynamic=d") }
  }

  @Test fun `@URL providing an absolute path`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicStringUrl("/dynamic/absolute/path?q1=v1&q2=v2")
    assertHttpLogMatches { hasUrl("https://urls/dynamic/absolute/path?q1=v1&q2=v2") }
  }

  @Test fun `@URL providing a protocol-relative path`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicStringUrl("//dynamic/protocol/relative/path?q1=v1&q2=v2")
    assertHttpLogMatches { hasUrl("https://dynamic/protocol/relative/path?q1=v1&q2=v2") }
  }

  @Test fun `@URL providing a full URL`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.dynamicStringUrl("https://dynamic/full/url?q1=v1&q2=v2")
    assertHttpLogMatches { hasUrl("https://dynamic/full/url?q1=v1&q2=v2") }
  }

  @Test fun `Encoded @Path parameters`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.stringPathParameters(pathParam1 = "v%201", pathParam2 = "%20v%20%202")
    service.dynamicStringUrl("v%201/%20v%20%202")

    assertHttpLogMatches(
      { hasUrl("https://urls/base/string/path/parameters/p1/v%201/p2/%20v%20%202") },
      { hasUrl("https://urls/base/v%201/%20v%20%202") },
    )
  }

  @Test fun `Whitespaces in @Path arguments are encoded`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.stringPathParameters(pathParam1 = "v 1", pathParam2 = " v  2")
    service.dynamicStringUrl("v 1/ v  2")

    assertHttpLogMatches(
      { hasUrl("https://urls/base/string/path/parameters/p1/v%201/p2/%20v%20%202") },
      { hasUrl("https://urls/base/v%201/%20v%20%202") },
    )
  }

  @Test fun `Braces are encoded in the URL if they don't surround a valid parameter`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.invalidPathParameterSurroundedByBraces(pathParam1 = "v1")
    assertHttpLogMatches { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/%7Bp2,p3%7D") }
  }

  @Test fun `Question mark in @Query argument is encoded`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.queryParameterWithQuestionMark(queryParam = "v?")
    assertHttpLogMatches { hasUrl("https://urls/base/query/parameter/with/question/mark?q%3F=v%3F") }
  }

  @Test fun `Hash in @Query argument is encoded`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.queryParameterWithQuestionMark(queryParam = "v#")
    assertHttpLogMatches { hasUrl("https://urls/base/query/parameter/with/question/mark?q%3F=v%23") }
  }

  @Test fun `Ampersand in @Query argument is encoded`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.queryParameterWithQuestionMark(queryParam = "v&")
    assertHttpLogMatches { hasUrl("https://urls/base/query/parameter/with/question/mark?q%3F=v%26") }
  }

  @Test fun `Base URL protocol must be HTTP or HTTPS`() = runHttpTest {
    assertThrows<IllegalArgumentException>(
      message = "Base URL protocol must be HTTP or HTTPS. Found: ftp://urls/base/"
    ) {
      UrlsTestService(Url("ftp://urls/base/"), httpClient)
    }
  }

  @Test fun `Base URL path must have a trailing slash`() = runHttpTest {
    assertThrows<IllegalArgumentException>(
      message = "Base URL should end in '/'. Found: https://urls/base"
    ) {
      UrlsTestService(
        URLBuilder(BASE_URL).apply { encodedPath = BASE_URL.encodedPath.removeSuffix("/") }.build(),
        httpClient,
        emptyList()
      )
    }
  }

  @Test fun `Base URL can't have query parameters`() = runHttpTest {
    assertThrows<IllegalArgumentException>(
      message = "Base URL should not have query parameters. Found: https://urls/base/?q=%2F"
    ) {
      UrlsTestService(
        URLBuilder(BASE_URL).apply { parameters.append("q", "/") }.build(),
        httpClient,
        emptyList()
      )
    }
  }

  @Test fun `Base URL can't have a non-empty fragment`() = runHttpTest {
    assertThrows<IllegalArgumentException>(
      message = "Base URL fragment should be empty. Found: https://urls/base/#/"
    ) {
      UrlsTestService(
        URLBuilder(BASE_URL).apply { fragment = "/" }.build(),
        httpClient,
        emptyList()
      )
    }
  }

  @Test fun `Base URL can't have a trailing question mark`() = runHttpTest {
    assertThrows<IllegalArgumentException>(
      message = "Base URL should end in '/'. Found: https://urls/base/?"
    ) {
      UrlsTestService(
        URLBuilder(BASE_URL).apply { trailingQuery = true }.build(),
        httpClient,
        emptyList()
      )
    }
  }

  @Test fun `@Path is not allowed to perform path traversal`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    suspend fun assertForbiddenPathTraversal(value: String) {
      assertThrows<IllegalArgumentException>(
        message = "@Path arguments should not introduce path traversal. Found: '$value'"
      ) {
        service.stringPathParameters(pathParam1 = "v1", pathParam2 = value)
      }
    }

    assertForbiddenPathTraversal(".")
    assertForbiddenPathTraversal("%2E")
    assertForbiddenPathTraversal("%2e")
    assertForbiddenPathTraversal("..")
    assertForbiddenPathTraversal("%2E.")
    assertForbiddenPathTraversal("%2e.")
    assertForbiddenPathTraversal(".%2E")
    assertForbiddenPathTraversal(".%2e")
    assertForbiddenPathTraversal("%2E%2e")
    assertForbiddenPathTraversal("%2e%2E")
    assertForbiddenPathTraversal("./a")
    assertForbiddenPathTraversal("a/.")
    assertForbiddenPathTraversal("../a")
    assertForbiddenPathTraversal("a/..")
    assertForbiddenPathTraversal("a/../b")
    assertForbiddenPathTraversal("a/%2e%2E/b")

    // ensuring that dollar signs in the URL template don't break this behavior
    assertThrows<IllegalArgumentException>(
      message = "@Path arguments should not introduce path traversal. Found: '\$a/../b\$'"
    ) {
      service.pathContainingDollarSigns("a/../b")
    }
  }

  @Test fun `Path traversal dots are allowed in @Path if they are not a full segment`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a/b/.../")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a/b/c../")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a/b/c..d/")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a/b/..c/")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a/b/..%5C../")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a.b/")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = "a..b/")
    service.stringPathParameters(pathParam1 = "v1", pathParam2 = " .. /")

    service.nonFullSegmentPathParameter("..")
    service.nonFullSegmentPathParameter(".")

    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces(".")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2E")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2e")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("..")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2E.")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2e.")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces(".%2E")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces(".%2e")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2E%2e")
    service.pathParameterInSegmentContainingLotsOfExtraWhitespaces("%2e%2E")

    @Suppress("LocalVariableName")
    val whitespaces_100 = (1..100).joinToString("") { "%20" }
    assertHttpLogMatches(
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a/b/.../") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a/b/c../") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a/b/c..d/") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a/b/..c/") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a/b/..%5C../") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a.b/") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/a..b/") },
      { hasUrl("https://urls/base/string/path/parameters/p1/v1/p2/%20..%20/") },

      { hasUrl("https://urls/base/fruit/all..s") },
      { hasUrl("https://urls/base/fruit/all.s") },

      { hasUrl("https://urls/base/path/%20.$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2E$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2e$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20..$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2E.$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2e.$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20.%2E$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20.%2e$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2E%2e$whitespaces_100/etc") },
      { hasUrl("https://urls/base/path/%20%2e%2E$whitespaces_100/etc") },
    )
  }

  @Test fun `Dollar signs in path are correctly escaped`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.pathContainingDollarSigns(parameter = "$")
    assertHttpLogMatches { hasUrl("https://urls/base/\$pa\$th\$/\$cont\$aining\$/\$dol\$lar\$/\$si\$gns\$/\$\$\$/") }
  }

  @Test fun `All escape sequences in path are correctly handled`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)
    service.pathContainingAllEscapeSequences(parameter = "$")
    assertHttpLogMatches {
      hasUrl("https://urls/base/\$pa%09th%08/%0Acont%0Daining'/%5Ca%22ll%22/%5Ces'cape\$/seq%08ue%0Dnces%09/%0A\$\$/")
    }
  }

  @Test fun `@Query iterables with non-null values`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    // [10]
    service.iterableOfStringQueryParametersWithSameName(emptyList())
    // [10, 20, 30]
    service.iterableOfStringQueryParametersWithSameName(listOf("20", "30"))
    // [10]
    service.iterableOfAnyQueryParametersWithSameName(emptyList())
    // [10, 100]
    service.iterableOfAnyQueryParametersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "100"
        },
      )
    )

    // [10]
    service.collectionOfStringQueryParametersWithSameName(emptyList())
    // [10, 20, 30]
    service.collectionOfStringQueryParametersWithSameName(listOf("20", "30"))
    // [10]
    service.collectionOfAnyQueryParametersWithSameName(emptyList())
    // [10, 100]
    service.collectionOfAnyQueryParametersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "100"
        },
      )
    )

    // [10]
    service.listOfStringQueryParametersWithSameName(emptyList())
    // [10, 20, 30]
    service.listOfStringQueryParametersWithSameName(listOf("20", "30"))
    // [10]
    service.listOfAnyQueryParametersWithSameName(emptyList())
    // [10, 100]
    service.listOfAnyQueryParametersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "100"
        },
      )
    )

    // [10]
    service.setOfStringQueryParametersWithSameName(emptySet())
    // [10, 20, 30]
    service.setOfStringQueryParametersWithSameName(setOf("20", "30"))
    // [10]
    service.setOfAnyQueryParametersWithSameName(emptySet())
    // [10, 100]
    service.setOfAnyQueryParametersWithSameName(
      setOf(
        object : Any() {
          override fun toString() = "100"
        },
      )
    )

    assertHttpLogMatches(
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=100") },

      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=100") },

      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=100") },

      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=100") },
    )
  }

  @Test fun `@QueryName iterables with non-null values`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    // [key=value, name1]
    service.iterableOfStringQueryNames(emptyList())
    // [key=value, name1, name2, name3]
    service.iterableOfStringQueryNames(listOf("name2", "name3"))
    // [key=value, name1]
    service.iterableOfAnyQueryNames(emptyList())
    // [key=value, name1, name100]
    service.iterableOfAnyQueryNames(
      listOf(
        object : Any() {
          override fun toString() = "name100"
        },
      )
    )

    // [key=value, name1]
    service.collectionOfStringQueryNames(emptyList())
    // [key=value, name1, name2, name3]
    service.collectionOfStringQueryNames(listOf("name2", "name3"))
    // [key=value, name1]
    service.collectionOfAnyQueryNames(emptyList())
    // [key=value, name1, name100]
    service.collectionOfAnyQueryNames(
      listOf(
        object : Any() {
          override fun toString() = "name100"
        },
      )
    )

    // [key=value, name1]
    service.listOfStringQueryNames(emptyList())
    // [key=value, name1, name2, name3]
    service.listOfStringQueryNames(listOf("name2", "name3"))
    // [key=value, name1]
    service.listOfAnyQueryNames(emptyList())
    // [key=value, name1, name100]
    service.listOfAnyQueryNames(
      listOf(
        object : Any() {
          override fun toString() = "name100"
        },
      )
    )

    // [key=value, name1]
    service.setOfStringQueryNames(emptySet())
    // [key=value, name1, name2, name3]
    service.setOfStringQueryNames(setOf("name2", "name3"))
    // [key=value, name1]
    service.setOfAnyQueryNames(emptySet())
    // [key=value, name1, name100]
    service.setOfAnyQueryNames(
      setOf(
        object : Any() {
          override fun toString() = "name100"
        },
      )
    )

    assertHttpLogMatches(
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name2&name3") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name100") },

      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name2&name3") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name100") },

      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name2&name3") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name100") },

      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name2&name3") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1") },
      { hasUrl("https://urls/base/multiple/query/names?key=value&name1&name100") },
    )
  }

  @Test fun `@Query and @QueryName iterables with nullable values`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    // [10]
    service.queryParameterIterableNullableTypes(
      null,
      null,
      listOf(null),
      setOf(null),
      null,
      null,
      null,
      null,
      listOf(null),
      setOf(null),
      null,
      null,
      null,
      null,
      null,
      null,
      null
    )
    // [10, 20, 30, 40, 50, 60, 70]
    service.queryParameterIterableNullableTypes(
      // @Query
      null,
      null,
      listOf("20", "30"),
      setOf(null, "40"),
      listOf("50", null, "60"),
      setOf(null, "70", null),
      // @QueryName
      null,
      null,
      listOf("name1", "name2"),
      setOf(null, "name3"),
      listOf("name4", null, "name5"),
      setOf(null, "name6", null),
      null,
      // @QueryMap
      mapOf(
        "q" to null,
      ),
      mapOf(
        "q" to null,
      ),
      listOf(
        "q" to listOf("80"),
        "q" to null
      ),
      listOf(
        "q" to null,
        "q" to setOf("90", null)
      )
    )

    assertHttpLogMatches(
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10") },
      { hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=20&q=30&q=40&q=50&q=60&q=70&q=80&q=90&name1&name2&name3&name4&name5&name6") },
    )
  }

  @Test fun `@QueryMap of Strings`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryParameterStringMap(emptyMap())
    service.queryParameterStringMap(mapOf("q" to "1"))
    service.queryParameterStringMap(mapOf("q" to "1", "r" to "2"))

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2")
      }
    )
  }

  @Test fun `@QueryMap of Any`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryParameterAnyMap(emptyMap())
    service.queryParameterAnyMap(
      mapOf(
        "q" to object : Any() {
          override fun toString(): String = "1"
        }
      )
    )
    service.queryParameterAnyMap(
      mapOf(
        "q" to object : Any() {
          override fun toString(): String = "1"
        },
        "r" to object : Any() {
          override fun toString(): String = "2"
        }
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2")
      }
    )
  }

  @Test fun `@QueryMap of String Iterable`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryParameterMapOfIterableString(emptyMap())
    service.queryParameterMapOfIterableString(
      mapOf(
        "q" to listOf("1"),
        "r" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `@QueryMap of Any Iterable`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryParameterMapOfIterableAny(emptyMap())
    service.queryParameterMapOfIterableAny(
      mapOf(
        "q" to listOf(
          object : Any() {
            override fun toString() = "1"
          }
        ),
        "r" to listOf(
          object : Any() {
            override fun toString() = "2"
          },
          object : Any() {
            override fun toString() = "3"
          }
        )
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `StringValues @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapStringValues(StringValues.Empty)
    service.queryMapStringValues(Parameters.Empty)
    service.queryMapStringValues(
      parametersOf(
        "q" to listOf("1"),
        "r" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `Parameters @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapParameters(Parameters.Empty)
    service.queryMapParameters(
      parametersOf(
        "q" to listOf("1"),
        "r" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `Headers @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapHeaders(Headers.Empty)
    service.queryMapHeaders(
      headersOf(
        "q" to listOf("1"),
        "r" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `Key-value Pairs with String values @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapListOfPairsWithStringValues(emptyList())
    service.queryMapListOfPairsWithStringValues(
      listOf(
        "q" to "1",
        "r" to "2",
        "r" to "3"
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `Key-value Pairs with Any values @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapListOfPairsWithAnyValues(emptyList())
    service.queryMapListOfPairsWithAnyValues(
      listOf(
        "q" to object : Any() {
          override fun toString() = "1"
        },
        "r" to object : Any() {
          override fun toString() = "2"
        },
        "r" to object : Any() {
          override fun toString() = "3"
        }
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3")
      }
    )
  }

  @Test fun `Key-value Pairs with Iterable values @QueryMap`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryMapListOfPairsWithIterableValues(emptyList())
    service.queryMapListOfPairsWithIterableValues(
      listOf(
        "q" to listOf("1"),
        "q" to emptyList(),
        "r" to listOf("2", "3"),
        "r" to listOf(
          object : Any() {
            override fun toString() = "4"
          }
        )
      )
    )

    assertHttpLogMatches(
      {
        hasUrl("https://urls/base/get?q=static")
      },
      {
        hasUrl("https://urls/base/get?q=static&q=1&r=2&r=3&r=4")
      }
    )
  }

  @Test fun `@QueryMap iterable values with null items`() = runHttpTest {
    val service = UrlsTestService(BASE_URL, httpClient)

    service.queryParameterIterableNullableTypes(
      null,
      null,
      emptyList(),
      emptySet(),
      null,
      null,
      null,
      null,
      emptyList(),
      emptySet(),
      null,
      null,
      ParametersBuilder().apply {
        append("q", "a")
        appendAll("q", listOf("b", "c"))
      }.build(),
      mapOf(
        "q" to listOf(null, null),
        "r" to listOf(null, "d")
      ),
      mapOf(
        "q" to listOf(null, "e", null),
        "r" to setOf("f", null)
      ),
      listOf("q" to listOf(null, "g", null)),
      listOf("r" to setOf("h", null))
    )

    assertHttpLogMatches {
      hasUrl("https://urls/base/multiple/query/parameters/same/name?q=10&q=a&q=b&q=c&q=e&q=g&r=d&r=f&r=h")
    }
  }
}
