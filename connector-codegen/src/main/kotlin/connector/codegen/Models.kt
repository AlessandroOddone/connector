package connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import io.ktor.http.HttpHeaders

data class Service(
    val name: String,
    val functions: List<Function>,
    val existingParentInterface: ClassName?
) {
    sealed class Function {
        abstract val name: String
        abstract val parameters: Map<String, TypeName>

        data class Http(
            override val name: String,
            override val parameters: Map<String, TypeName>,
            val method: HttpMethod,
            val relativeUrl: RelativeUrl,
            val headers: List<Pair<String, StringValue>>,
            val requestBody: RequestBody?,
            val returnType: TypeName
        ) : Function() {
            init {
                if (headers.any { it.first == HttpHeaders.ContentType || it.first == HttpHeaders.ContentLength }) {
                    error(
                        "'headers' must not contain ${HttpHeaders.ContentType} " +
                            "and ${HttpHeaders.ContentLength}. Found: $headers"
                    )
                }
                if (parameters.values.any { it !is ClassName && it !is ParameterizedTypeName }) {
                    error(
                        "'parameters' types must be either 'ClassName's or 'ParameterizedTypeName's. " +
                            "Found: ${parameters.map { "${it::class.simpleName}($it)" }}"
                    )
                }
                if (returnType !is ClassName && returnType !is ParameterizedTypeName) {
                    error(
                        "'returnType' must be either a 'ClassName' or a 'ParameterizedTypeName'. " +
                            "Found: ${returnType::class.simpleName}($returnType)"
                    )
                }
            }

            data class RequestBody(val parameterName: String, val contentType: String)
        }
    }
}

sealed class RelativeUrl {
    data class Parameter(val name: String) : RelativeUrl()
    data class Template(
        val path: List<StringValue>,
        val queryParameters: Map<String, StringValue>
    ) : RelativeUrl()
}

sealed class StringValue
data class Static(val content: String) : StringValue()
data class Dynamic(val parameterName: String) : StringValue()

enum class HttpMethod(val allowsBody: Boolean) {
    DELETE(allowsBody = false),
    GET(allowsBody = false),
    HEAD(allowsBody = false),
    OPTIONS(allowsBody = false),
    PATCH(allowsBody = true),
    POST(allowsBody = true),
    PUT(allowsBody = true)
}
