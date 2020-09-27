package connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import io.ktor.http.HttpHeaders

data class ServiceDescription(
    val name: String,
    val functions: List<Function>,
    val parentInterface: ClassName
) {
    sealed class Function {
        abstract val name: String
        abstract val parameters: Map<String, TypeName>

        data class Http(
            override val name: String,
            override val parameters: Map<String, TypeName>,
            val method: String,
            val url: Url,
            val headers: List<StringValue>,
            val requestBody: HttpRequestBody?,
            val returnType: TypeName
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

    sealed class Url {
        abstract val dynamicQueryParameters: List<StringValue.Dynamic>

        data class Template(
            val value: String,
            val type: UrlType,
            val parameterNameReplacementMappings: Map<String, String>,
            override val dynamicQueryParameters: List<StringValue.Dynamic>
        ) : Url()

        data class Dynamic(
            val parameterName: String,
            override val dynamicQueryParameters: List<StringValue.Dynamic>
        ) : Url()
    }

    data class HttpRequestBody(val parameterName: String, val contentType: String)
}

enum class UrlType { ABSOLUTE, FULL, PROTOCOL_RELATIVE, RELATIVE }

sealed class StringValue {
    abstract val name: String

    data class Static(
        override val name: String,
        val value: String
    ) : StringValue()

    data class Dynamic(
        override val name: String,
        val parameterName: String
    ) : StringValue()
}
