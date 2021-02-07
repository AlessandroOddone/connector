package dev.aoddon.connector.codegen

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import dev.aoddon.connector.codegen.util.classNameOrNull
import dev.aoddon.connector.codegen.util.escape
import dev.aoddon.connector.codegen.util.noBreakingSpaces
import dev.aoddon.connector.codegen.util.nonNull
import dev.aoddon.connector.http.HttpBody
import dev.aoddon.connector.http.HttpContentSerializer
import dev.aoddon.connector.http.HttpInterceptor
import dev.aoddon.connector.http.HttpRequest
import dev.aoddon.connector.http.HttpResponse
import dev.aoddon.connector.http.HttpResult
import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.HttpStatement
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.HttpMethod
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.util.StringValues
import io.ktor.utils.io.ByteReadChannel

public fun ServiceDescription.toFileSpec(): FileSpec = ServiceCodeGenerator(this).run()

private class ServiceCodeGenerator(private val serviceDescription: ServiceDescription) {
  private val packageName = serviceDescription.parentInterface.packageName
  private val implementationClassName = "ConnectorGenerated${serviceDescription.name}"

  fun run(): FileSpec = with(serviceDescription) {
    FileSpec
      .builder(
        packageName = packageName,
        fileName = name
      )
      .addFunction(factoryFunctionSpec)
      .addType(implementationClassSpec)
      .addFunction(httpRequestHandlerFunctionSpec)
      .addFunction(executeRequestWithInterceptorsFunctionSpec)
      .addFunction(toHttpStatementFunctionSpec)
      .addFunction(throwableAsHttpResultFunctionSpec)
      .addFunction(firstWriterOfFunctionSpec)
      .addFunction(firstReaderOfFunctionSpec)
      .addFunction(ensureValidBaseUrlFunctionSpec)
      .addFunction(dynamicUrlFunctionSpec)
      .addFunction(isFullUrlFunctionSpec)
      .addFunction(ensureNoPathTraversalFunctionSpec)
      .addFunction(isPathTraversalSegmentFunctionSpec)
      .build()
  }

  private val factoryFunctionSpec: FunSpec = with(serviceDescription) {
    val baseUrlParameter = ParameterSpec
      .builder(ParameterNames.BASE_URL, ClassNames.Ktor.URL)
      .build()

    val httpClientParameter = ParameterSpec
      .builder(ParameterNames.HTTP_CLIENT, ClassNames.HTTP_CLIENT)
      .build()

    val httpBodySerializersParameter = ParameterSpec
      .builder(
        ParameterNames.HTTP_CONTENT_SERIALIZERS,
        LIST.plusParameter(ClassNames.HTTP_CONTENT_SERIALIZER)
      )
      .defaultValue("emptyList()")
      .build()

    val httpInterceptorsParameter = ParameterSpec
      .builder(
        ParameterNames.HTTP_INTERCEPTORS,
        LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR)
      )
      .defaultValue("emptyList()")
      .build()

    FunSpec.builder(name)
      .addParameter(baseUrlParameter)
      .addParameter(httpClientParameter)
      .addParameter(httpBodySerializersParameter)
      .addParameter(httpInterceptorsParameter)
      .returns(parentInterface)
      .addCode(
        buildCodeBlock {
          addStatement(
            "%L.%M()",
            ParameterNames.BASE_URL,
            MemberName(packageName, FunctionNames.ENSURE_VALID_BASE_URL)
          )
          add(
            """
            return·$implementationClassName(
              %L,
              %L,
              %L,
              %L
            )
            """.trimIndent(),
            ParameterNames.BASE_URL,
            ParameterNames.HTTP_CLIENT,
            ParameterNames.HTTP_CONTENT_SERIALIZERS,
            ParameterNames.HTTP_INTERCEPTORS
          )
        }
      )
      .build()
  }

  val implementationClassSpec: TypeSpec = with(serviceDescription) {
    TypeSpec.classBuilder(implementationClassName)
      .addModifiers(KModifier.PRIVATE)
      .addSuperinterface(parentInterface)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter(ParameterNames.BASE_URL, ClassNames.Ktor.URL)
          .addParameter(ParameterNames.HTTP_CLIENT, ClassNames.HTTP_CLIENT)
          .addParameter(
            ParameterNames.HTTP_CONTENT_SERIALIZERS,
            LIST.plusParameter(ClassNames.HTTP_CONTENT_SERIALIZER)
          )
          .addParameter(
            ParameterNames.HTTP_INTERCEPTORS,
            LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR)
          )
          .build()
      )
      .addProperty(
        PropertySpec
          .builder(ParameterNames.BASE_URL, ClassNames.Ktor.URL, KModifier.PRIVATE)
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
            ParameterNames.HTTP_CONTENT_SERIALIZERS,
            LIST.plusParameter(ClassNames.HTTP_CONTENT_SERIALIZER),
            KModifier.PRIVATE
          )
          .initializer(ParameterNames.HTTP_CONTENT_SERIALIZERS)
          .build()
      )
      .addProperty(
        PropertySpec
          .builder(
            ParameterNames.HTTP_INTERCEPTORS,
            LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR),
            KModifier.PRIVATE
          )
          .initializer(ParameterNames.HTTP_INTERCEPTORS)
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
          url.replaceBlockToParameterMap.forEach { (toReplace, parameterName) ->
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

      toValidate.forEach { s ->
        addStatement("%L.%M()", s, MemberName(packageName, FunctionNames.ENSURE_NO_PATH_TRAVERSAL))
      }
    }

    fun urlBuilderVariable(variableName: String) = buildCodeBlock {
      beginControlFlow(
        "val·$variableName·= %T().%M",
        URLBuilder::class,
        MemberName("kotlin", "apply")
      )

      when (url) {
        is ServiceDescription.Url.Template -> {
          val urlStringTemplate = url.value.let { urlTemplate ->
            var result = urlTemplate.escape().noBreakingSpaces()
            url.replaceBlockToParameterMap.forEach { (toReplace, parameterName) ->
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
            ClassNames.Ktor.URL -> {
              addStatement(
                "%M(%L)",
                MemberName("io.ktor.http", "takeFrom"),
                url.parameterName,
              )
            }

            else -> {
              addStatement(
                "%M(%L%L, %L)",
                MemberName(packageName, FunctionNames.DYNAMIC_URL),
                url.parameterName,
                if (typeName != STRING) ".toString()" else "",
                classProperty(ParameterNames.BASE_URL, isNestedThis = true)
              )
            }
          }
        }
      }
      url.dynamicQueryParameters.forEach { (queryParameterName, functionParameterName) ->
        val queryParameterTypeName = parameters.getValue(functionParameterName)
        if (queryParameterTypeName.isNullable) {
          beginControlFlow("if ($functionParameterName·!= null)")
        }

        val queryParameterTypeQualifiedName = queryParameterTypeName.classNameOrNull()?.canonicalName
        when {
          queryParameterTypeQualifiedName == "kotlin.String" -> {
            addStatement("parameters.append(%S, $functionParameterName)", queryParameterName)
          }

          ITERABLE_TYPE_QUALIFIED_NAMES.contains(queryParameterTypeQualifiedName) -> {
            queryParameterTypeName as ParameterizedTypeName
            val iterableTypeArgumentName = queryParameterTypeName.typeArguments[0]
            when {
              iterableTypeArgumentName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
                addStatement(
                  "parameters.appendAll(%S, $functionParameterName.%M { it%L.toString() })",
                  queryParameterName,
                  MemberName(
                    "kotlin.collections",
                    if (iterableTypeArgumentName.isNullable) "mapNotNull" else "map"
                  ),
                  if (iterableTypeArgumentName.isNullable) "?" else ""
                )
              }

              // Iterable<String?>
              iterableTypeArgumentName.isNullable -> {
                addStatement(
                  "parameters.appendAll(%S, $functionParameterName.%M())",
                  queryParameterName,
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement("parameters.appendAll(%S, $functionParameterName)", queryParameterName)
              }
            }
          }

          else -> {
            addStatement("parameters.append(%S, $functionParameterName.toString())", queryParameterName)
          }
        }

        if (queryParameterTypeName.isNullable) {
          endControlFlow()
        }
      }
      url.queryMapParameterNames.forEach { parameterName ->
        add(
          addMapToParameterBuilderCodeBlock(
            mapParameterName = parameterName,
            mapTypeName = parameters.getValue(parameterName),
            parametersBuilderNameReference = "parameters"
          )
        )
      }
      endControlFlow()
    }

    fun headersVariable(variableName: String) = buildCodeBlock {
      require(headers.isNotEmpty()) {
        "'$variableName' variable should not be created if there are no headers"
      }
      beginControlFlow(
        "val·$variableName·=·%M",
        MemberName("io.ktor.client.utils", "buildHeaders")
      )
      for (header in headers) {
        if (header is StringValue.Static) {
          addStatement("append(%S, %S)", header.name, header.value)
          continue
        }
        header as StringValue.Dynamic
        val headerTypeName = parameters.getValue(header.parameterName)
        if (headerTypeName.isNullable) {
          beginControlFlow("if (${header.parameterName}·!= null)")
        }

        val headerTypeQualifiedName = headerTypeName.classNameOrNull()?.canonicalName
        when {
          headerTypeQualifiedName == "kotlin.String" -> {
            addStatement("append(%S, ${header.parameterName})", header.name)
          }

          ITERABLE_TYPE_QUALIFIED_NAMES.contains(headerTypeQualifiedName) -> {
            headerTypeName as ParameterizedTypeName
            val iterableTypeArgumentName = headerTypeName.typeArguments[0]
            when {
              iterableTypeArgumentName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
                addStatement(
                  "appendAll(%S, ${header.parameterName}.%M { it%L.toString() })",
                  header.name,
                  MemberName(
                    "kotlin.collections",
                    if (iterableTypeArgumentName.isNullable) "mapNotNull" else "map"
                  ),
                  if (iterableTypeArgumentName.isNullable) "?" else ""
                )
              }

              // Iterable<String?>
              iterableTypeArgumentName.isNullable -> {
                addStatement(
                  "appendAll(%S, ${header.parameterName}.%M())",
                  header.name,
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement("appendAll(%S, ${header.parameterName})", header.name)
              }
            }
          }

          else -> {
            addStatement("append(%S, ${header.parameterName}.toString())", header.name)
          }
        }

        if (headerTypeName.isNullable) {
          endControlFlow()
        }
      }
      headerMapParameterNames.forEach { parameterName ->
        add(
          addMapToParameterBuilderCodeBlock(
            mapParameterName = parameterName,
            mapTypeName = parameters.getValue(parameterName)
          )
        )
      }
      endControlFlow()
    }

    fun contentTypeVariable(variableName: String) = buildCodeBlock {
      content as ServiceDescription.HttpContent.Body
      val parsedContentType = ContentType.parse(content.contentType)
      if (parsedContentType.parameters.isEmpty()) {
        addStatement(
          "val $variableName = %T(%S, %S)",
          ClassNames.Ktor.CONTENT_TYPE,
          parsedContentType.contentType,
          parsedContentType.contentSubtype
        )
      } else {
        add("val $variableName = %T(\n", ClassNames.Ktor.CONTENT_TYPE)
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
                      ClassNames.Ktor.HEADER_VALUE_PARAM
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
    }

    fun requestBodyVariable(variableName: String) = buildCodeBlock {
      content as ServiceDescription.HttpContent.Body
      beginControlFlow("val·$variableName·by·lazy")

      val typeName = parameters.getValue(content.parameterName)
      val className = typeName.classNameOrNull()
      val isHttpBodyClass = className?.nonNull() == ClassNames.HTTP_BODY
      val isOptional = isHttpBodyClass && typeName.isNullable

      if (isOptional) {
        beginControlFlow("${content.parameterName}?.let")
      }

      val contentTypeVariableName = variableName("contentType")
      add(contentTypeVariable(contentTypeVariableName))

      add(
        "%L.%M($contentTypeVariableName).write(\n",
        classProperty(ParameterNames.HTTP_CONTENT_SERIALIZERS, isNestedThis = true),
        MemberName(packageName, FunctionNames.FIRST_WRITER_OF)
      )
      indent()
      add(
        obtainSerializerCodeBlock(
          if (isHttpBodyClass) {
            (typeName as ParameterizedTypeName).typeArguments.first()
          } else {
            typeName
          }
        )
      )
      add(",\n")
      add(
        if (isHttpBodyClass) {
          "${content.parameterName}.value"
        } else {
          content.parameterName
        }
      )
      add(",\n")
      add(contentTypeVariableName)
      add("\n")
      unindent()
      add(")\n")

      if (isOptional) {
        endControlFlow()
      }

      endControlFlow()
    }

    fun formUrlEncodedParametersBuilderVariable(variableName: String) = buildCodeBlock {
      content as ServiceDescription.HttpContent.FormUrlEncoded
      beginControlFlow(
        "val·$variableName·=·%T().apply",
        ClassNames.Ktor.PARAMETERS_BUILDER
      )

      content.parameterToFieldNameMap.forEach { (parameterName, fieldName) ->
        val fieldTypeName = parameters.getValue(parameterName)

        if (fieldTypeName.isNullable) {
          beginControlFlow("if ($parameterName·!= null)")
        }

        val fieldTypeQualifiedName = fieldTypeName.classNameOrNull()?.canonicalName
        when {
          fieldTypeQualifiedName == "kotlin.String" -> {
            addStatement("append(%S, $parameterName)", fieldName)
          }

          ITERABLE_TYPE_QUALIFIED_NAMES.contains(fieldTypeQualifiedName) -> {
            fieldTypeName as ParameterizedTypeName
            val iterableTypeArgumentName = fieldTypeName.typeArguments[0]
            val iterableTypeArgumentQualifiedName = iterableTypeArgumentName.classNameOrNull()?.canonicalName
            when {
              iterableTypeArgumentQualifiedName != "kotlin.String" -> {
                addStatement(
                  "appendAll(%S, $parameterName.%M { it%L.toString() })",
                  fieldName,
                  MemberName(
                    "kotlin.collections",
                    if (iterableTypeArgumentName.isNullable) "mapNotNull" else "map"
                  ),
                  if (iterableTypeArgumentName.isNullable) "?" else ""
                )
              }

              // Iterable<String?>
              iterableTypeArgumentName.isNullable -> {
                addStatement(
                  "appendAll(%S, $parameterName.%M())",
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement("appendAll(%S, $parameterName)", fieldName)
              }
            }
          }

          else -> {
            addStatement("append(%S, $parameterName.toString())", fieldName)
          }
        }

        if (fieldTypeName.isNullable) {
          endControlFlow()
        }
      }

      content.fieldMapParameterNames.forEach { parameterName ->
        add(
          addMapToParameterBuilderCodeBlock(
            mapParameterName = parameterName,
            mapTypeName = parameters.getValue(parameterName)
          )
        )
      }

      endControlFlow()
    }

    fun requestVariable(
      variableName: String,
      urlBuilderVariableName: String,
      headersVariableName: String?,
      bodySupplierExpression: CodeBlock? = null
    ) = buildCodeBlock {
      add("val·$variableName·=·%T(\n", ClassNames.HTTP_REQUEST)
      indent()
      add(
        "method·=·%L,\n",
        CodeBlock.of(
          when {
            method.equals("DELETE", ignoreCase = true) -> "%T.Delete"
            method.equals("GET", ignoreCase = true) -> "%T.Get"
            method.equals("HEAD", ignoreCase = true) -> "%T.Head"
            method.equals("OPTIONS", ignoreCase = true) -> "%T.Options"
            method.equals("PATCH", ignoreCase = true) -> "%T.Patch"
            method.equals("POST", ignoreCase = true) -> "%T.Post"
            method.equals("PUT", ignoreCase = true) -> "%T.Put"
            else -> "%T(\"$method\")"
          },
          ClassNames.Ktor.HTTP_METHOD
        )
      )
      add("url·=·$urlBuilderVariableName.build()")

      if (headersVariableName != null) {
        add(",\n")
        add("headers·=·$headersVariableName")
      }

      if (bodySupplierExpression != null) {
        add(",\n")
        add("bodySupplier·=·%L", bodySupplierExpression)
      } else {
        add("\n")
      }

      unindent()
      add(")\n")
    }

    fun returnFromFunction(resultVariableName: String) = buildCodeBlock returnFromFunctionBlock@{
      val returnsUnit = returnType.classNameOrNull() == UNIT

      if (returnsUnit) {
        add("$resultVariableName.%M()", MemberName("dev.aoddon.connector.http", "successOrThrow"))
        return@returnFromFunctionBlock
      }

      val returnTypeClassName = returnType.classNameOrNull()?.nonNull()
      var isNestedHttpBody = false
      var isNestedHttpBodyNullable = false
      var deserializedResponseBodyTypeName = returnType
      while (
        deserializedResponseBodyTypeName.classNameOrNull().let { className ->
          when (className?.nonNull()) {
            ClassNames.HTTP_RESULT,
            ClassNames.HTTP_RESPONSE,
            ClassNames.HTTP_RESPONSE_SUCCESS,
            ClassNames.HTTP_BODY -> true

            else -> false
          }
        }
      ) {
        deserializedResponseBodyTypeName =
          (deserializedResponseBodyTypeName as ParameterizedTypeName).typeArguments.first()

        if (
          deserializedResponseBodyTypeName.classNameOrNull()?.canonicalName == ClassNames.HTTP_BODY.canonicalName
        ) {
          isNestedHttpBody = true
          isNestedHttpBodyNullable = deserializedResponseBodyTypeName.isNullable
        }
      }

      fun responseBody(
        successVariableName: String,
        isReturnNeeded: Boolean = false
      ) = buildCodeBlock responseBodyBlock@{
        @Suppress("NAME_SHADOWING") var isReturnNeeded = isReturnNeeded

        if (
          deserializedResponseBodyTypeName == STAR ||
          deserializedResponseBodyTypeName.classNameOrNull() == UNIT
        ) {
          addStatement(
            "%L$successVariableName.%M(%T)",
            if (isReturnNeeded) "return·" else "",
            MemberName("dev.aoddon.connector.http", "toSuccess"),
            UNIT
          )
          return@responseBodyBlock
        }

        if (returnType.isNullable && returnTypeClassName == ClassNames.HTTP_BODY) {
          beginControlFlow("if ($successVariableName.body.availableForRead·==·0)")
          addStatement("%Lnull", if (isReturnNeeded) "return·" else "")
          endControlFlow()
        }

        if (isNestedHttpBodyNullable) {
          beginControlFlow(
            "%Lif ($successVariableName.body.availableForRead·==·0)",
            if (isReturnNeeded) "return·" else ""
          )
          isReturnNeeded = false
          addStatement(
            "$successVariableName.%M(null)",
            MemberName("dev.aoddon.connector.http", "toSuccess")
          )
          nextControlFlow("else")
        }

        val contentTypeVariableName = variableName("responseContentType")
        addStatement(
          "val·$contentTypeVariableName·= $successVariableName.%M()",
          MemberName("io.ktor.http", "contentType")
        )

        if (returnTypeClassName == ClassNames.HTTP_RESULT) {
          beginControlFlow("try")
        }

        val deserializedBodyVariableName = variableName("deserializedResponseBody")
        add(
          "%L%L.%M(%L).read(\n",
          when (returnTypeClassName) {
            ClassNames.HTTP_RESULT, ClassNames.HTTP_RESPONSE, ClassNames.HTTP_RESPONSE_SUCCESS, ClassNames.HTTP_BODY -> {
              "val·$deserializedBodyVariableName·=·"
            }
            else -> if (isReturnNeeded) "return·" else ""
          },
          classProperty(ParameterNames.HTTP_CONTENT_SERIALIZERS, isNestedThis = true),
          MemberName(packageName, FunctionNames.FIRST_READER_OF),
          contentTypeVariableName
        )
        indent()
        add(obtainSerializerCodeBlock(deserializedResponseBodyTypeName))
        add(",\n$successVariableName.body,\n$contentTypeVariableName\n")
        unindent()
        add(")\n")

        when (returnTypeClassName) {
          ClassNames.HTTP_RESULT, ClassNames.HTTP_RESPONSE, ClassNames.HTTP_RESPONSE_SUCCESS -> {
            addStatement(
              "%L$successVariableName.%M(%L)",
              if (isReturnNeeded) "return·" else "",
              MemberName("dev.aoddon.connector.http", "toSuccess"),
              if (isNestedHttpBody) {
                buildCodeBlock {
                  add("%T($deserializedBodyVariableName)", ClassNames.HTTP_BODY)
                }
              } else {
                deserializedBodyVariableName
              }
            )
          }
          ClassNames.HTTP_BODY -> {
            addStatement(
              "%L%T($deserializedBodyVariableName)",
              if (isReturnNeeded) "return·" else "",
              ClassNames.HTTP_BODY
            )
          }
          else -> {
          } // do nothing: we've already got the deserialized body
        }

        if (returnTypeClassName == ClassNames.HTTP_RESULT) {
          nextControlFlow("catch (throwable: %T)", ClassNames.THROWABLE)
          addStatement(
            "%L$resultVariableName.%M(throwable)",
            if (isReturnNeeded) "return·" else "",
            MemberName("dev.aoddon.connector.http", "toFailure")
          )
          endControlFlow()
        }

        if (isNestedHttpBodyNullable) {
          endControlFlow()
        }
      }

      when (returnTypeClassName) {
        ClassNames.HTTP_RESULT -> {
          beginControlFlow("return·when($resultVariableName)")

          beginControlFlow("is·%T·->", ClassNames.HTTP_RESPONSE_SUCCESS)
          add(responseBody(successVariableName = resultVariableName))
          endControlFlow()

          addStatement("is·%T·->·$resultVariableName", ClassNames.HTTP_RESPONSE_ERROR)
          addStatement("is·%T·->·$resultVariableName", ClassNames.HTTP_RESULT_FAILURE)

          endControlFlow()
        }

        ClassNames.HTTP_RESPONSE -> {
          val responseVariableName = variableName("response")
          addStatement(
            "val·$responseVariableName·= $resultVariableName.%M()",
            MemberName("dev.aoddon.connector.http", "responseOrThrow")
          )
          beginControlFlow("return·when($responseVariableName)")

          beginControlFlow("is·%T·->", ClassNames.HTTP_RESPONSE_SUCCESS)
          add(responseBody(successVariableName = responseVariableName))
          endControlFlow()

          addStatement("is·%T·->·$responseVariableName", ClassNames.HTTP_RESPONSE_ERROR)

          endControlFlow()
        }

        else -> {
          val successVariableName = variableName("success")
          addStatement(
            "val·$successVariableName·= $resultVariableName.%M()",
            MemberName("dev.aoddon.connector.http", "successOrThrow")
          )
          add(responseBody(successVariableName = successVariableName, isReturnNeeded = true))
        }
      }
    }

    return buildCodeBlock {
      val urlBuilderVariableName = variableName("urlBuilder")
      val headersVariableName = if (headers.isNotEmpty()) variableName("headers") else null
      val requestBodyVariableName = if (content is ServiceDescription.HttpContent.Body) {
        variableName("requestBody")
      } else {
        null
      }
      val formUrlEncodedParametersBuilderVariableName = if (content is ServiceDescription.HttpContent.FormUrlEncoded) {
        variableName("parametersBuilder")
      } else {
        null
      }
      val requestVariableName = variableName("request")
      val resultVariableName = variableName("result")

      add(validateDynamicPathParameters())
      add(urlBuilderVariable(variableName = urlBuilderVariableName))

      if (headersVariableName != null) {
        add(headersVariable(variableName = headersVariableName))
      }

      if (requestBodyVariableName != null) {
        add(requestBodyVariable(variableName = requestBodyVariableName))
      }

      if (formUrlEncodedParametersBuilderVariableName != null) {
        add(
          formUrlEncodedParametersBuilderVariable(
            variableName = formUrlEncodedParametersBuilderVariableName
          )
        )
      }

      val bodySupplierExpression = when {
        requestBodyVariableName != null -> buildCodeBlock {
          content as ServiceDescription.HttpContent.Body
          val typeName = parameters.getValue(content.parameterName)
          val className = typeName.classNameOrNull()
          val isHttpBodyClass = className?.nonNull() == ClassNames.HTTP_BODY
          val isOptional = isHttpBodyClass && typeName.isNullable

          addStatement(
            "{·$requestBodyVariableName%L·}",
            if (isOptional) {
              buildCodeBlock { add(" ?:·%T", ClassNames.Ktor.EMPTY_CONTENT) }
            } else {
              ""
            }
          )
        }

        formUrlEncodedParametersBuilderVariableName != null -> buildCodeBlock {
          add(
            "{ %T(%L.build()) }\n",
            ClassNames.Ktor.FORM_DATA_CONTENT,
            formUrlEncodedParametersBuilderVariableName
          )
        }

        else -> null
      }

      add(
        requestVariable(
          variableName = requestVariableName,
          urlBuilderVariableName = urlBuilderVariableName,
          headersVariableName = headersVariableName,
          bodySupplierExpression = bodySupplierExpression
        )
      )

      add(
        "val·$resultVariableName·= $requestVariableName.%M(%L·+·%M(%L))\n",
        MemberName(packageName, FunctionNames.EXECUTE_WITH),
        classProperty(ParameterNames.HTTP_INTERCEPTORS),
        MemberName(packageName, FunctionNames.HTTP_REQUEST_HANDLER),
        classProperty(ParameterNames.HTTP_CLIENT)
      )

      add(returnFromFunction(resultVariableName = resultVariableName))
    }
  }

  private val httpRequestHandlerFunctionSpec: FunSpec = run {
    val clientParameterName = "client"

    FunSpec.builder(FunctionNames.HTTP_REQUEST_HANDLER)
      .addModifiers(KModifier.PRIVATE, KModifier.SUSPEND, KModifier.INLINE)
      .addParameter(clientParameterName, ClassNames.HTTP_CLIENT)
      .returns(ClassNames.HTTP_INTERCEPTOR)
      .addCode(
        """
        return object : %T {
          public override suspend fun %T.intercept(): %T {
            return request.%M($clientParameterName).execute { response ->
              with(response) {
                when (status.value) {
                  in (200..299) -> request.%M(
                    status = status,
                    headers = headers,
                    body = content,
                    protocol = version,
                    timestamp = responseTime.timestamp,
                    requestTimestamp = requestTime.timestamp
                  )
                  else -> request.%M(
                    status = status,
                    headers = headers,
                    body = content.%M().%M(),
                    protocol = version,
                    timestamp = responseTime.timestamp,
                    requestTimestamp = requestTime.timestamp
                  )
                }
              }
            }
          }
        }
        """.trimIndent(),
        ClassNames.HTTP_INTERCEPTOR,
        ClassNames.HTTP_INTERCEPTOR_CONTEXT,
        ClassNames.HTTP_RESULT.parameterizedBy(ClassNames.Ktor.BYTE_READ_CHANNEL),
        MemberName(packageName, "toHttpStatement"),
        MemberName("dev.aoddon.connector.http", "success"),
        MemberName("dev.aoddon.connector.http", "responseError"),
        MemberName("io.ktor.utils.io", "readRemaining"),
        MemberName("io.ktor.utils.io.core", "readBytes"),
      )
      .build()
  }

  private val executeRequestWithInterceptorsFunctionSpec: FunSpec = run {
    val requestParameterName = "request"
    val interceptorsParameterName = "interceptors"
    val httpResultParameterized = ClassNames.HTTP_RESULT.parameterizedBy(ClassNames.Ktor.BYTE_READ_CHANNEL)

    FunSpec.builder(FunctionNames.EXECUTE_WITH)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE, KModifier.SUSPEND)
      .receiver(ClassNames.HTTP_REQUEST)
      .addParameter(
        ParameterSpec(
          interceptorsParameterName,
          LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR)
        )
      )
      .returns(httpResultParameterized)
      .addCode(
        """
        require($interceptorsParameterName.isNotEmpty()) { "The·list·of·interceptors·should·not·be·empty" }
        var index = -1
        val context = object : %T {
          override var request: %T = this@${FunctionNames.EXECUTE_WITH}
      
          override suspend fun proceedWith(request: %T): %T {
            require(index++ < $interceptorsParameterName.lastIndex) { 
              "The·last·interceptor·should·not·call·'proceedWith'" 
            }
            this.request = $requestParameterName
            return·with($interceptorsParameterName[index]) {
              try {
                intercept()
              } catch (throwable: %T) {
                throwable.%M($requestParameterName)
              }
            }
          }
        }
        return·context.proceedWith(this)
        """.trimIndent(),
        ClassNames.HTTP_INTERCEPTOR_CONTEXT,
        ClassNames.HTTP_REQUEST,
        ClassNames.HTTP_REQUEST,
        httpResultParameterized,
        ClassNames.THROWABLE,
        MemberName(packageName, FunctionNames.AS_HTTP_RESULT)
      )
      .build()
  }

  private val toHttpStatementFunctionSpec: FunSpec = run {
    val clientParameterName = "client"

    FunSpec.builder(FunctionNames.TO_HTTP_STATEMENT)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE, KModifier.SUSPEND)
      .receiver(ClassNames.HTTP_REQUEST)
      .addParameter(
        ParameterSpec(
          clientParameterName,
          ClassNames.HTTP_CLIENT
        )
      )
      .returns(ClassNames.Ktor.HTTP_STATEMENT)
      .addCode(
        """
        return·$clientParameterName.%M {
          method·=·this@${FunctionNames.TO_HTTP_STATEMENT}.method
          url.%M(this@toHttpStatement.url)
          body·=·bodySupplier()
          headers.appendAll(this@${FunctionNames.TO_HTTP_STATEMENT}.headers)
        }
        """.trimIndent(),
        MemberName("io.ktor.client.request", "request"),
        MemberName("io.ktor.http", "takeFrom")
      )
      .build()
  }

  private val throwableAsHttpResultFunctionSpec: FunSpec = run {
    val requestParameterName = "request"

    FunSpec.builder(FunctionNames.AS_HTTP_RESULT)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE, KModifier.SUSPEND)
      .receiver(ClassNames.THROWABLE)
      .addParameter(
        ParameterSpec(
          requestParameterName,
          ClassNames.HTTP_REQUEST
        )
      )
      .returns(ClassNames.HTTP_RESULT.parameterizedBy(NOTHING))
      .addCode(
        """
        if·(this !is %T)·return·request.%M(this)
        return·request.%M(
          status·=·response.status,
          headers·=·response.headers,
          body·=·response.content.%M().%M(),
          protocol·=·response.version,
          timestamp·=·response.responseTime.timestamp,
          requestTimestamp·=·response.requestTime.timestamp
        )
        """.trimIndent(),
        ClassNames.Ktor.RESPONSE_EXCEPTION,
        MemberName("dev.aoddon.connector.http", "failure"),
        MemberName("dev.aoddon.connector.http", "responseError"),
        MemberName("io.ktor.utils.io", "readRemaining"),
        MemberName("io.ktor.utils.io.core", "readBytes"),
      )
      .build()
  }

  private val firstWriterOfFunctionSpec: FunSpec = run {
    val contentTypeParameterName = "contentType"
    FunSpec.builder(FunctionNames.FIRST_WRITER_OF)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
      .receiver(LIST.plusParameter(ClassNames.HTTP_CONTENT_SERIALIZER))
      .addParameter(
        ParameterSpec(
          contentTypeParameterName,
          ClassNames.Ktor.CONTENT_TYPE
        )
      )
      .returns(ClassNames.HTTP_CONTENT_SERIALIZER)
      .addCode(
        buildCodeBlock {
          add("return·find·{·it.canWrite($contentTypeParameterName)·}·?:·error(\n")
          indent()
          add(
            "\"No·suitable·%L·found·for·writing·Content-Type:·'$$contentTypeParameterName'\"\n",
            ClassNames.HTTP_CONTENT_SERIALIZER.simpleName
          )
          unindent()
          add(")\n")
        }
      )
      .build()
  }

  private val firstReaderOfFunctionSpec: FunSpec = run {
    val contentTypeParameterName = "contentType"
    FunSpec.builder(FunctionNames.FIRST_READER_OF)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
      .receiver(LIST.plusParameter(ClassNames.HTTP_CONTENT_SERIALIZER))
      .addParameter(
        ParameterSpec(
          contentTypeParameterName,
          ClassNames.Ktor.CONTENT_TYPE.copy(nullable = true)
        )
      )
      .returns(ClassNames.HTTP_CONTENT_SERIALIZER)
      .addCode(
        buildCodeBlock {
          add("return·find·{·it.canRead($contentTypeParameterName)·}·?:·error(\n")
          indent()
          add(
            "\"No·suitable·%L·found·for·reading·Content-Type:·'$$contentTypeParameterName'\"\n",
            ClassNames.HTTP_CONTENT_SERIALIZER.simpleName
          )
          unindent()
          add(")\n")
        }
      )
      .build()
  }

  private val ensureValidBaseUrlFunctionSpec: FunSpec = run {
    FunSpec.builder(FunctionNames.ENSURE_VALID_BASE_URL)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
      .receiver(ClassNames.Ktor.URL)
      .addCode(
        buildCodeBlock {
          beginControlFlow(
            "require(protocol·== %T.HTTP || protocol·== %T.HTTPS)",
            URLProtocol::class,
            URLProtocol::class
          )
          addStatement(
            "%L",
            "\"Base·URL·protocol·must·be·HTTP·or·HTTPS.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow("require(parameters.isEmpty())")
          addStatement(
            "%L",
            "\"Base·URL·should·not·have·query·parameters.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow("require(fragment.isEmpty())")
          addStatement(
            "%L",
            "\"Base·URL·fragment·should·be·empty.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow(
            "require(%M.endsWith(%L))",
            MemberName("io.ktor.http", "fullPath"),
            "'/'"
          )
          addStatement(
            "%L",
            "\"Base·URL·should·end·in·'/'.·Found:·\$this\""
          )
          endControlFlow()
        }
      )
      .build()
  }

  private val dynamicUrlFunctionSpec: FunSpec = run {
    FunSpec.builder(FunctionNames.DYNAMIC_URL)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
      .receiver(ClassNames.Ktor.URL_BUILDER)
      .addParameter(ParameterNames.DYNAMIC_URL, STRING)
      .addParameter(ParameterNames.BASE_URL, ClassNames.Ktor.URL)
      .returns(ClassNames.Ktor.URL_BUILDER)
      .addCode(
        buildCodeBlock {
          addStatement(
            "val·path·= %L.takeWhile·{ it·!=·'?'·&& it·!=·'#' }",
            ParameterNames.DYNAMIC_URL,
          )

          beginControlFlow("when")

          // Protocol-relative
          beginControlFlow(
            "path.startsWith(%S) ->",
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
            "path.startsWith(%S) ->",
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
          beginControlFlow("path.%M() ->", MemberName(packageName, FunctionNames.IS_FULL_URL))
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
          addStatement("val·afterPath·= ${ParameterNames.DYNAMIC_URL}.substring(path.length)")
          add(
            """
            // 'null' if there is no '?'
            val queryString: String? = if (afterPath[0] == '?') {
              afterPath.drop(1).takeWhile { it != '#' }
            } else {
              null
            }
            if (queryString?.isEmpty() == true) {
              trailingQuery = true
            }
            if (queryString?.isNotEmpty() == true) {
              parameters.appendAll(%M(queryString))
            }
            """.trimIndent(),
            MemberName("io.ktor.http", "parseQueryString"),
          )
          add("\n")
          add(
            """
            fragment = if (afterPath[0] == '#') {
              afterPath.drop(1)
            } else {
              afterPath.substringAfter("#", missingDelimiterValue = "")
            }
            """.trimIndent()
          )
          add("\n")
          endControlFlow()

          addStatement("return·this")
        }
      )
      .build()
  }

  private val isFullUrlFunctionSpec = FunSpec.builder(FunctionNames.IS_FULL_URL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(STRING)
    .returns(BOOLEAN)
    .addCode(
      """
      for (index in indices) {
        val char = get(index)
        return·if (index == 0 && char !in 'a'..'z' && char !in 'A'..'Z') {
          false
        } else when (char) {
          ':' -> true
          in 'a'..'z', in 'A'..'Z', in '0'..'9', '+', '-', '.' -> continue
          else -> false
        }
      }
      return·false
      """.trimIndent()
    )
    .build()

  private val ensureNoPathTraversalFunctionSpec = FunSpec.builder(FunctionNames.ENSURE_NO_PATH_TRAVERSAL)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(ANY.copy(nullable = true))
    .addCode(
      buildCodeBlock {
        addStatement("if (this·==·null) return")
        beginControlFlow(
          "require(toString().split('/').none·{ it.%M() })",
          MemberName(packageName, FunctionNames.IS_PATH_TRAVERSAL_SEGMENT)
        )
        addStatement(
          "%L",
          "\"@Path·arguments·should·not·introduce·path·traversal.·Found:·'\$this'\""
        )
        endControlFlow()
      }
    )
    .build()

  private val isPathTraversalSegmentFunctionSpec = FunSpec.builder(FunctionNames.IS_PATH_TRAVERSAL_SEGMENT)
    .addAnnotation(suppressNothingToInlineAnnotationSpec)
    .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
    .receiver(STRING)
    .returns(BOOLEAN)
    .addCode(
      buildCodeBlock {
        beginControlFlow("return·when")

        beginControlFlow("startsWith('.') ->")
        addStatement(
          "length·==·1·|| length·==·2·&&·endsWith('.')·|| length·==·4·&&·endsWith(%S, ignoreCase·=·true)",
          "%2E",
        )
        endControlFlow()

        beginControlFlow("startsWith(%S, ignoreCase·=·true) ->", "%2E")
        addStatement(
          "length·==·3·|| length·==·4·&&·endsWith('.')·|| length·==·6·&&·endsWith(%S, ignoreCase·=·true)",
          "%2E",
        )
        endControlFlow()

        addStatement("else -> false")

        endControlFlow()
      }
    )
    .build()
}

private fun obtainSerializerCodeBlock(typeToSerializeName: TypeName): CodeBlock = buildCodeBlock {
  when (typeToSerializeName) {
    is ClassName -> {
      when (typeToSerializeName.canonicalName) {
        "kotlin.Boolean", "kotlin.Byte", "kotlin.Char",
        "kotlin.Double", "kotlin.Float", "kotlin.Int",
        "kotlin.Long", "kotlin.Short", "kotlin.String" -> {
          add(
            "%T.%M()",
            typeToSerializeName.nonNull(),
            MemberName("kotlinx.serialization.builtins", "serializer")
          )
        }

        "kotlin.BooleanArray", "kotlin.ByteArray", "kotlin.CharArray",
        "kotlin.DoubleArray", "kotlin.FloatArray", "kotlin.IntArray",
        "kotlin.LongArray", "kotlin.ShortArray" -> {
          val serializerName = "${typeToSerializeName.simpleName}Serializer"
          add("%M()", MemberName("kotlinx.serialization.builtins", serializerName))
        }

        else -> {
          add("%T.serializer()", typeToSerializeName.nonNull())
        }
      }
    }

    is ParameterizedTypeName -> {
      val literalPlaceholders = typeToSerializeName.typeArguments.joinToString { "%L" }
      val literalArgs = typeToSerializeName.typeArguments.map { obtainSerializerCodeBlock(it) }.toTypedArray()

      when (typeToSerializeName.rawType.canonicalName) {
        "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set", "kotlin.collections.Map",
        "kotlin.collections.Map.Entry", "kotlin.Pair", "kotlin.Triple" -> {
          val serializerName = "${typeToSerializeName.rawType.simpleNames.joinToString("")}Serializer"
          add(
            "%M($literalPlaceholders)",
            MemberName("kotlinx.serialization.builtins", serializerName),
            *literalArgs
          )
        }

        else -> {
          add(
            "%T.serializer($literalPlaceholders)",
            typeToSerializeName.rawType.nonNull(),
            *literalArgs
          )
        }
      }
    }

    else -> error("Expected 'typeName' to be either a ClassName or a ParameterizedTypeName")
  }

  if (typeToSerializeName.isNullable) {
    add(".%M", MemberName("kotlinx.serialization.builtins", "nullable"))
  }
}

// 'parametersBuilderNameReference' should be null if the target builder is a receiver type in the current scope.
private fun addMapToParameterBuilderCodeBlock(
  mapParameterName: String,
  mapTypeName: TypeName,
  parametersBuilderNameReference: String? = null
): CodeBlock = buildCodeBlock {
  when (mapTypeName.classNameOrNull()?.canonicalName) {
    StringValues::class.qualifiedName -> {
      if (mapTypeName.isNullable) {
        beginControlFlow("if ($mapParameterName·!= null)")
      }
      addStatement(
        "%LappendAll($mapParameterName)",
        parametersBuilderNameReference?.let { "$it." }.orEmpty()
      )
      if (mapTypeName.isNullable) {
        endControlFlow()
      }
    }

    "kotlin.collections.Map" -> {
      val valueTypeName = (mapTypeName as ParameterizedTypeName).typeArguments[1]
      val valueTypeQualifiedName = valueTypeName.classNameOrNull()?.canonicalName

      beginControlFlow(
        "$mapParameterName%L.%M { (key, value) ->",
        if (mapTypeName.isNullable) "?" else "",
        MemberName("kotlin.collections", "forEach")
      )

      if (valueTypeName.isNullable) {
        beginControlFlow("if (value != null)")
      }

      when {
        valueTypeQualifiedName == "kotlin.String" -> {
          addStatement(
            "%Lappend(key, value)",
            parametersBuilderNameReference?.let { "$it." }.orEmpty()
          )
        }

        ITERABLE_TYPE_QUALIFIED_NAMES.contains(valueTypeQualifiedName) -> {
          val iterableTypeArgumentName = (valueTypeName as ParameterizedTypeName).typeArguments[0]
          when {
            iterableTypeArgumentName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
              addStatement(
                "%LappendAll(key, value.%M { it%L.toString() })",
                parametersBuilderNameReference?.let { "$it." }.orEmpty(),
                MemberName(
                  "kotlin.collections",
                  if (iterableTypeArgumentName.isNullable) "mapNotNull" else "map"
                ),
                if (iterableTypeArgumentName.isNullable) "?" else ""
              )
            }

            // Iterable<String?>
            iterableTypeArgumentName.isNullable -> {
              addStatement(
                "%LappendAll(key, value.%M())",
                parametersBuilderNameReference?.let { "$it." }.orEmpty(),
                MemberName("kotlin.collections", "filterNotNull")
              )
            }

            // Iterable<String>
            else -> {
              addStatement(
                "%LappendAll(key, value)",
                parametersBuilderNameReference?.let { "$it." }.orEmpty()
              )
            }
          }
        }

        else -> {
          addStatement(
            "%Lappend(key, value.toString())",
            parametersBuilderNameReference?.let { "$it." }.orEmpty()
          )
        }
      }

      if (valueTypeName.isNullable) {
        endControlFlow()
      }

      endControlFlow()
    }

    else -> error("Unexpected parameter type: $mapTypeName")
  }
}

private val suppressNothingToInlineAnnotationSpec = AnnotationSpec
  .builder(ClassNames.SUPPRESS)
  .addMember("%S", "NOTHING_TO_INLINE")
  .build()

private object ClassNames {
  object Ktor {
    val BYTE_READ_CHANNEL = ByteReadChannel::class.asClassName()
    val CONTENT_TYPE = ContentType::class.asClassName()
    val EMPTY_CONTENT = EmptyContent::class.asClassName()
    val FORM_DATA_CONTENT = FormDataContent::class.asClassName()
    val HEADER_VALUE_PARAM = HeaderValueParam::class.asClassName()
    val HTTP_METHOD = HttpMethod::class.asClassName()
    val HTTP_STATEMENT = HttpStatement::class.asClassName()
    val PARAMETERS_BUILDER = ParametersBuilder::class.asClassName()
    val RESPONSE_EXCEPTION = ResponseException::class.asClassName()
    val URL = Url::class.asClassName()
    val URL_BUILDER = URLBuilder::class.asClassName()
  }

  val HTTP_BODY = HttpBody::class.asClassName()
  val HTTP_CONTENT_SERIALIZER = HttpContentSerializer::class.asClassName()
  val HTTP_CLIENT = HttpClient::class.asClassName()
  val HTTP_INTERCEPTOR = HttpInterceptor::class.asClassName()
  val HTTP_INTERCEPTOR_CONTEXT = HttpInterceptor.Context::class.asClassName()
  val HTTP_REQUEST = HttpRequest::class.asClassName()
  val HTTP_RESPONSE = HttpResponse::class.asClassName()
  val HTTP_RESPONSE_ERROR = HttpResponse.Error::class.asClassName()
  val HTTP_RESPONSE_SUCCESS = HttpResponse.Success::class.asClassName()
  val HTTP_RESULT = HttpResult::class.asClassName()
  val HTTP_RESULT_FAILURE = HttpResult.Failure::class.asClassName()
  val SUPPRESS = Suppress::class.asClassName()
  val THROWABLE = Throwable::class.asClassName()
}

private object ParameterNames {
  const val BASE_URL = "baseUrl"
  const val DYNAMIC_URL = "dynamicUrl"
  const val HTTP_CLIENT = "httpClient"
  const val HTTP_CONTENT_SERIALIZERS = "httpContentSerializers"
  const val HTTP_INTERCEPTORS = "httpInterceptors"
}

private object FunctionNames {
  const val AS_HTTP_RESULT = "asHttpResult"
  const val DYNAMIC_URL = "dynamicUrl"
  const val ENSURE_NO_PATH_TRAVERSAL = "ensureNoPathTraversal"
  const val ENSURE_VALID_BASE_URL = "ensureValidBaseUrl"
  const val EXECUTE_WITH = "executeWith"
  const val FIRST_READER_OF = "firstReaderOf"
  const val FIRST_WRITER_OF = "firstWriterOf"
  const val HTTP_REQUEST_HANDLER = "httpRequestHandler"
  const val IS_FULL_URL = "isFullUrl"
  const val IS_PATH_TRAVERSAL_SEGMENT = "isPathTraversalSegment"
  const val TO_HTTP_STATEMENT = "toHttpStatement"
}

private val ITERABLE_TYPE_QUALIFIED_NAMES = listOf(
  "kotlin.collections.Collection",
  "kotlin.collections.Iterable",
  "kotlin.collections.List",
  "kotlin.collections.Set",
)
