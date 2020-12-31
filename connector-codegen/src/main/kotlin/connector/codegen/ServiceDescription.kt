package connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
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
      public val headers: List<StringValue>,
      public val requestBody: HttpRequestBody?,
      public val returnType: TypeName
    ) : Function() {
      init {
        if (headers.any { it.name == HttpHeaders.ContentType || it.name == HttpHeaders.ContentLength }) {
          throw IllegalArgumentException(
            "'headers' must not contain ${HttpHeaders.ContentType} " +
              "and ${HttpHeaders.ContentLength}. Found: $headers"
          )
        }
        if (parameters.values.any { it !is ClassName && it !is ParameterizedTypeName }) {
          throw IllegalArgumentException(
            "'parameters' types must be either 'ClassName's or 'ParameterizedTypeName's. " +
              "Found: ${parameters.map { "${it::class.simpleName}($it)" }}"
          )
        }
        if (returnType !is ClassName && returnType !is ParameterizedTypeName) {
          throw IllegalArgumentException(
            "'returnType' must be either a 'ClassName' or a 'ParameterizedTypeName'. " +
              "Found: ${returnType::class.simpleName}($returnType)"
          )
        }
      }
    }
  }

  public sealed class Url {
    public abstract val dynamicQueryParameters: List<StringValue.Dynamic>

    public data class Template(
      public val value: String,
      public val type: UrlType,
      public val parameterNameReplacementMappings: Map<String, String>,
      override val dynamicQueryParameters: List<StringValue.Dynamic>
    ) : Url()

    public data class Dynamic(
      val parameterName: String,
      override val dynamicQueryParameters: List<StringValue.Dynamic>
    ) : Url()
  }

  public data class HttpRequestBody(public val parameterName: String, public val contentType: String)
}

public enum class UrlType { ABSOLUTE, FULL, PROTOCOL_RELATIVE, RELATIVE }

public sealed class StringValue {
  public abstract val name: String

  public data class Static(
    override val name: String,
    public val value: String
  ) : StringValue()

  public data class Dynamic(
    override val name: String,
    public val parameterName: String
  ) : StringValue()
}