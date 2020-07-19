package segment.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class Service(
    val name: String,
    val functions: List<Function>,
    val existingParentInterface: ClassName?
) {
    sealed class Function {
        abstract val name: String
        abstract val parameters: Map<String, Type>

        data class Http(
            override val name: String,
            override val parameters: Map<String, Type>,
            val method: HttpMethod,
            val relativeUrl: RelativeUrl,
            val headers: List<Pair<String, StringValue>>,
            val requestBodyParameterName: String?,
            val responseBodyType: Type
        ) : Function()
    }
}

sealed class Type {
    abstract val name: TypeName

    data class Existing(override val name: TypeName) : Type()
}

sealed class RelativeUrl {
    data class Parameter(val name: String) : RelativeUrl()
    data class Template(
        val path: List<StringValue>,
        val queryParameters: Map<String, StringValue>
    ) : RelativeUrl()
}

sealed class StringValue {
    @Suppress("FunctionName")
    companion object {
        fun Static(value: String) = segment.codegen.Static(value)
        fun Dynamic(name: String) = segment.codegen.Dynamic(name)
    }
}

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
