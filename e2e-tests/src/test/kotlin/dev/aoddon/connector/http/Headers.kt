package dev.aoddon.connector.http

import dev.aoddon.connector.Service
import dev.aoddon.connector.util.assertHttpLogMatches
import dev.aoddon.connector.util.runHttpTest
import io.ktor.client.utils.buildHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parametersOf
import io.ktor.util.StringValues
import org.junit.Test

private val BASE_URL = Url("https://headers/")

@Service interface HeadersTestService {
  @GET("get")
  @Headers("header: value")
  suspend fun singleStaticHeader()

  @GET("get")
  @Headers(
    "header1: value1",
    "header2: value2"
  )
  suspend fun multipleStaticHeaders()

  @GET("get")
  @Headers(
    "header1:value1",
    "header2: value2",
    "header3   :    value3",
    "header4 :value4"
  )
  suspend fun multipleStaticHeadersWithDifferentSpacingAroundColon()

  @GET("get")
  suspend fun dynamicStringHeader(
    @Header("header") h: String
  )

  @GET("get")
  suspend fun dynamicNullableAnyHeader(
    @Header("header") h: Any?
  )

  @GET("get")
  suspend fun multipleDynamicHeaders(
    @Header("header1") h1: Any?,
    @Header("header2") h2: Any?,
    @Header("header3") h3: Any?,
  )

  @GET("get")
  @Headers("header:static")
  suspend fun multipleHeadersWithSameName(
    @Header("header") h1: String,
    @Header("header") h2: String,
  )

  @GET("get")
  @Headers("header:static")
  suspend fun iterableOfStringHeadersWithSameName(@Header("header") h: Iterable<String>)

  @GET("get")
  @Headers("header:static")
  suspend fun iterableOfAnyHeadersWithSameName(@Header("header") h: Iterable<Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun collectionOfStringHeadersWithSameName(@Header("header") h: Collection<Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun collectionOfAnyHeadersWithSameName(@Header("header") h: Collection<Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun listOfStringHeadersWithSameName(@Header("header") h: List<String>)

  @GET("get")
  @Headers("header:static")
  suspend fun listOfAnyHeadersWithSameName(@Header("header") h: List<Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun setOfStringHeadersWithSameName(@Header("header") h: Set<String>)

  @GET("get")
  @Headers("header:static")
  suspend fun setOfAnyHeadersWithSameName(@Header("header") h: Set<Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun stringMap(@HeaderMap map: Map<String, String>)

  @GET("get")
  @Headers("header:static")
  suspend fun anyMap(@HeaderMap map: Map<String, Any>)

  @GET("get")
  @Headers("header:static")
  suspend fun mapOfIterableString(@HeaderMap map: Map<String, Iterable<String>>)

  @GET("get")
  @Headers("header:static")
  suspend fun mapOfIterableAny(@HeaderMap map: Map<String, Iterable<Any>>)

  @GET("get")
  @Headers("header:static")
  suspend fun stringValues(@HeaderMap stringValues: StringValues)

  @GET("get")
  @Headers("header:static")
  suspend fun parameters(@HeaderMap parameters: Parameters)

  @GET("get")
  @Headers("header:static")
  suspend fun ktorHeaders(@HeaderMap headers: io.ktor.http.Headers)

  @POST("post")
  @Headers("header:static")
  suspend fun listOfPairsWithStringValues(@HeaderMap entries: List<Pair<String, String>>)

  @POST("post")
  @Headers("header:static")
  suspend fun listOfPairsWithAnyValues(@HeaderMap entries: List<Pair<String, Any>>)

  @POST("post")
  @Headers("header:static")
  suspend fun listOfPairsWithIterableValues(@HeaderMap entries: List<Pair<String, Iterable<Any>>>)

  @GET("get")
  @Headers("header:static")
  suspend fun iterableNullableTypes(
    @Header("header") h1: Iterable<String>?,
    @Header("header") h2: Collection<Any>?,
    @Header("header") h3: List<String?>,
    @Header("header") h4: Set<Any?>,
    @Header("header") h5: List<String?>?,
    @Header("header") h6: Iterable<Any?>?,
    @HeaderMap stringValues: StringValues?,
    @HeaderMap map1: Map<String, List<String?>?>?,
    @HeaderMap map2: Map<String, Collection<Any?>?>?,
    @HeaderMap map3: List<Pair<String, Collection<String?>?>>?,
    @HeaderMap map4: List<Pair<String, Collection<Any?>?>>?
  )
}

class HeadersTest {
  @Test fun `@Headers defining a single static header`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.singleStaticHeader()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `@Headers defining multiple static headers`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header2", "value2")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleStaticHeaders()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `@Headers ignores whitespaces around colon`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header2", "value2")
      append("header3", "value3")
      append("header4", "value4")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleStaticHeadersWithDifferentSpacingAroundColon()
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `String @Header argument is used as the header value`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicStringHeader(h = "value")
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `'toString' of object @Header argument is used as the header value`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header", "value")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicNullableAnyHeader(
      h = object : Any() {
        override fun toString() = "value"
      }
    )
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `If the @Header argument is null, the header is omitted`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.dynamicNullableAnyHeader(h = null)
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `Multiple @Header parameters`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = buildHeaders {
      append("header1", "value1")
      append("header3", "value3")
      append("Accept-Charset", "UTF-8")
      append("Accept", "*/*")
    }
    service.multipleDynamicHeaders(
      h1 = object : Any() {
        override fun toString() = "value1"
      },
      h2 = null,
      h3 = object : Any() {
        override fun toString() = "value3"
      }
    )
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `Multiple @Header parameters with the same name`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)
    val expectedHeaders = headersOf(
      "header" to listOf("static", "dynamic1", "dynamic2"),
      "Accept-Charset" to listOf("UTF-8"),
      "Accept" to listOf("*/*")
    )
    service.multipleHeadersWithSameName(h1 = "dynamic1", h2 = "dynamic2")
    assertHttpLogMatches { hasRequestHeaders(expectedHeaders) }
  }

  @Test fun `@Header iterables with non-null values`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    // [static]
    service.iterableOfStringHeadersWithSameName(emptyList())
    // [static, d1, d2]
    service.iterableOfStringHeadersWithSameName(listOf("d1", "d2"))
    // [static]
    service.iterableOfAnyHeadersWithSameName(emptyList())
    // [static, d10]
    service.iterableOfAnyHeadersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "d10"
        },
      )
    )

    // [static]
    service.collectionOfStringHeadersWithSameName(emptyList())
    // [static, d1, d2]
    service.collectionOfStringHeadersWithSameName(listOf("d1", "d2"))
    // [static]
    service.collectionOfAnyHeadersWithSameName(emptyList())
    // [static, d10]
    service.collectionOfAnyHeadersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "d10"
        },
      )
    )

    // [static]
    service.listOfStringHeadersWithSameName(emptyList())
    // [static, d1, d2]
    service.listOfStringHeadersWithSameName(listOf("d1", "d2"))
    // [static]
    service.listOfAnyHeadersWithSameName(emptyList())
    // [static, d10]
    service.listOfAnyHeadersWithSameName(
      listOf(
        object : Any() {
          override fun toString() = "d10"
        },
      )
    )

    // [static]
    service.setOfStringHeadersWithSameName(emptySet())
    // [static, d1, d2]
    service.setOfStringHeadersWithSameName(setOf("d1", "d2"))
    // [static]
    service.setOfAnyHeadersWithSameName(emptySet())
    // [static, d10]
    service.setOfAnyHeadersWithSameName(
      setOf(
        object : Any() {
          override fun toString() = "d10"
        },
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d1", "d2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d10"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },

      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d1", "d2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d10"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },

      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d1", "d2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d10"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },

      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d1", "d2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d10"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
    )
  }

  @Test fun `@Header iterables with nullable values`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    // [static]
    service.iterableNullableTypes(
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
    // [static, d1, d2, d3, d4, d5, d6, d7, d8]
    service.iterableNullableTypes(
      null,
      null,
      listOf("d1", "d2"),
      setOf(null, "d3"),
      listOf("d4", null, "d5"),
      setOf(null, "d6", null),
      null,
      mapOf(
        "header" to null,
      ),
      mapOf(
        "header" to null,
      ),
      listOf(
        "header" to listOf("d7"),
        "header" to null
      ),
      listOf(
        "header" to null,
        "header" to setOf("d8", null)
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
    )
  }

  @Test fun `@HeaderMap of Strings`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.stringMap(emptyMap())
    service.stringMap(mapOf("header" to "1"))
    service.stringMap(mapOf("header" to "1", "otherHeader" to "2"))

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `@HeaderMap of Any`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.anyMap(emptyMap())
    service.anyMap(
      mapOf(
        "header" to object : Any() {
          override fun toString(): String = "1"
        }
      )
    )
    service.anyMap(
      mapOf(
        "header" to object : Any() {
          override fun toString(): String = "1"
        },
        "otherHeader" to object : Any() {
          override fun toString(): String = "2"
        }
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `@HeaderMap of String Iterable`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.mapOfIterableString(emptyMap())
    service.mapOfIterableString(
      mapOf(
        "header" to listOf("1"),
        "otherHeader" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `@HeaderMap of Any Iterable`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.mapOfIterableAny(emptyMap())
    service.mapOfIterableAny(
      mapOf(
        "header" to listOf(
          object : Any() {
            override fun toString() = "1"
          }
        ),
        "otherHeader" to listOf(
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
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `StringValues @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.stringValues(StringValues.Empty)
    service.stringValues(Parameters.Empty)
    service.stringValues(
      parametersOf(
        "header" to listOf("1"),
        "otherHeader" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `Parameters @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.parameters(Parameters.Empty)
    service.parameters(
      parametersOf(
        "header" to listOf("1"),
        "otherHeader" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `Ktor Headers @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.ktorHeaders(io.ktor.http.Headers.Empty)
    service.ktorHeaders(
      headersOf(
        "header" to listOf("1"),
        "otherHeader" to listOf("2", "3")
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `Key-value Pairs with String values @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.listOfPairsWithStringValues(emptyList())
    service.listOfPairsWithStringValues(
      listOf(
        "header" to "1",
        "otherHeader" to "2",
        "otherHeader" to "3"
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `Key-value Pairs with Any values @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.listOfPairsWithAnyValues(emptyList())
    service.listOfPairsWithAnyValues(
      listOf(
        "header" to object : Any() {
          override fun toString() = "1"
        },
        "otherHeader" to object : Any() {
          override fun toString() = "2"
        },
        "otherHeader" to object : Any() {
          override fun toString() = "3"
        }
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `Key-value Pairs with Iterable values @HeaderMap`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.listOfPairsWithIterableValues(emptyList())
    service.listOfPairsWithIterableValues(
      listOf(
        "header" to listOf("1"),
        "header" to emptyList(),
        "otherHeader" to listOf("2", "3"),
        "otherHeader" to listOf(
          object : Any() {
            override fun toString() = "4"
          }
        )
      )
    )

    assertHttpLogMatches(
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      },
      {
        hasRequestHeaders(
          headersOf(
            "header" to listOf("static", "1"),
            "otherHeader" to listOf("2", "3", "4"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept" to listOf("*/*")
          )
        )
      }
    )
  }

  @Test fun `@HeaderMap iterable values with null items`() = runHttpTest {
    val service = HeadersTestService(BASE_URL, httpClient)

    service.iterableNullableTypes(
      null,
      null,
      emptyList(),
      emptySet(),
      null,
      null,
      buildHeaders {
        append("header", "a")
        appendAll("header", listOf("b", "c"))
      },
      mapOf(
        "header" to listOf(null, null),
        "otherHeader" to listOf(null, "d")
      ),
      mapOf(
        "header" to listOf(null, "e", null),
        "otherHeader" to setOf("f", null)
      ),
      listOf(
        "header" to null,
        "otherHeader" to null,
      ),
      listOf(
        "header" to null,
        "header" to listOf(null, "g", null),
        "otherHeader" to listOf("h", null),
        "otherHeader" to null,
      )
    )

    assertHttpLogMatches {
      hasRequestHeaders(
        headersOf(
          "header" to listOf("static", "a", "b", "c", "e", "g"),
          "otherHeader" to listOf("d", "f", "h"),
          "Accept-Charset" to listOf("UTF-8"),
          "Accept" to listOf("*/*")
        )
      )
    }
  }
}
