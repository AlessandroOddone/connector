package dev.aoddon.connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.ktor.http.HttpHeaders

public data class ServiceDescription(
  public val name: String,
  public val functions: List<Function>,
  public val parentInterface: ClassName
) {
  public companion object {
    public val SUPPORTED_ITERABLE_TYPES: List<ClassName> = listOf(
      Collection::class.asClassName(),
      Iterable::class.asClassName(),
      List::class.asClassName(),
      Set::class.asClassName(),
    )
  }

  public sealed class Function {
    public abstract val name: String
    public abstract val parameters: Map<String, TypeName>

    public data class Http(
      override val name: String,
      override val parameters: Map<String, TypeName>,
      public val method: String,
      public val url: Url,
      public val headers: List<HeaderContent>,
      public val content: HttpContent?,
      public val returnType: TypeName
    ) : Function() {
      public sealed class HeaderContent {
        public data class Static(val name: String, val value: String) : HeaderContent()
        public data class Parameter(val parameterName: String, val headerName: String) : HeaderContent()
        public data class Map(val parameterName: String) : HeaderContent()
      }

      init {
        validate()
      }
    }
  }

  public sealed class Url {
    public abstract val dynamicQueryParameters: List<QueryContent>

    public data class Template(
      public val value: String,
      public val type: UrlType,
      public val parameterNamesByReplaceBlock: Map<String, String>,
      override val dynamicQueryParameters: List<QueryContent>
    ) : Url()

    public data class Dynamic(
      val parameterName: String,
      override val dynamicQueryParameters: List<QueryContent>
    ) : Url()

    public sealed class QueryContent {
      public data class Parameter(val parameterName: String, val key: String) : QueryContent()
      public data class Map(val parameterName: String) : QueryContent()
    }
  }

  public sealed class HttpContent {
    public data class Body(
      public val parameterName: String,
      public val contentType: String
    ) : HttpContent()

    public data class FormUrlEncoded(val fields: List<FieldContent>) : HttpContent() {
      public sealed class FieldContent {
        public data class Parameter(val parameterName: String, val fieldName: String) : FieldContent()
        public data class Map(val parameterName: String) : FieldContent()
      }
    }

    public data class Multipart(val subtype: String, val parts: List<PartContent>) : HttpContent() {
      public sealed class PartContent {
        public data class Parameter(val parameterName: String, val metadata: PartMetadata?) : PartContent()
        public data class Iterable(val parameterName: String, val metadata: PartMetadata?) : PartContent()
        public data class Map(val parameterName: String, val contentType: String) : PartContent()
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
}

public enum class UrlType { ABSOLUTE, FULL, PROTOCOL_RELATIVE, RELATIVE }

private fun ServiceDescription.Function.Http.validate() {
  check(
    headers.none { content ->
      when (content) {
        is ServiceDescription.Function.Http.HeaderContent.Static -> {
          content.name == HttpHeaders.ContentType || content.name == HttpHeaders.ContentLength
        }
        is ServiceDescription.Function.Http.HeaderContent.Parameter -> {
          content.headerName == HttpHeaders.ContentType || content.headerName == HttpHeaders.ContentLength
        }
        is ServiceDescription.Function.Http.HeaderContent.Map -> false
      }
    }
  ) {
    "'headers' must not contain ${HttpHeaders.ContentType} or ${HttpHeaders.ContentLength}. Found: $headers"
  }
  check(parameters.values.all { it is ClassName || it is ParameterizedTypeName }) {
    "'parameters' must be either 'ClassName's or 'ParameterizedTypeName's. Found: $parameters"
  }
  check(returnType is ClassName || returnType is ParameterizedTypeName) {
    "'returnType' must be either a 'ClassName' or a 'ParameterizedTypeName'. Found: $returnType"
  }
}

private fun ServiceDescription.HttpContent.FormUrlEncoded.validate() {
  check(fields.isNotEmpty()) { "'fields' should not be empty." }
}

private fun ServiceDescription.HttpContent.Multipart.validate() {
  check(parts.isNotEmpty()) { "'parts' should not be empty." }
}
