package connector.codegen

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
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
import connector.codegen.util.escape
import connector.codegen.util.noBreakingSpaces
import connector.codegen.util.nonNull
import connector.http.HttpBodySerializer
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

fun ServiceDescription.toFileSpec(): FileSpec {
  val implementationClassName = "ConnectorGenerated$name"
  return FileSpec
    .builder(
      packageName = parentInterface.packageName,
      fileName = name
    )
    .addFunction(
      factoryFunctionSpec(
        implementationClassName = implementationClassName,
        parentInterface = parentInterface
      )
    )
    .addType(
      implementationClassSpec(
        name = implementationClassName,
        parentInterface = parentInterface
      )
    )
    .addFunction(ensureValidBaseUrlFunctionSpec())
    .addFunction(dynamicUrlFunctionSpec())
    .addFunction(isFullUrlFunctionSpec())
    .addFunction(ensureNoPathTraversalFunctionSpec())
    .addFunction(isPathTraversalSegmentFunctionSpec())
    .build()
}

private fun ServiceDescription.factoryFunctionSpec(
  implementationClassName: String,
  parentInterface: ClassName
): FunSpec {
  val baseUrlParameter = ParameterSpec
    .builder(ParameterNames.BASE_URL, ClassNames.URL)
    .build()

  val httpClientParameter = ParameterSpec
    .builder(ParameterNames.HTTP_CLIENT, ClassNames.HTTP_CLIENT)
    .build()

  val httpBodySerializersParameter = ParameterSpec
    .builder(
      ParameterNames.HTTP_BODY_SERIALIZERS,
      ITERABLE.plusParameter(ClassNames.HTTP_BODY_SERIALIZER)
    )
    .build()

  return FunSpec.builder(name)
    .addParameter(baseUrlParameter)
    .addParameter(httpClientParameter)
    .addParameter(httpBodySerializersParameter)
    .returns(parentInterface)
    .addCode(
      buildCodeBlock {
        addStatement("%L.%L()", ParameterNames.BASE_URL, FunctionNames.ENSURE_VALID_BASE_URL)
        addStatement(
          "return $implementationClassName(%L, %L, %L)",
          ParameterNames.BASE_URL,
          ParameterNames.HTTP_CLIENT,
          ParameterNames.HTTP_BODY_SERIALIZERS
        )
      }
    )
    .build()
}

private fun ServiceDescription.implementationClassSpec(
  name: String,
  parentInterface: ClassName
): TypeSpec {
  return TypeSpec.classBuilder(name)
    .addModifiers(KModifier.PRIVATE)
    .addSuperinterface(parentInterface)
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameter(ParameterNames.BASE_URL, ClassNames.URL)
        .addParameter(ParameterNames.HTTP_CLIENT, ClassNames.HTTP_CLIENT)
        .addParameter(
          ParameterNames.HTTP_BODY_SERIALIZERS,
          ITERABLE.plusParameter(ClassNames.HTTP_BODY_SERIALIZER)
        )
        .build()
    )
    .addProperty(
      PropertySpec
        .builder(ParameterNames.BASE_URL, ClassNames.URL, KModifier.PRIVATE)
        .initializer(ParameterNames.BASE_URL)
        .build()
    )
    .addProperty(
      PropertySpec
        .builder(ParameterNames.HTTP_CLIENT, ClassNames.HTTP_CLIENT, KModifier.PRIVATE)
        .initializer(ParameterNames.HTTP_CLIENT)
        .build()
    )
    .addProperty(
      PropertySpec
        .builder(
          ParameterNames.HTTP_BODY_SERIALIZERS,
          ITERABLE.plusParameter(ClassNames.HTTP_BODY_SERIALIZER),
          KModifier.PRIVATE
        )
        .initializer(ParameterNames.HTTP_BODY_SERIALIZERS)
        .build()
    )
    .apply {
      functions.forEach { serviceFunction ->
        serviceFunction as ServiceDescription.Function.Http
        addFunction(serviceFunction.toFunSpec(parentClassName = name))
      }
    }
    .build()
}

private fun ServiceDescription.Function.Http.toFunSpec(parentClassName: String): FunSpec {
  return FunSpec.builder(name)
    .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
    .addParameters(parameters())
    .addCode(body(parentClassName = parentClassName))
    .returns(returnType)
    .build()
}

private fun ServiceDescription.Function.Http.parameters(): List<ParameterSpec> =
  parameters.map { (parameterName, parameterType) ->
    ParameterSpec(name = parameterName, type = parameterType)
  }

private fun ServiceDescription.Function.Http.body(parentClassName: String): CodeBlock {
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

  fun validateDynamicPathParameters() = buildCodeBlock {
    if (url !is ServiceDescription.Url.Template) return@buildCodeBlock
    val segments = url.value.split('/')
    val toValidate = segments
      .mapNotNull { segment ->
        var surroundWithQuotes = false
        var didReplace = false
        var result = segment
        url.parameterNameReplacementMappings.forEach { (toReplace, parameterName) ->
          when {
            segment == toReplace -> {
              if (!didReplace) {
                result = result.escape().noBreakingSpaces()
              }
              didReplace = true
              result = result.replace(toReplace, parameterName)
            }
            segment.contains(toReplace) -> {
              if (!didReplace) {
                result = result.escape().noBreakingSpaces()
              }
              didReplace = true
              surroundWithQuotes = true
              result = result.replace(toReplace, "\${$parameterName}")
            }
          }
        }
        if (!didReplace) return@mapNotNull null
        if (surroundWithQuotes) "\"$result\"" else result
      }
      .toSet()

    toValidate.forEach { addStatement("%L.${FunctionNames.ENSURE_NO_PATH_TRAVERSAL}()", it) }
  }

  fun setMethod() = buildCodeBlock {
    val thisOrEmpty = if (parameters.containsKey("method")) "this." else ""
    addStatement(
      "${thisOrEmpty}method = " + when {
        method.equals("DELETE", ignoreCase = true) -> "%T.Delete"
        method.equals("GET", ignoreCase = true) -> "%T.Get"
        method.equals("HEAD", ignoreCase = true) -> "%T.Head"
        method.equals("OPTIONS", ignoreCase = true) -> "%T.Options"
        method.equals("PATCH", ignoreCase = true) -> "%T.Patch"
        method.equals("POST", ignoreCase = true) -> "%T.Post"
        method.equals("PUT", ignoreCase = true) -> "%T.Put"
        else -> "%T(\"$method\")"
      },
      io.ktor.http.HttpMethod::class
    )
  }

  fun setUrl() = buildCodeBlock {
    beginControlFlow("url")

    when (url) {
      is ServiceDescription.Url.Template -> {
        val urlStringTemplate = url.value.let { urlTemplate ->
          var result = urlTemplate.escape().noBreakingSpaces()
          url.parameterNameReplacementMappings.forEach { (toReplace, parameterName) ->
            result = result.replace(toReplace, "\${$parameterName}")
          }
          result
        }
        val path = urlStringTemplate.takeWhile { it != '?' && it != '#' }

        when (url.type) {
          UrlType.ABSOLUTE -> {
            addStatement(
              "%M(%L)",
              MemberName("io.ktor.http", "takeFrom"),
              classProperty(ParameterNames.BASE_URL, isNestedThis = true),
            )
            addStatement(
              "encodedPath·= %L.%M()",
              "\"$path\"",
              MemberName("io.ktor.http", "encodeURLPath")
            )
          }

          UrlType.FULL -> {
            addStatement(
              "%M(%L)",
              MemberName("io.ktor.http", "takeFrom"),
              "\"$path\"",
            )
          }

          UrlType.PROTOCOL_RELATIVE -> {
            addStatement(
              "%M(\"%L:%L\")",
              MemberName("io.ktor.http", "takeFrom"),
              "\${${classProperty(ParameterNames.BASE_URL, isNestedThis = true)}.protocol.name}",
              path,
            )
          }

          UrlType.RELATIVE -> {
            addStatement(
              "%M(%L)",
              MemberName("io.ktor.http", "takeFrom"),
              classProperty(ParameterNames.BASE_URL, isNestedThis = true),
            )
            addStatement(
              "encodedPath·+= %L.%M()",
              "\"$path\"",
              MemberName("io.ktor.http", "encodeURLPath")
            )
          }
        }

        if (urlStringTemplate.length > path.length) {
          val afterPath = urlStringTemplate.substring(path.length)
          // 'null' if there is no '?'
          val queryString: String? = if (afterPath[0] == '?') {
            afterPath.drop(1).takeWhile { it != '#' }
          } else {
            null
          }
          // 'null' if there is no '#'
          val fragment: String? = if (afterPath[0] == '#') {
            afterPath.drop(1)
          } else {
            val indexOfHash = afterPath.indexOf('#')
            when {
              indexOfHash < 0 -> null
              indexOfHash == afterPath.lastIndex -> ""
              else -> afterPath.substring(indexOfHash + 1)
            }
          }

          if (queryString?.isEmpty() == true) {
            addStatement("trailingQuery = true")
          }

          if (queryString?.isNotEmpty() == true) {
            addStatement(
              "parameters.appendAll(%M(%S))",
              MemberName("io.ktor.http", "parseQueryString"),
              queryString
            )
          }

          if (fragment?.isNotEmpty() == true) {
            addStatement("fragment·= %L", "\"$fragment\"")
          }
        }
      }

      is ServiceDescription.Url.Dynamic -> {
        when (val typeName = parameters.getValue(url.parameterName)) {
          ClassNames.URL -> {
            addStatement(
              "%M(%L)",
              MemberName("io.ktor.http", "takeFrom"),
              url.parameterName,
            )
          }

          else -> {
            addStatement(
              "%L(%L%L, %L)",
              FunctionNames.DYNAMIC_URL,
              url.parameterName,
              if (typeName != STRING) ".toString()" else "",
              classProperty(ParameterNames.BASE_URL, isNestedThis = true)
            )
          }
        }
      }
    }
    url.dynamicQueryParameters.forEach { (queryParameterName, functionParameterName) ->
      val typeName = parameters.getValue(functionParameterName)
      if (typeName.isNullable) {
        beginControlFlow("if (%L·!= null)", functionParameterName)
      }
      addStatement(
        "parameters.append(%S, %L)",
        queryParameterName,
        if (typeName.nonNull() == STRING) {
          functionParameterName
        } else {
          "$functionParameterName.toString()"
        }
      )
      if (typeName.isNullable) {
        endControlFlow()
      }
    }

    endControlFlow()
  }

  fun setHeaders() = buildCodeBlock {
    if (headers.isNotEmpty()) {
      add("%M·{\n", MemberName(packageName = "io.ktor.client.request", "headers"))
      indent()
      for (header in headers) {
        if (header is StringValue.Static) {
          addStatement("append(%S, %S)", header.name, header.value)
          continue
        }
        header as StringValue.Dynamic
        val typeName = parameters.getValue(header.parameterName)
        if (typeName.isNullable) {
          beginControlFlow("if (${header.parameterName}·!= null)")
        }
        if (typeName.nonNull() == STRING) {
          addStatement("append(%S, ${header.parameterName})", header.name)
        } else {
          addStatement("append(%S, ${header.parameterName}.toString())", header.name)
        }
        if (typeName.isNullable) {
          endControlFlow()
        }
      }
      unindent()
      add("}\n")
    }
  }

  fun setBody() = buildCodeBlock {
    if (requestBody != null) {
      val typeName = parameters.getValue(requestBody.parameterName)
      if (typeName.isNullable) {
        beginControlFlow("if (${requestBody.parameterName}·!= null)")
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

  fun returnResponseBody(httpResponseVariableName: String) = buildCodeBlock {
    if (returnType.isNullable) {
      beginControlFlow("if ($httpResponseVariableName.content.availableForRead·==·0)")
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

    beginControlFlow("if ($serializerVariableName·==·null)")
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
    add(validateDynamicPathParameters())

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

    add(setMethod())
    add(setUrl())
    add(setHeaders())
    add(setBody())

    unindent()
    add("}\n")

    if (!returnsUnit) {
      add(returnResponseBody(httpResponseVariableName = httpResponseVariableName))
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

private fun ensureValidBaseUrlFunctionSpec(): FunSpec {
  return FunSpec.builder(FunctionNames.ENSURE_VALID_BASE_URL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(ClassNames.URL)
    .addCode(
      buildCodeBlock {
        beginControlFlow(
          "if (protocol·!= %T.HTTP && protocol·!= %T.HTTPS)",
          URLProtocol::class,
          URLProtocol::class
        )
        addStatement(
          "throw %T(%L)",
          IllegalArgumentException::class,
          "\"Base·URL·protocol·must·be·HTTP·or·HTTPS.·Found:·\$this\""
        )
        endControlFlow()

        beginControlFlow("if (!parameters.isEmpty())")
        addStatement(
          "throw %T(%L)",
          IllegalArgumentException::class,
          "\"Base·URL·should·not·have·query·parameters.·Found:·\$this\""
        )
        endControlFlow()

        beginControlFlow(
          "if (fragment.%M())",
          MemberName("kotlin.text", "isNotEmpty")
        )
        addStatement(
          "throw %T(%L)",
          IllegalArgumentException::class,
          "\"Base·URL·fragment·should·be·empty.·Found:·\$this\""
        )
        endControlFlow()

        beginControlFlow(
          "if (!%M.%M(%L))",
          MemberName("io.ktor.http", "fullPath"),
          MemberName("kotlin.text", "endsWith"),
          "'/'"
        )
        addStatement(
          "throw %T(%L)",
          IllegalArgumentException::class,
          "\"Base·URL·should·end·in·'/'.·Found:·\$this\""
        )
        endControlFlow()
      }
    )
    .build()
}

private fun dynamicUrlFunctionSpec(): FunSpec {
  return FunSpec.builder(FunctionNames.DYNAMIC_URL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(ClassNames.URL_BUILDER)
    .addParameter(ParameterNames.DYNAMIC_URL, STRING)
    .addParameter(ParameterNames.BASE_URL, ClassNames.URL)
    .returns(ClassNames.URL_BUILDER)
    .addCode(
      buildCodeBlock {
        addStatement(
          "val path = %L.%M·{ it·!=·'?'·&& it·!=·'#' }",
          ParameterNames.DYNAMIC_URL,
          MemberName("kotlin.text", "takeWhile"),
        )

        beginControlFlow("when")

        // Protocol-relative
        beginControlFlow(
          "path.%M(%S) ->",
          MemberName("kotlin.text", "startsWith"),
          "//",
        )
        addStatement(
          "%M(\"%L:%L\")",
          MemberName("io.ktor.http", "takeFrom"),
          "\${${ParameterNames.BASE_URL}.protocol.name}",
          "\$path",
        )
        endControlFlow()

        // Absolute
        beginControlFlow(
          "path.%M(%S) ->",
          MemberName("kotlin.text", "startsWith"),
          "/",
        )
        addStatement(
          "%M(%L)",
          MemberName("io.ktor.http", "takeFrom"),
          ParameterNames.BASE_URL,
        )
        addStatement(
          "encodedPath·= path.%M()",
          MemberName("io.ktor.http", "encodeURLPath")
        )
        endControlFlow()

        // Full
        beginControlFlow("path.${FunctionNames.IS_FULL_URL}() ->")
        addStatement(
          "%M(path)",
          MemberName("io.ktor.http", "takeFrom"),
        )
        endControlFlow()

        // Relative
        beginControlFlow("else ->")
        addStatement(
          "%M(%L)",
          MemberName("io.ktor.http", "takeFrom"),
          ParameterNames.BASE_URL,
        )
        addStatement(
          "encodedPath·+= path.%M()",
          MemberName("io.ktor.http", "encodeURLPath")
        )
        endControlFlow()

        endControlFlow()

        beginControlFlow("if (${ParameterNames.DYNAMIC_URL}.length > path.length)")
        addStatement(
          "val afterPath = ${ParameterNames.DYNAMIC_URL}.%M(path.length)",
          MemberName("kotlin.text", "substring"),
        )
        add(
          """
                    // 'null' if there is no '?'
                    val queryString: String? = if (afterPath[0] == '?') {
                      afterPath.%M(1).%M { it != '#' }
                    } else {
                      null
                    }
                    if (queryString?.%M() == true) {
                      trailingQuery = true
                    }
                    if (queryString?.%M() == true) {
                      parameters.appendAll(%M(queryString))
                    }
          """.trimIndent(),
          MemberName("kotlin.text", "drop"),
          MemberName("kotlin.text", "takeWhile"),
          MemberName("kotlin.text", "isEmpty"),
          MemberName("kotlin.text", "isNotEmpty"),
          MemberName("io.ktor.http", "parseQueryString"),
        )
        add("\n")
        add(
          """
                    fragment = if (afterPath[0] == '#') {
                      afterPath.%M(1)
                    } else {
                      afterPath.%M("#", missingDelimiterValue = "")
                    }
          """.trimIndent(),
          MemberName("kotlin.text", "drop"),
          MemberName("kotlin.text", "substringAfter"),
        )
        add("\n")
        endControlFlow()

        addStatement("return this")
      }
    )
    .build()
}

private fun isFullUrlFunctionSpec(): FunSpec {
  return FunSpec.builder(FunctionNames.IS_FULL_URL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(STRING)
    .returns(BOOLEAN)
    .addCode(
      """
            for (index in %M) {
              val char = get(index)
              return if (index == 0 && char !in 'a'..'z' && char !in 'A'..'Z') {
                  false
              } else when (char) {
                  ':' -> true
                  in 'a'..'z', in 'A'..'Z', in '0'..'9', '+', '-', '.' -> continue
                  else -> false
              }
            }
            return false
      """.trimIndent(),
      MemberName("kotlin.text", "indices")
    )
    .build()
}

private fun ensureNoPathTraversalFunctionSpec(): FunSpec {
  return FunSpec.builder(FunctionNames.ENSURE_NO_PATH_TRAVERSAL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(ANY.copy(nullable = true))
    .addCode(
      buildCodeBlock {
        addStatement("if (this·==·null) return")
        beginControlFlow(
          "if (toString().%M('/').%M·{ it.isPathTraversalSegment() })",
          MemberName("kotlin.text", "split"),
          MemberName("kotlin.collections", "any")
        )
        addStatement(
          "throw·%T(%L)",
          IllegalArgumentException::class,
          "\"@Path·arguments·cannot·introduce·path·traversal.·Found:·'\$this'\""
        )
        endControlFlow()
      }
    )
    .build()
}

private fun isPathTraversalSegmentFunctionSpec(): FunSpec {
  return FunSpec.builder(FunctionNames.IS_PATH_TRAVERSAL_SEGMENT)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(STRING)
    .returns(BOOLEAN)
    .addCode(
      buildCodeBlock {
        val startsWithMemberName = MemberName("kotlin.text", "startsWith")
        val endsWithMemberName = MemberName("kotlin.text", "endsWith")

        beginControlFlow("return when")

        beginControlFlow("%M('.') ->", startsWithMemberName)
        addStatement(
          "length·==·1·|| length·==·2·&&·%M('.')·|| length·==·4·&&·%M(%S, ignoreCase·=·true)",
          endsWithMemberName,
          endsWithMemberName,
          "%2E",
        )
        endControlFlow()

        beginControlFlow(
          "%M(%S, ignoreCase·=·true) ->",
          startsWithMemberName,
          "%2E",
        )
        addStatement(
          "length·==·3·|| length·==·4·&&·%M('.')·|| length·==·6·&&·%M(%S, ignoreCase·=·true)",
          endsWithMemberName,
          endsWithMemberName,
          "%2E",
        )
        endControlFlow()

        addStatement("else -> false")

        endControlFlow()
      }
    )
    .build()
}

private val suppressNothingToInlineAnnotationSpec = AnnotationSpec
  .builder(Suppress::class)
  .addMember("%S", "NOTHING_TO_INLINE")
  .build()

private object ClassNames {
  val HTTP_BODY_SERIALIZER = HttpBodySerializer::class.asClassName()
  val HTTP_CLIENT = HttpClient::class.asClassName()
  val URL = Url::class.asClassName()
  val URL_BUILDER = URLBuilder::class.asClassName()
}

private object ParameterNames {
  const val BASE_URL = "baseUrl"
  const val DYNAMIC_URL = "dynamicUrl"
  const val HTTP_CLIENT = "httpClient"
  const val HTTP_BODY_SERIALIZERS = "httpBodySerializers"
}

private object FunctionNames {
  const val DYNAMIC_URL = "dynamicUrl"
  const val ENSURE_NO_PATH_TRAVERSAL = "ensureNoPathTraversal"
  const val ENSURE_VALID_BASE_URL = "ensureValidBaseUrl"
  const val IS_FULL_URL = "isFullUrl"
  const val IS_PATH_TRAVERSAL_SEGMENT = "isPathTraversalSegment"
}
