package dev.aoddon.connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import io.ktor.http.HttpHeaders

public data class ServiceDescription(
  public val name: String,
  public val functions: List<Function>,
  public val parentInterface: ClassName
) {
  public sealed class Function {
    public abstract val name: String
    public abstract val parameters: Map<String, TypeName>

    public data class Http(
      override val name: String,
      override val parameters: Map<String, TypeName>,
      public val method: String,
      public val url: Url,
      public val headers: List<Header>,
      public val content: HttpContent?,
      public val streamingLambdaProviderParameter: String?,
      public val returnType: TypeName
    ) : Function() {
      public sealed class Header {
        public data class SingleStatic(val name: String, val value: String) : Header()
        public data class SingleDynamic(val name: String, val valueProviderParameter: String) : Header()
        public data class DynamicIterable(val name: String, val valueProviderParameter: String) : Header()
        public data class DynamicMap(val type: MapType, val valueProviderParameter: String) : Header()
      }

      init {
        validate()
      }
    }
  }

  public sealed class Url {
    public abstract val dynamicQueryParameters: List<QueryParameter>

    public data class Template(
      public val value: String,
      public val type: UrlType,
      public val valueProviderParametersByReplaceBlock: Map<String, String>,
      override val dynamicQueryParameters: List<QueryParameter>
    ) : Url()

    public data class Dynamic(
      val valueProviderParameter: String,
      override val dynamicQueryParameters: List<QueryParameter>
    ) : Url()

    public sealed class QueryParameter {
      public data class Single(val name: String, val valueProviderParameter: String) : QueryParameter()
      public data class Iterable(val name: String, val valueProviderParameter: String) : QueryParameter()
      public data class Map(val type: MapType, val valueProviderParameter: String) : QueryParameter()
    }
  }

  public sealed class HttpContent {
    public data class Body(
      public val valueProviderParameter: String,
      public val contentType: String
    ) : HttpContent()

    public data class FormUrlEncoded(val fields: List<FieldContent>) : HttpContent() {
      public sealed class FieldContent {
        public data class Single(val name: String, val valueProviderParameter: String) : FieldContent()
        public data class Iterable(val name: String, val valueProviderParameter: String) : FieldContent()
        public data class Map(val type: MapType, val valueProviderParameter: String) : FieldContent()
      }

      init {
        validate()
      }
    }

    public data class Multipart(val subtype: String, val parts: List<PartContent>) : HttpContent() {
      public sealed class PartContent {
        public data class Single(val valueProviderParameter: String, val metadata: PartMetadata?) : PartContent()
        public data class Iterable(val valueProviderParameter: String, val metadata: PartMetadata?) : PartContent()
        public data class Map(
          val type: MapType,
          val valueProviderParameter: String,
          val contentType: String
        ) : PartContent()
      }

      public data class PartMetadata(
        public val contentType: String,
        public val formFieldName: String?
      )

      init {
        validate()
      }
    }
  }

  public sealed class MapType {
    public data class Map(val hasIterableValues: Boolean) : MapType()
    public data class IterableKeyValuePairs(val hasIterableValues: Boolean) : MapType()
    public object KtorStringValues : MapType() {
      override fun toString(): String = "KtorStringValues"
    }
  }
}

public enum class UrlType { ABSOLUTE, FULL, PROTOCOL_RELATIVE, RELATIVE }

private fun ServiceDescription.Function.Http.validate() {
  check(
    headers.none { content ->
      when (content) {
        is ServiceDescription.Function.Http.Header.SingleStatic -> {
          content.name == HttpHeaders.ContentType || content.name == HttpHeaders.ContentLength
        }
        is ServiceDescription.Function.Http.Header.SingleDynamic -> {
          content.name == HttpHeaders.ContentType || content.name == HttpHeaders.ContentLength
        }
        is ServiceDescription.Function.Http.Header.DynamicIterable -> {
          content.name == HttpHeaders.ContentType || content.name == HttpHeaders.ContentLength
        }
        is ServiceDescription.Function.Http.Header.DynamicMap -> false
      }
    }
  ) {
    "'headers' must not contain ${HttpHeaders.ContentType} or ${HttpHeaders.ContentLength}. Found: $headers"
  }
}

private fun ServiceDescription.HttpContent.FormUrlEncoded.validate() {
  check(fields.isNotEmpty()) { "'fields' should not be empty." }
}

private fun ServiceDescription.HttpContent.Multipart.validate() {
  check(parts.isNotEmpty()) { "'parts' should not be empty." }
}
