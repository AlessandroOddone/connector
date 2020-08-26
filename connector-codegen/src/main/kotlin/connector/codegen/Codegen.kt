package connector.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ITERABLE
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import connector.codegen.util.nonNull
import connector.http.HttpBodySerializer
import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.ServerResponseException
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.Url

fun Service.toFileSpec(): FileSpec {
    requireNotNull(existingParentInterface)
    val implementationClassName = "ConnectorGenerated$name"
    return FileSpec
        .builder(
            packageName = existingParentInterface.packageName,
            fileName = name
        )
        .addFunction(
            factoryFunctionSpec(
                implementationClassName = implementationClassName,
                parentInterface = existingParentInterface
            )
        )
        .addType(
            implementationClassSpec(
                name = implementationClassName,
                parentInterface = existingParentInterface
            )
        )
        .build()
}

private fun Service.factoryFunctionSpec(
    implementationClassName: String,
    parentInterface: ClassName
): FunSpec {
    val baseUrlParameter = ParameterSpec
        .builder(ParameterNames.BASE_URL, Url::class)
        .build()

    val httpClientParameter = ParameterSpec
        .builder(ParameterNames.HTTP_CLIENT, HttpClient::class)
        .build()

    val httpBodySerializersParameter = ParameterSpec
        .builder(
            ParameterNames.HTTP_BODY_SERIALIZERS,
            ITERABLE.plusParameter(HttpBodySerializer::class.asClassName())
        )
        .build()

    return FunSpec.builder(name)
        .addParameter(baseUrlParameter)
        .addParameter(httpClientParameter)
        .addParameter(httpBodySerializersParameter)
        .returns(parentInterface)
        .addStatement(
            "return $implementationClassName(" +
                "${ParameterNames.BASE_URL}, ${ParameterNames.HTTP_CLIENT}, ${ParameterNames.HTTP_BODY_SERIALIZERS})"
        )
        .build()
}

private fun Service.implementationClassSpec(
    name: String,
    parentInterface: ClassName
): TypeSpec {
    return TypeSpec.classBuilder(name)
        .addModifiers(KModifier.PRIVATE)
        .addSuperinterface(parentInterface)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterNames.BASE_URL, Url::class)
                .addParameter(ParameterNames.HTTP_CLIENT, HttpClient::class)
                .addParameter(
                    ParameterNames.HTTP_BODY_SERIALIZERS,
                    ITERABLE.plusParameter(HttpBodySerializer::class.asClassName())
                )
                .build()
        )
        .addProperty(
            PropertySpec
                .builder(ParameterNames.BASE_URL, Url::class, KModifier.PRIVATE)
                .initializer(ParameterNames.BASE_URL)
                .build()
        )
        .addProperty(
            PropertySpec
                .builder(ParameterNames.HTTP_CLIENT, HttpClient::class, KModifier.PRIVATE)
                .initializer(ParameterNames.HTTP_CLIENT)
                .build()
        )
        .addProperty(
            PropertySpec
                .builder(
                    ParameterNames.HTTP_BODY_SERIALIZERS,
                    ITERABLE.plusParameter(HttpBodySerializer::class.asClassName()),
                    KModifier.PRIVATE
                )
                .initializer(ParameterNames.HTTP_BODY_SERIALIZERS)
                .build()
        )
        .apply {
            functions.forEach { serviceFunction ->
                serviceFunction as Service.Function.Http
                addFunction(serviceFunction.toFunSpec(parentClassName = name))
            }
        }
        .build()
}

private fun Service.Function.Http.toFunSpec(parentClassName: String): FunSpec {
    return FunSpec.builder(name)
        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
        .addParameters(parameters())
        .addCode(body(parentClassName = parentClassName))
        .apply {
            if (returnType != UNIT) {
                returns(returnType)
            }
        }
        .build()
}

private fun Service.Function.Http.parameters(): List<ParameterSpec> =
    parameters.map { (parameterName, parameterType) ->
        ParameterSpec(name = parameterName, type = parameterType)
    }

private fun Service.Function.Http.body(parentClassName: String): CodeBlock {
    fun classProperty(name: String, isNestedThis: Boolean = false): String = when {
        !parameters.containsKey(name) -> name
        isNestedThis -> "this@$parentClassName.$name"
        else -> "this.$name"
    }

    fun variableName(desiredName: String): String {
        var variableName = desiredName
        while (parameters.containsKey(variableName)) variableName += "_"
        return variableName
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

    fun CodeBlock.Builder.setUrl() {
        // TODO don't prefix baseUrl if full URL is provided
        val urlStringTemplate = "\${${classProperty(ParameterNames.BASE_URL, isNestedThis = true)}}" +
            when (relativeUrl) {
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
            "%M(%L)",
            MemberName("io.ktor.client.request", "url"),
            "\"$urlStringTemplate\""
        )
    }

    fun CodeBlock.Builder.setHeaders() {
        if (headers.isNotEmpty()) {
            add("%M·{\n", MemberName(packageName = "io.ktor.client.request", "headers"))
            indent()
            for ((name, stringValue) in headers) {
                if (stringValue is Static) {
                    addStatement("append(%S, %S)", name, stringValue.content)
                    continue
                }
                stringValue as Dynamic
                val typeName = parameters.getValue(stringValue.parameterName)
                if (typeName.isNullable) {
                    beginControlFlow("if (${stringValue.parameterName} != null)")
                }
                if (typeName == STRING) {
                    addStatement("append(%S, ${stringValue.parameterName})", name)
                } else {
                    addStatement("append(%S, ${stringValue.parameterName}.toString())", name)
                }
                if (typeName.isNullable) {
                    endControlFlow()
                }
            }
            unindent()
            add("}\n")
        }
    }

    fun CodeBlock.Builder.setBody() {
        if (requestBody != null) {
            val typeName = parameters.getValue(requestBody.parameterName)
            if (typeName.isNullable) {
                beginControlFlow("if (${requestBody.parameterName} != null)")
            }

            val contentTypeVariableName = variableName("requestContentType")
            val serializerVariableName = variableName("writer")

            val parsedContentType = ContentType.parse(requestBody.contentType)
            if (parsedContentType.parameters.isEmpty()) {
                addStatement(
                    "val $contentTypeVariableName = %T(%S, %S)",
                    ContentType::class.asClassName(),
                    parsedContentType.contentType,
                    parsedContentType.contentSubtype
                )
            } else {
                add("val $contentTypeVariableName = %T(\n", ContentType::class.asClassName())
                indent()
                add("%S,\n", parsedContentType.contentType)
                add("%S,\n", parsedContentType.contentSubtype)
                add(
                    "%L\n",
                    buildCodeBlock {
                        add(
                            "%M(${parsedContentType.parameters.joinToString { "%L" }})",
                            MemberName("kotlin.collections", "listOf"),
                            *parsedContentType.parameters
                                .map { (name, value) ->
                                    buildCodeBlock {
                                        add(
                                            "%T(\"$name\", \"$value\")",
                                            HeaderValueParam::class.asClassName()
                                        )
                                    }
                                }
                                .toTypedArray()
                        )
                    }
                )
                unindent()
                add(")\n")
            }

            add(
                "val $serializerVariableName = " +
                    "${classProperty(ParameterNames.HTTP_BODY_SERIALIZERS, isNestedThis = true)}.%M·{ serializer ->\n",
                MemberName("kotlin.collections", "find")
            )
            indent()
            addStatement("serializer.canWrite($contentTypeVariableName)")
            unindent()
            add("}\n")

            beginControlFlow("if ($serializerVariableName == null)")
            addStatement(
                "%M(%L)",
                MemberName("kotlin", "error"),
                "\"No·suitable·HttpBodySerializer·found·for·writing·Content-Type:·'$$contentTypeVariableName'\""
            )
            endControlFlow()

            val thisOrEmpty = if (parameters.containsKey("body")) "this." else ""
            add("${thisOrEmpty}body = $serializerVariableName.write(\n")
            indent()
            add(obtainSerializerFor(typeName))
            add(",\n${requestBody.parameterName},\n$contentTypeVariableName\n")
            unindent()
            add(")\n")

            if (typeName.isNullable) {
                endControlFlow()
            }
        }
    }

    fun CodeBlock.Builder.returnResponseBody(httpResponseVariableName: String) {
        beginControlFlow("when ($httpResponseVariableName.status.value)")
        addStatement(
            "in 300..399 -> throw %T($httpResponseVariableName)",
            RedirectResponseException::class.asClassName()
        )
        addStatement(
            "in 400..499 -> throw %T($httpResponseVariableName)",
            ClientRequestException::class.asClassName()
        )
        addStatement(
            "in 500..599 -> throw %T($httpResponseVariableName)",
            ServerResponseException::class.asClassName()
        )
        addStatement(
            "!in 200..299 -> throw %T($httpResponseVariableName)",
            ResponseException::class.asClassName()
        )
        endControlFlow()

        if (returnType.isNullable) {
            beginControlFlow("if ($httpResponseVariableName.content.availableForRead == 0)")
            addStatement("return null")
            endControlFlow()
        }

        val contentTypeVariableName = variableName("responseContentType")
        val serializerVariableName = variableName("reader")
        addStatement(
            "val $contentTypeVariableName = $httpResponseVariableName.%M()",
            MemberName("io.ktor.http", "contentType")
        )
        add(
            "val $serializerVariableName = " +
                "${classProperty(ParameterNames.HTTP_BODY_SERIALIZERS, isNestedThis = true)}.%M·{ serializer ->\n",
            MemberName("kotlin.collections", "find")
        )
        indent()
        add("serializer.canRead($contentTypeVariableName)\n")
        unindent()
        add("}\n")

        beginControlFlow("if ($serializerVariableName == null)")
        addStatement(
            "%M(%L)",
            MemberName("kotlin", "error"),
            "\"No·suitable·HttpBodySerializer·found·for·reading·Content-Type:·'$$contentTypeVariableName'\""
        )
        endControlFlow()

        add("return $serializerVariableName.read(\n")
        indent()
        add(obtainSerializerFor(returnType))
        add(
            ",\n$httpResponseVariableName.content.%M(),\n$contentTypeVariableName\n",
            MemberName("io.ktor.utils.io", "readRemaining")
        )
        unindent()
        add(")")
    }

    return buildCodeBlock {
        val httpResponseVariableName = variableName("httpResponse")
        val returnsUnit = returnType.nonNull() == UNIT
        if (!returnsUnit) {
            add("val $httpResponseVariableName = ")
        }
        add(
            "${classProperty(ParameterNames.HTTP_CLIENT)}.%M<%T>·{\n",
            MemberName("io.ktor.client.request", "request"),
            ClassName("io.ktor.client.statement", "HttpResponse")
        )
        indent()

        setMethod()
        setUrl()
        setHeaders()
        setBody()

        unindent()
        add("}\n")

        if (!returnsUnit) {
            returnResponseBody(httpResponseVariableName = httpResponseVariableName)
        } else if (returnType.isNullable) {
            addStatement("return %T", UNIT)
        }
    }
}

private fun obtainSerializerFor(typeName: TypeName): CodeBlock = buildCodeBlock {
    when (typeName) {
        is ClassName -> {
            when (typeName.canonicalName) {
                "kotlin.Boolean", "kotlin.Byte", "kotlin.Char",
                "kotlin.Double", "kotlin.Float", "kotlin.Int",
                "kotlin.Long", "kotlin.Short", "kotlin.String" -> {
                    add(
                        "%T.%M()",
                        typeName.nonNull(),
                        MemberName("kotlinx.serialization.builtins", "serializer")
                    )
                }

                "kotlin.BooleanArray", "kotlin.ByteArray", "kotlin.CharArray",
                "kotlin.DoubleArray", "kotlin.FloatArray", "kotlin.IntArray",
                "kotlin.LongArray", "kotlin.ShortArray", "kotlin.StringArray" -> {
                    val serializerName = "${typeName.simpleName}Serializer"
                    add("%M()", MemberName("kotlinx.serialization.builtins", serializerName))
                }

                else -> {
                    add("%T.serializer()", typeName.nonNull())
                }
            }
        }

        is ParameterizedTypeName -> {
            val literalPlaceholders = typeName.typeArguments.joinToString { "%L" }
            val literalArgs = typeName.typeArguments.map { obtainSerializerFor(it) }.toTypedArray()

            when (typeName.rawType.canonicalName) {
                "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set", "kotlin.collections.Map",
                "kotlin.collections.Map.Entry", "kotlin.Pair", "kotlin.Triple" -> {
                    val serializerName = "${typeName.rawType.simpleNames.joinToString("")}Serializer"
                    add(
                        "%M($literalPlaceholders)",
                        MemberName("kotlinx.serialization.builtins", serializerName),
                        *literalArgs
                    )
                }

                else -> {
                    add(
                        "%T.serializer($literalPlaceholders)",
                        typeName.rawType.nonNull(),
                        *literalArgs
                    )
                }
            }
        }

        else -> error("Expected 'typeName' to be either a ClassName or a ParameterizedTypeName")
    }

    if (typeName.isNullable) {
        add(".%M", MemberName("kotlinx.serialization.builtins", "nullable"))
    }
}

private object ParameterNames {
    const val BASE_URL = "baseUrl"
    const val HTTP_CLIENT = "httpClient"
    const val HTTP_BODY_SERIALIZERS = "httpBodySerializers"
}
