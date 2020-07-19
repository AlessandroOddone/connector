package segment.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.buildCodeBlock
import io.ktor.client.HttpClient
import io.ktor.http.Url

fun Service.toFileSpec(): FileSpec {
    requireNotNull(existingParentInterface)
    return FileSpec
        .builder(
            packageName = existingParentInterface.packageName,
            fileName = name
        )
        .addFunction(factoryFunctionSpec(parentInterface = existingParentInterface))
        .build()
}

private fun Service.factoryFunctionSpec(parentInterface: ClassName): FunSpec {
    val baseUrlParameter = ParameterSpec
        .builder("baseUrl", Url::class)
        .build()

    val clientParameter = ParameterSpec
        .builder("client", HttpClient::class)
        .build()

    return FunSpec.builder(name)
        .addParameter(baseUrlParameter)
        .addParameter(clientParameter)
        .addStatement("return %L", anonymousImplementationSpec(parentInterface))
        .build()
}

private fun Service.anonymousImplementationSpec(parentInterface: ClassName): TypeSpec {
    return TypeSpec.anonymousClassBuilder()
        .addSuperinterface(parentInterface)
        .apply {
            functions.forEach { serviceFunction ->
                serviceFunction as Service.Function.Http
                addFunction(serviceFunction.toFunSpec())
            }
        }
        .build()
}

private fun Service.Function.Http.toFunSpec(): FunSpec {
    return FunSpec.builder(name)
        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
        .addParameters(parameters())
        .addCode(body())
        .apply {
            if (responseBodyType.name != UNIT) {
                returns(responseBodyType.name)
            }
        }
        .build()
}

private fun Service.Function.Http.parameters(): List<ParameterSpec> =
    parameters.map { (parameterName, parameterType) ->
        ParameterSpec(name = parameterName, type = parameterType.name)
    }

private fun Service.Function.Http.body(): CodeBlock {
    fun CodeBlock.Builder.setUrl() {
        // TODO don't prefix baseUrl if full URL is provided
        val urlStringTemplate = "\${baseUrl}" + when (relativeUrl) {
            is RelativeUrl.Parameter -> relativeUrl.name
            is RelativeUrl.Template -> {
                buildString {
                    append(
                        relativeUrl.path.joinToString(separator = "/") { value ->
                            when (value) {
                                is Static -> value.content
                                is Dynamic -> "$${value.parameterName}"
                            }
                        }
                    )
                    if (relativeUrl.queryParameters.isNotEmpty()) {
                        append('?')
                    }
                    append(
                        relativeUrl.queryParameters.entries.joinToString(separator = "&") { (name, value) ->
                            "$name=" + when (value) {
                                is Static -> value.content
                                is Dynamic -> "$${value.parameterName}"
                            }
                        }
                    )
                }
            }
        }
        addStatement(
            "%M(\"$urlStringTemplate\")",
            MemberName("io.ktor.client.request", "url")
        )
    }

    fun CodeBlock.Builder.setMethod() {
        val thisOrEmpty = if (parameters.containsKey("method")) "this." else ""
        addStatement(
            "${thisOrEmpty}method = " + when (method) {
                HttpMethod.DELETE -> "%T.Delete"
                HttpMethod.GET -> "%T.Get"
                HttpMethod.HEAD -> "%T.Head"
                HttpMethod.OPTIONS -> "%T.Options"
                HttpMethod.PATCH -> "%T.Patch"
                HttpMethod.POST -> "%T.Post"
                HttpMethod.PUT -> "%T.Put"
            },
            io.ktor.http.HttpMethod::class
        )
    }

    fun CodeBlock.Builder.setHeaders() {
        if (headers.isNotEmpty()) {
            add("%M {\n", MemberName(packageName = "io.ktor.client.request", "headers"))
            indent()
            headers.forEach { (name, stringValue) ->
                val value = when (stringValue) {
                    is Static -> "\"${stringValue.content}\""
                    is Dynamic -> stringValue.parameterName
                }
                add("append(\"$name\", $value)\n")
            }
            unindent()
            add("}\n")
        }
    }

    fun CodeBlock.Builder.setBody() {
        if (requestBodyParameterName != null) {
            val thisOrEmpty = if (parameters.containsKey("body")) "this." else ""
            addStatement("${thisOrEmpty}body = $requestBodyParameterName")
        }
    }

    responseBodyType as Type.Existing
    return buildCodeBlock {
        add(
            "return client.%M<%T> {\n",
            MemberName("io.ktor.client.request", "request"),
            responseBodyType.name
        )
        indent()

        setUrl()
        setMethod()
        setHeaders()
        setBody()

        unindent()
        add("}\n")
    }
}
