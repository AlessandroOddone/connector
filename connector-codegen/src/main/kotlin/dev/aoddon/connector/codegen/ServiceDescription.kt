package dev.aoddon.connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

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

        public data class DynamicIterable(
          val name: String,
          val type: IterableType,
          val valueProviderParameter: String,
        ) : Header()

        public data class DynamicMap(val type: MapType, val valueProviderParameter: String) : Header()
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
      public sealed class Single : QueryParameter() {
        public data class HasValue(val name: String, val valueProviderParameter: String) : Single()
        public data class NoValue(val nameProviderParameter: String) : Single()
      }

      public sealed class Iterable : QueryParameter() {
        public abstract val type: IterableType

        public data class HasValue(
          val name: String,
          override val type: IterableType,
          val valueProviderParameter: String,
        ) : Iterable()

        public data class NoValue(
          override val type: IterableType,
          val nameProviderParameter: String
        ) : Iterable()
      }

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

        public data class Iterable(
          val name: String,
          val type: IterableType,
          val valueProviderParameter: String
        ) : FieldContent()

        public data class Map(val type: MapType, val valueProviderParameter: String) : FieldContent()
      }
    }

    public data class Multipart(val subtype: String, val parts: List<PartContent>) : HttpContent() {
      public sealed class PartContent {
        public data class Single(val valueProviderParameter: String, val metadata: PartMetadata?) : PartContent()

        public data class Iterable(
          val type: IterableType,
          val valueProviderParameter: String,
          val metadata: PartMetadata?
        ) : PartContent()

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
    }
  }

  public data class IterableType(val valueTypeName: TypeName)

  public sealed class MapType {
    public data class Map(val valueType: ValueType) : MapType()

    public data class IterableKeyValuePairs(val valueType: ValueType) : MapType()

    public object KtorStringValues : MapType() {
      override fun toString(): String = "KtorStringValues"
    }

    public sealed class ValueType {
      public abstract val typeName: TypeName

      public data class Single(override val typeName: TypeName) : ValueType()
      public data class Iterable(override val typeName: TypeName, val valueTypeName: TypeName) : ValueType()
    }
  }
}

public enum class UrlType { ABSOLUTE, FULL, PROTOCOL_RELATIVE, RELATIVE }
