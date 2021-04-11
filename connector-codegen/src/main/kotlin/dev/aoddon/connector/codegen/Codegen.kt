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
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import dev.aoddon.connector.codegen.util.classNameOrNull
import dev.aoddon.connector.codegen.util.escape
import dev.aoddon.connector.codegen.util.noBreakingSpaces
import dev.aoddon.connector.codegen.util.nonNull
import dev.aoddon.connector.http.HttpBody
import dev.aoddon.connector.http.HttpBodySerializer
import dev.aoddon.connector.http.HttpInterceptor
import dev.aoddon.connector.http.HttpRequest
import dev.aoddon.connector.http.HttpResponse
import dev.aoddon.connector.http.HttpResult
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.HttpMethod
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job

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
      .addFunction(executeRequestFunctionSpec)
      .addFunction(toHttpRequestBuilderFunctionSpec)
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
      .builder(ParameterNames.HTTP_CLIENT, ClassNames.Ktor.HTTP_CLIENT)
      .build()

    val httpBodySerializersParameter = ParameterSpec
      .builder(
        ParameterNames.HTTP_BODY_SERIALIZERS,
        LIST.plusParameter(ClassNames.HTTP_BODY_SERIALIZER)
      )
      .defaultValue("%M()", MemberName("kotlin.collections", "emptyList"))
      .build()

    val httpInterceptorsParameter = ParameterSpec
      .builder(
        ParameterNames.HTTP_INTERCEPTORS,
        LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR)
      )
      .defaultValue("%M()", MemberName("kotlin.collections", "emptyList"))
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
            ParameterNames.HTTP_BODY_SERIALIZERS,
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
          .addParameter(ParameterNames.HTTP_CLIENT, ClassNames.Ktor.HTTP_CLIENT)
          .addParameter(
            ParameterNames.HTTP_BODY_SERIALIZERS,
            LIST.plusParameter(ClassNames.HTTP_BODY_SERIALIZER)
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
          .builder(ParameterNames.HTTP_CLIENT, ClassNames.Ktor.HTTP_CLIENT, KModifier.PRIVATE)
          .initializer(ParameterNames.HTTP_CLIENT)
          .build()
      )
      .addProperty(
        PropertySpec
          .builder(
            ParameterNames.HTTP_BODY_SERIALIZERS,
            LIST.plusParameter(ClassNames.HTTP_BODY_SERIALIZER),
            KModifier.PRIVATE
          )
          .initializer(ParameterNames.HTTP_BODY_SERIALIZERS)
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
          addFunction(serviceFunction.toFunSpec())
        }
      }
      .build()
  }

  private fun ServiceDescription.Function.Http.toFunSpec(): FunSpec {
    return FunSpec.builder(name)
      .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
      .addParameters(parameters())
      .addCode(body())
      .returns(returnType)
      .build()
  }

  private fun ServiceDescription.Function.Http.parameters(): List<ParameterSpec> =
    parameters.map { (parameterName, parameterType) ->
      ParameterSpec(name = parameterName, type = parameterType)
    }

  private fun ServiceDescription.Function.Http.body(): CodeBlock {
    fun nestedThisProperty(name: String): String = when {
      parameters.containsKey(name) -> "this.$name"
      else -> name
    }

    fun serviceClassProperty(name: String, isNestedThis: Boolean = false): String = when {
      !parameters.containsKey(name) -> name
      isNestedThis -> "this@$implementationClassName.$name"
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
          url.valueProviderParametersByReplaceBlock.forEach { (toReplace, parameterName) ->
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
            url.valueProviderParametersByReplaceBlock.forEach { (toReplace, parameterName) ->
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
                serviceClassProperty(ParameterNames.BASE_URL, isNestedThis = true),
              )
              addStatement(
                "%L·= %L.%M()",
                nestedThisProperty("encodedPath"),
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
                "\${${serviceClassProperty(ParameterNames.BASE_URL, isNestedThis = true)}.protocol.name}",
                path,
              )
            }

            UrlType.RELATIVE -> {
              addStatement(
                "%M(%L)",
                MemberName("io.ktor.http", "takeFrom"),
                serviceClassProperty(ParameterNames.BASE_URL, isNestedThis = true),
              )
              addStatement(
                "%L·+= %L.%M()",
                nestedThisProperty("encodedPath"),
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
              addStatement("%L = true", nestedThisProperty("trailingQuery"))
            }

            if (queryString?.isNotEmpty() == true) {
              addStatement(
                "%L.appendAll(%M(%S))",
                nestedThisProperty("parameters"),
                MemberName("io.ktor.http", "parseQueryString"),
                queryString
              )
            }

            if (fragment?.isNotEmpty() == true) {
              addStatement("%L·= %S", nestedThisProperty("fragment"), fragment)
            }
          }
        }

        is ServiceDescription.Url.Dynamic -> {
          when (val typeName = parameters.getValue(url.valueProviderParameter)) {
            ClassNames.Ktor.URL -> {
              addStatement(
                "%M(%L)",
                MemberName("io.ktor.http", "takeFrom"),
                url.valueProviderParameter,
              )
            }

            else -> {
              addStatement(
                "%M(%L%L, %L)",
                MemberName(packageName, FunctionNames.DYNAMIC_URL),
                url.valueProviderParameter,
                if (typeName != STRING) ".toString()" else "",
                serviceClassProperty(ParameterNames.BASE_URL, isNestedThis = true)
              )
            }
          }
        }
      }
      url.dynamicQueryParameters.forEach { queryContent ->
        when (queryContent) {
          is ServiceDescription.Url.QueryParameter.Single.HasValue -> {
            val parameterTypeName = parameters.getValue(queryContent.valueProviderParameter)
            if (parameterTypeName.isNullable) {
              beginControlFlow("if (${queryContent.valueProviderParameter}·!=·null)")
            }

            addStatement(
              "%L.append(%S, %L)",
              nestedThisProperty("parameters"),
              queryContent.name,
              when (parameterTypeName.classNameOrNull()?.canonicalName) {
                "kotlin.String" -> queryContent.valueProviderParameter
                else -> "${queryContent.valueProviderParameter}.toString()"
              }
            )

            if (parameterTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.Url.QueryParameter.Single.NoValue -> {
            val parameterTypeName = parameters.getValue(queryContent.nameProviderParameter)
            if (parameterTypeName.isNullable) {
              beginControlFlow("if (${queryContent.nameProviderParameter}·!=·null)")
            }

            addStatement(
              "%L.appendAll(%L, %M())",
              nestedThisProperty("parameters"),
              when (parameterTypeName.classNameOrNull()?.canonicalName) {
                "kotlin.String" -> queryContent.nameProviderParameter
                else -> "${queryContent.nameProviderParameter}.toString()"
              },
              MemberName("kotlin.collections", "emptyList")
            )

            if (parameterTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.Url.QueryParameter.Iterable.HasValue -> {
            val parameterTypeName = parameters.getValue(queryContent.valueProviderParameter)
            if (parameterTypeName.isNullable) {
              beginControlFlow("if (${queryContent.valueProviderParameter}·!=·null)")
            }

            when {
              queryContent.type.valueTypeName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
                addStatement(
                  "%L.appendAll(%S, ${queryContent.valueProviderParameter}.%M { it%L.toString() })",
                  nestedThisProperty("parameters"),
                  queryContent.name,
                  MemberName(
                    "kotlin.collections",
                    if (queryContent.type.valueTypeName.isNullable) "mapNotNull" else "map"
                  ),
                  if (queryContent.type.valueTypeName.isNullable) "?" else ""
                )
              }

              // Iterable<String?>
              queryContent.type.valueTypeName.isNullable -> {
                addStatement(
                  "%L.appendAll(%S, ${queryContent.valueProviderParameter}.%M())",
                  nestedThisProperty("parameters"),
                  queryContent.name,
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement(
                  "%L.appendAll(%S, ${queryContent.valueProviderParameter})",
                  nestedThisProperty("parameters"),
                  queryContent.name
                )
              }
            }

            if (parameterTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.Url.QueryParameter.Iterable.NoValue -> {
            val parameterTypeName = parameters.getValue(queryContent.nameProviderParameter)

            beginControlFlow(
              "${queryContent.nameProviderParameter}%L.%M",
              if (parameterTypeName.isNullable) "?" else "",
              MemberName("kotlin.collections", "forEach")
            )

            if (queryContent.type.valueTypeName.isNullable) {
              beginControlFlow("if·(it·!=·null)")
            }

            addStatement(
              "%L.appendAll(it%L, %M())",
              nestedThisProperty("parameters"),
              when (queryContent.type.valueTypeName.classNameOrNull()?.canonicalName) {
                "kotlin.String" -> ""
                else -> ".toString()"
              },
              MemberName("kotlin.collections", "emptyList")
            )

            if (queryContent.type.valueTypeName.isNullable) {
              endControlFlow()
            }

            endControlFlow()
          }

          is ServiceDescription.Url.QueryParameter.Map -> {
            add(
              addMapToParameterBuilderCodeBlock(
                parameterName = queryContent.valueProviderParameter,
                typeName = parameters.getValue(queryContent.valueProviderParameter),
                mapType = queryContent.type,
                parametersBuilderNameReference = nestedThisProperty("parameters")
              )
            )
          }
        }
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
      headers.forEach { headerContent ->
        when (headerContent) {
          is ServiceDescription.Function.Http.Header.SingleStatic -> {
            addStatement("append(%S, %S)", headerContent.name, headerContent.value)
          }

          is ServiceDescription.Function.Http.Header.SingleDynamic -> {
            val headerTypeName = parameters.getValue(headerContent.valueProviderParameter)

            if (headerTypeName.isNullable) {
              beginControlFlow("if (${headerContent.valueProviderParameter}·!= null)")
            }

            when (headerTypeName.classNameOrNull()?.canonicalName) {
              "kotlin.String" -> {
                addStatement("append(%S, ${headerContent.valueProviderParameter})", headerContent.name)
              }
              else -> {
                addStatement("append(%S, ${headerContent.valueProviderParameter}.toString())", headerContent.name)
              }
            }

            if (headerTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.Function.Http.Header.DynamicIterable -> {
            val headerTypeName = parameters.getValue(headerContent.valueProviderParameter) as ParameterizedTypeName

            if (headerTypeName.isNullable) {
              beginControlFlow("if (${headerContent.valueProviderParameter}·!= null)")
            }

            val iterableTypeArgumentName = headerTypeName.typeArguments[0]
            when {
              iterableTypeArgumentName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
                addStatement(
                  "appendAll(%S, ${headerContent.valueProviderParameter}.%M { it%L.toString() })",
                  headerContent.name,
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
                  "appendAll(%S, ${headerContent.valueProviderParameter}.%M())",
                  headerContent.name,
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement("appendAll(%S, ${headerContent.valueProviderParameter})", headerContent.name)
              }
            }

            if (headerTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.Function.Http.Header.DynamicMap -> {
            add(
              addMapToParameterBuilderCodeBlock(
                parameterName = headerContent.valueProviderParameter,
                typeName = parameters.getValue(headerContent.valueProviderParameter),
                mapType = headerContent.type
              )
            )
          }
        }
      }

      endControlFlow()
    }

    fun contentTypeVariable(variableName: String, contentType: String) = buildCodeBlock {
      val parsedContentType = ContentType.parse(contentType)
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
      beginControlFlow("val·$variableName·by·%M", MemberName("kotlin", "lazy"))

      val typeName = parameters.getValue(content.valueProviderParameter)
      val className = typeName.classNameOrNull()
      val isHttpBodyClass = className?.nonNull() == ClassNames.HTTP_BODY
      val isOptional = isHttpBodyClass && typeName.isNullable

      if (isOptional) {
        beginControlFlow("${content.valueProviderParameter}?.%M", MemberName("kotlin", "let"))
      }

      val contentTypeVariableName = variableName("contentType")
      add(
        contentTypeVariable(
          variableName = contentTypeVariableName,
          contentType = content.contentType
        )
      )

      add(
        "%L.%M($contentTypeVariableName).write(\n",
        serviceClassProperty(ParameterNames.HTTP_BODY_SERIALIZERS, isNestedThis = true),
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
          "${content.valueProviderParameter}.value"
        } else {
          content.valueProviderParameter
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

    fun multipartRequestBodyVariable(variableName: String) = buildCodeBlock {
      content as ServiceDescription.HttpContent.Multipart
      beginControlFlow("val·$variableName·by·%M", MemberName("kotlin", "lazy"))
      beginControlFlow(
        "%M",
        MemberName("dev.aoddon.connector.http.multipart", "MultipartOutgoingContent")
      )

      addStatement("%L·= %S", nestedThisProperty("subtype"), content.subtype)

      fun appendRawPart(
        referenceLiteral: String,
        typeName: TypeName
      ) = buildCodeBlock {
        if (typeName.isNullable) {
          beginControlFlow("$referenceLiteral?.%M", MemberName("kotlin", "let"))
        }
        addStatement("appendPart(%L)", referenceLiteral)
        if (typeName.isNullable) {
          endControlFlow()
        }
      }

      fun appendPart(
        referenceLiteral: String,
        typeName: TypeName,
        contentType: String,
        formFieldNameLiteral: String?,
        isIsolatedBlock: Boolean = false
      ) = buildCodeBlock {
        val className = typeName.classNameOrNull()
        val isHttpBodyClass = className?.nonNull() == ClassNames.HTTP_BODY
        val isOptional = isHttpBodyClass && typeName.isNullable

        if (isOptional) {
          beginControlFlow("$referenceLiteral?.%M", MemberName("kotlin", "let"))
        } else if (!isIsolatedBlock) {
          beginControlFlow("$referenceLiteral.%M", MemberName("kotlin", "let"))
        }

        val contentTypeVariableName = variableName("contentType")
        add(contentTypeVariable(variableName = contentTypeVariableName, contentType = contentType))
        if (formFieldNameLiteral != null) {
          add("appendFormPart(\n")
          indent()
          add("%L,\n", formFieldNameLiteral)
        } else {
          add("appendPart(\n")
          indent()
        }
        add(
          "%L.%M(%L).write(\n",
          serviceClassProperty(ParameterNames.HTTP_BODY_SERIALIZERS, isNestedThis = true),
          MemberName(packageName, FunctionNames.FIRST_WRITER_OF),
          contentTypeVariableName
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
            "$referenceLiteral.value"
          } else {
            referenceLiteral
          }
        )
        add(",\n")
        add(contentTypeVariableName)
        add("\n")
        unindent()
        add(")\n")
        unindent()
        add(")\n")

        if (isOptional || !isIsolatedBlock) {
          endControlFlow()
        }
      }

      content.parts.forEach { partContent ->
        when (partContent) {
          is ServiceDescription.HttpContent.Multipart.PartContent.Single -> {
            val typeName = parameters.getValue(partContent.valueProviderParameter)
            // null metadata encodes raw parts
            if (partContent.metadata == null) {
              add(appendRawPart(referenceLiteral = partContent.valueProviderParameter, typeName = typeName))
            } else {
              add(
                appendPart(
                  referenceLiteral = partContent.valueProviderParameter,
                  typeName = typeName,
                  contentType = partContent.metadata.contentType,
                  formFieldNameLiteral = partContent.metadata.formFieldName?.let { "\"$it\"" }
                )
              )
            }
          }

          is ServiceDescription.HttpContent.Multipart.PartContent.Iterable -> {
            val iterableTypeName = parameters.getValue(partContent.valueProviderParameter)
            beginControlFlow(
              "%L%L.%M",
              partContent.valueProviderParameter,
              if (iterableTypeName.isNullable) "?" else "",
              MemberName("kotlin.collections", "forEach")
            )
            // null metadata encodes raw parts
            if (partContent.metadata == null) {
              add(appendRawPart(referenceLiteral = "it", typeName = partContent.type.valueTypeName))
            } else {
              add(
                appendPart(
                  referenceLiteral = "it",
                  typeName = partContent.type.valueTypeName,
                  contentType = partContent.metadata.contentType,
                  formFieldNameLiteral = partContent.metadata.formFieldName?.let { "\"$it\"" },
                  isIsolatedBlock = true
                )
              )
            }
            endControlFlow()
          }

          is ServiceDescription.HttpContent.Multipart.PartContent.Map -> {
            val mapTypeName = parameters.getValue(partContent.valueProviderParameter)
            val partTypeName = when (partContent.type) {
              ServiceDescription.MapType.KtorStringValues -> STRING
              is ServiceDescription.MapType.IterableKeyValuePairs -> partContent.type.valueType.typeName
              is ServiceDescription.MapType.Map -> partContent.type.valueType.typeName
            }
            beginControlFlow(
              "%L%L.%M { %Lname, value%L ->",
              partContent.valueProviderParameter,
              if (mapTypeName.isNullable) "?" else "",
              if (partContent.type == ServiceDescription.MapType.KtorStringValues) {
                MemberName("io.ktor.util", "flattenForEach")
              } else {
                MemberName("kotlin.collections", "forEach")
              },
              if (partContent.type != ServiceDescription.MapType.KtorStringValues) "(" else "",
              if (partContent.type != ServiceDescription.MapType.KtorStringValues) ")" else "",
            )
            add(
              appendPart(
                referenceLiteral = "value",
                typeName = partTypeName,
                contentType = partContent.contentType,
                formFieldNameLiteral = "name",
                isIsolatedBlock = true
              )
            )
            endControlFlow()
          }
        }
      }

      endControlFlow()
      endControlFlow()
    }

    fun formUrlEncodedParametersBuilderVariable(variableName: String) = buildCodeBlock {
      content as ServiceDescription.HttpContent.FormUrlEncoded
      beginControlFlow(
        "val·$variableName·=·%T().%M",
        ClassNames.Ktor.PARAMETERS_BUILDER,
        MemberName("kotlin", "apply")
      )

      content.fields.forEach { fieldContent ->
        when (fieldContent) {
          is ServiceDescription.HttpContent.FormUrlEncoded.FieldContent.Single -> {
            val fieldTypeName = parameters.getValue(fieldContent.valueProviderParameter)

            if (fieldTypeName.isNullable) {
              beginControlFlow("if (${fieldContent.valueProviderParameter}·!= null)")
            }

            when (fieldTypeName.classNameOrNull()?.canonicalName) {
              "kotlin.String" -> {
                addStatement("append(%S, ${fieldContent.valueProviderParameter})", fieldContent.name)
              }
              else -> {
                addStatement("append(%S, ${fieldContent.valueProviderParameter}.toString())", fieldContent.name)
              }
            }

            if (fieldTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.HttpContent.FormUrlEncoded.FieldContent.Iterable -> {
            val fieldTypeName = parameters.getValue(fieldContent.valueProviderParameter) as ParameterizedTypeName

            if (fieldTypeName.isNullable) {
              beginControlFlow("if (${fieldContent.valueProviderParameter}·!= null)")
            }

            val iterableTypeArgumentName = fieldTypeName.typeArguments[0]
            val iterableTypeArgumentQualifiedName = iterableTypeArgumentName.classNameOrNull()?.canonicalName
            when {
              iterableTypeArgumentQualifiedName != "kotlin.String" -> {
                addStatement(
                  "appendAll(%S, ${fieldContent.valueProviderParameter}.%M { it%L.toString() })",
                  fieldContent.name,
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
                  "appendAll(%S, ${fieldContent.valueProviderParameter}.%M())",
                  MemberName("kotlin.collections", "filterNotNull")
                )
              }

              // Iterable<String>
              else -> {
                addStatement("appendAll(%S, ${fieldContent.valueProviderParameter})", fieldContent.name)
              }
            }

            if (fieldTypeName.isNullable) {
              endControlFlow()
            }
          }

          is ServiceDescription.HttpContent.FormUrlEncoded.FieldContent.Map -> {
            add(
              addMapToParameterBuilderCodeBlock(
                parameterName = fieldContent.valueProviderParameter,
                typeName = parameters.getValue(fieldContent.valueProviderParameter),
                mapType = fieldContent.type
              )
            )
          }
        }
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

    fun rawResultMapper(resultVariableName: String) = buildCodeBlock rawResultMapperBlock@{
      if (streamingLambdaProviderParameter != null) {
        val streamingLambdaType = parameters.getValue(streamingLambdaProviderParameter) as LambdaTypeName
        when (streamingLambdaType.parameters.single().type.classNameOrNull()) {
          ClassNames.HTTP_RESULT -> {
            addStatement("$streamingLambdaProviderParameter($resultVariableName)")
          }
          ClassNames.HTTP_RESPONSE -> {
            addStatement(
              "$streamingLambdaProviderParameter($resultVariableName.%M())",
              MemberName("dev.aoddon.connector.http", "responseOrThrow")
            )
          }
          ClassNames.HTTP_RESPONSE_SUCCESS -> {
            addStatement(
              "$streamingLambdaProviderParameter($resultVariableName.%M())",
              MemberName("dev.aoddon.connector.http", "successOrThrow")
            )
          }
          ClassNames.Ktor.BYTE_READ_CHANNEL -> {
            addStatement(
              "$streamingLambdaProviderParameter($resultVariableName.%M())",
              MemberName("dev.aoddon.connector.http", "successBodyOrThrow")
            )
          }
          else -> error("Unexpected streaming lambda parameter type: $streamingLambdaType")
        }
        return@rawResultMapperBlock
      }

      val returnsUnit = returnType.classNameOrNull() == UNIT

      if (returnsUnit) {
        addStatement(
          "$resultVariableName.%M()",
          MemberName("dev.aoddon.connector.http", "successOrThrow")
        )
        return@rawResultMapperBlock
      }

      val returnTypeClassName = returnType.classNameOrNull()?.nonNull()
      var isNestedHttpBody = false
      var isNullableNestedHttpBody = false
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
          isNullableNestedHttpBody = deserializedResponseBodyTypeName.isNullable
        }
      }

      fun responseBody(successVariableName: String) = buildCodeBlock responseBodyBlock@{
        if (
          deserializedResponseBodyTypeName == STAR ||
          deserializedResponseBodyTypeName.classNameOrNull() == UNIT
        ) {
          addStatement(
            "$successVariableName.%M(%T)",
            MemberName("dev.aoddon.connector.http", "toSuccess"),
            UNIT
          )
          return@responseBodyBlock
        }

        val isNullableHttpBody = returnType.isNullable && returnTypeClassName == ClassNames.HTTP_BODY
        if (isNullableHttpBody) {
          beginControlFlow(
            "if ($successVariableName.status.value == 204 || $successVariableName.%M()·==·0L)",
            MemberName("io.ktor.http", "contentLength")
          )
          addStatement("null")
          nextControlFlow("else")
        }

        if (isNullableNestedHttpBody) {
          beginControlFlow(
            "if ($successVariableName.status.value == 204 || $successVariableName.%M()·==·0L)",
            MemberName("io.ktor.http", "contentLength")
          )
          addStatement(
            "$successVariableName.%M(null)",
            MemberName("dev.aoddon.connector.http", "toSuccess")
          )
          nextControlFlow("else")
        }

        val contentTypeVariableName = variableName("contentType")
        addStatement(
          "val·$contentTypeVariableName·= $successVariableName.%M()",
          MemberName("io.ktor.http", "contentType")
        )

        if (returnTypeClassName == ClassNames.HTTP_RESULT) {
          beginControlFlow("try")
        }

        val deserializedBodyVariableName = variableName("deserializedBody")
        add(
          "%L%L.%M(%L).read(\n",
          when (returnTypeClassName) {
            ClassNames.HTTP_RESULT, ClassNames.HTTP_RESPONSE, ClassNames.HTTP_RESPONSE_SUCCESS, ClassNames.HTTP_BODY -> {
              "val·$deserializedBodyVariableName·=·"
            }
            else -> ""
          },
          serviceClassProperty(ParameterNames.HTTP_BODY_SERIALIZERS, isNestedThis = true),
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
              "$successVariableName.%M(%L)",
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
              "%T($deserializedBodyVariableName)",
              ClassNames.HTTP_BODY
            )
          }
          else -> {
          } // do nothing: we've already got the deserialized body
        }

        if (returnTypeClassName == ClassNames.HTTP_RESULT) {
          nextControlFlow("catch (throwable: %T)", ClassNames.THROWABLE)
          addStatement(
            "$resultVariableName.%M(throwable)",
            MemberName("dev.aoddon.connector.http", "toFailure")
          )
          endControlFlow()
        }

        if (isNullableHttpBody || isNullableNestedHttpBody) {
          endControlFlow()
        }
      }

      when (returnTypeClassName) {
        ClassNames.HTTP_RESULT -> {
          beginControlFlow("when ($resultVariableName)")

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
          beginControlFlow("when ($responseVariableName)")

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
          add(responseBody(successVariableName = successVariableName))
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
      val multipartRequestBodyVariableName = if (content is ServiceDescription.HttpContent.Multipart) {
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

      if (multipartRequestBodyVariableName != null) {
        add(multipartRequestBodyVariable(variableName = multipartRequestBodyVariableName))
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
          val typeName = parameters.getValue(content.valueProviderParameter)
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

        multipartRequestBodyVariableName != null -> buildCodeBlock {
          content as ServiceDescription.HttpContent.Multipart
          addStatement("{·$multipartRequestBodyVariableName·}")
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

      beginControlFlow(
        "return $requestVariableName.%M(%L, %L) { $resultVariableName ->",
        MemberName(packageName, FunctionNames.EXECUTE),
        serviceClassProperty(ParameterNames.HTTP_CLIENT),
        serviceClassProperty(ParameterNames.HTTP_INTERCEPTORS),
      )

      add(rawResultMapper(resultVariableName = resultVariableName))

      endControlFlow()
    }
  }

  private val executeRequestFunctionSpec: FunSpec = run {
    val clientParameterName = "client"
    val interceptorsParameterName = "interceptors"
    val blockParameterName = "block"

    val httpResultParameterized = ClassNames.HTTP_RESULT.parameterizedBy(ClassNames.Ktor.BYTE_READ_CHANNEL)
    val blockResultT = TypeVariableName("T")

    FunSpec.builder(FunctionNames.EXECUTE)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE, KModifier.SUSPEND)
      .addTypeVariable(blockResultT)
      .receiver(ClassNames.HTTP_REQUEST)
      .addParameter(
        ParameterSpec(
          clientParameterName,
          ClassNames.Ktor.HTTP_CLIENT
        )
      )
      .addParameter(
        ParameterSpec(
          interceptorsParameterName,
          LIST.plusParameter(ClassNames.HTTP_INTERCEPTOR)
        )
      )
      .addParameter(
        ParameterSpec(
          blockParameterName,
          LambdaTypeName
            .get(
              parameters = listOf(ParameterSpec(name = "", type = httpResultParameterized)),
              returnType = blockResultT
            )
            .copy(suspending = true),
          modifiers = listOf(KModifier.CROSSINLINE)
        )
      )
      .returns(blockResultT)
      .addCode(
        """
        var index = -1
        var dispose: (suspend () -> Unit)? = null
      
        val context = object : %T {
          override var request: %T = this@execute
      
          override suspend fun proceedWith(request: %T): %T {
            this.request = request
            return try {
              if (++index < $interceptorsParameterName.size) %M($interceptorsParameterName[index]) { intercept() } else {
                val builder = request.%M()
                val call = $clientParameterName.requestPipeline.execute(builder, builder.body) as %T
                %M(call.response) {
                  val responseBody = %M()
                  dispose = {
                    val job = coroutineContext[%T] as %T
                    job.complete()
                    %M { responseBody.cancel(null) }
                    job.join()
                  }
                  when (status.value) {
                    in (200..299) -> request.%M(
                      status = status,
                      headers = headers,
                      body = responseBody,
                      protocol = version,
                      timestamp = responseTime.timestamp,
                      requestTimestamp = requestTime.timestamp
                    )
                    else -> request.%M(
                      status = status,
                      headers = headers,
                      body = responseBody.%M().%M(),
                      protocol = version,
                      timestamp = responseTime.timestamp,
                      requestTimestamp = requestTime.timestamp
                    )
                  }
                }
              }
            } catch (throwable: %T) {
              request.%M(throwable)
            }
          }
        }
        try {
          return block(context.proceedWith(this))
        } finally {
          dispose?.invoke()
        }
        """.trimIndent(),
        ClassNames.HTTP_INTERCEPTOR_CONTEXT,
        ClassNames.HTTP_REQUEST,
        ClassNames.HTTP_REQUEST,
        httpResultParameterized,
        MemberName("kotlin", "with"),
        MemberName(packageName, FunctionNames.TO_HTTP_REQUEST_BUILDER),
        ClassNames.Ktor.HTTP_CLIENT_CALL,
        MemberName("kotlin", "with"),
        MemberName("io.ktor.client.statement", "bodyAsChannel"),
        ClassNames.JOB,
        ClassNames.COMPLETABLE_JOB,
        MemberName("kotlin", "runCatching"),
        MemberName("dev.aoddon.connector.http", "success"),
        MemberName("dev.aoddon.connector.http", "responseError"),
        MemberName("io.ktor.utils.io", "readRemaining"),
        MemberName("io.ktor.utils.io.core", "readBytes"),
        ClassNames.THROWABLE,
        MemberName("dev.aoddon.connector.http", "failure")
      )
      .build()
  }

  private val toHttpRequestBuilderFunctionSpec: FunSpec = run {
    FunSpec.builder(FunctionNames.TO_HTTP_REQUEST_BUILDER)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE, KModifier.SUSPEND)
      .receiver(ClassNames.HTTP_REQUEST)
      .returns(ClassNames.Ktor.HTTP_REQUEST_BUILDER)
      .addCode(
        """
        return·%T().%M·{
          method·=·this@${FunctionNames.TO_HTTP_REQUEST_BUILDER}.method
          url.%M(this@${FunctionNames.TO_HTTP_REQUEST_BUILDER}.url)
          headers.appendAll(this@${FunctionNames.TO_HTTP_REQUEST_BUILDER}.headers)
          %M(bodySupplier())
          %M·=·false
        }
        """.trimIndent(),
        ClassNames.Ktor.HTTP_REQUEST_BUILDER,
        MemberName("kotlin", "apply"),
        MemberName("io.ktor.http", "takeFrom"),
        MemberName("io.ktor.client.request", "setBody"),
        MemberName("io.ktor.client.features", "expectSuccess")
      )
      .build()
  }

  private val firstWriterOfFunctionSpec: FunSpec = run {
    val contentTypeParameterName = "contentType"
    FunSpec.builder(FunctionNames.FIRST_WRITER_OF)
      .addAnnotation(suppressNothingToInlineAnnotationSpec)
      .addModifiers(KModifier.PRIVATE, KModifier.INLINE)
      .receiver(LIST.plusParameter(ClassNames.HTTP_BODY_SERIALIZER))
      .addParameter(
        ParameterSpec(
          contentTypeParameterName,
          ClassNames.Ktor.CONTENT_TYPE
        )
      )
      .returns(ClassNames.HTTP_BODY_SERIALIZER)
      .addCode(
        buildCodeBlock {
          add(
            "return·%M·{·it.canWrite($contentTypeParameterName)·}·?:·%M(\n",
            MemberName("kotlin.collections", "find"),
            MemberName("kotlin", "error")
          )
          indent()
          add(
            "\"No·suitable·%L·found·for·writing·Content-Type:·'$$contentTypeParameterName'\"\n",
            ClassNames.HTTP_BODY_SERIALIZER.simpleName
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
      .receiver(LIST.plusParameter(ClassNames.HTTP_BODY_SERIALIZER))
      .addParameter(
        ParameterSpec(
          contentTypeParameterName,
          ClassNames.Ktor.CONTENT_TYPE.copy(nullable = true)
        )
      )
      .returns(ClassNames.HTTP_BODY_SERIALIZER)
      .addCode(
        buildCodeBlock {
          add(
            "return·%M·{·it.canRead($contentTypeParameterName)·}·?:·%M(\n",
            MemberName("kotlin.collections", "find"),
            MemberName("kotlin", "error")
          )
          indent()
          add(
            "\"No·suitable·%L·found·for·reading·Content-Type:·'$$contentTypeParameterName'\"\n",
            ClassNames.HTTP_BODY_SERIALIZER.simpleName
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
            "%M(protocol·== %T.HTTP || protocol·== %T.HTTPS)",
            MemberName("kotlin", "require"),
            URLProtocol::class,
            URLProtocol::class
          )
          addStatement(
            "%L",
            "\"Base·URL·protocol·must·be·HTTP·or·HTTPS.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow("%M(parameters.isEmpty())", MemberName("kotlin", "require"))
          addStatement(
            "%L",
            "\"Base·URL·should·not·have·query·parameters.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow(
            "%M(fragment.%M())",
            MemberName("kotlin", "require"),
            MemberName("kotlin.text", "isEmpty")
          )
          addStatement(
            "%L",
            "\"Base·URL·fragment·should·be·empty.·Found:·\$this\""
          )
          endControlFlow()

          beginControlFlow(
            "%M(%M.%M(%L))",
            MemberName("kotlin", "require"),
            MemberName("io.ktor.http", "fullPath"),
            MemberName("kotlin.text", "endsWith"),
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
            "val·path·= %L.%M·{ it·!=·'?'·&& it·!=·'#' }",
            ParameterNames.DYNAMIC_URL,
            MemberName("kotlin.text", "takeWhile")
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
          addStatement(
            "val·afterPath·= ${ParameterNames.DYNAMIC_URL}.%M(path.length)",
            MemberName("kotlin.text", "substring")
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
            MemberName("kotlin.text", "substringAfter")
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
          "require(toString().%M('/').%M·{ it.%M() })",
          MemberName("kotlin.text", "split"),
          MemberName("kotlin.collections", "none"),
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

        beginControlFlow("%M('.') ->", MemberName("kotlin.text", "startsWith"))
        addStatement(
          "length·==·1·|| length·==·2·&&·%M('.')·|| length·==·4·&&·%M(%S, ignoreCase·=·true)",
          MemberName("kotlin.text", "endsWith"),
          MemberName("kotlin.text", "endsWith"),
          "%2E",
        )
        endControlFlow()

        beginControlFlow(
          "%M(%S, ignoreCase·=·true) ->",
          MemberName("kotlin.text", "startsWith"),
          "%2E"
        )
        addStatement(
          "length·==·3·|| length·==·4·&&·%M('.')·|| length·==·6·&&·%M(%S, ignoreCase·=·true)",
          MemberName("kotlin.text", "endsWith"),
          MemberName("kotlin.text", "endsWith"),
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
  parameterName: String,
  typeName: TypeName,
  mapType: ServiceDescription.MapType,
  parametersBuilderNameReference: String? = null
): CodeBlock = buildCodeBlock {
  when (mapType) {
    ServiceDescription.MapType.KtorStringValues -> {
      if (typeName.isNullable) {
        beginControlFlow("if ($parameterName·!= null)")
      }
      addStatement(
        "%LappendAll($parameterName)",
        parametersBuilderNameReference?.let { "$it." }.orEmpty()
      )
      if (typeName.isNullable) {
        endControlFlow()
      }
    }

    is ServiceDescription.MapType.Map -> {
      beginControlFlow(
        "$parameterName%L.%M { (key, value) ->",
        if (typeName.isNullable) "?" else "",
        MemberName("kotlin.collections", "forEach")
      )

      if (mapType.valueType.typeName.isNullable) {
        beginControlFlow("if (value != null)")
      }

      val valueTypeQualifiedName = mapType.valueType.typeName.classNameOrNull()?.canonicalName

      when {
        valueTypeQualifiedName == "kotlin.String" -> {
          addStatement(
            "%Lappend(key, value)",
            parametersBuilderNameReference?.let { "$it." }.orEmpty()
          )
        }

        mapType.valueType is ServiceDescription.MapType.ValueType.Iterable -> {
          when {
            mapType.valueType.valueTypeName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
              addStatement(
                "%LappendAll(key, value.%M { it%L.toString() })",
                parametersBuilderNameReference?.let { "$it." }.orEmpty(),
                MemberName(
                  "kotlin.collections",
                  if (mapType.valueType.valueTypeName.isNullable) "mapNotNull" else "map"
                ),
                if (mapType.valueType.valueTypeName.isNullable) "?" else ""
              )
            }

            // Iterable<String?>
            mapType.valueType.valueTypeName.isNullable -> {
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

      if (mapType.valueType.typeName.isNullable) {
        endControlFlow()
      }

      endControlFlow()
    }

    is ServiceDescription.MapType.IterableKeyValuePairs -> {
      beginControlFlow(
        "$parameterName%L.%M { (key, value) ->",
        if (typeName.isNullable) "?" else "",
        MemberName("kotlin.collections", "forEach")
      )

      if (mapType.valueType.typeName.isNullable) {
        beginControlFlow("if (value != null)")
      }

      val valueTypeQualifiedName = mapType.valueType.typeName.classNameOrNull()?.canonicalName

      when {
        valueTypeQualifiedName == "kotlin.String" -> {
          addStatement(
            "%Lappend(key, value)",
            parametersBuilderNameReference?.let { "$it." }.orEmpty()
          )
        }

        mapType.valueType is ServiceDescription.MapType.ValueType.Iterable -> {
          when {
            mapType.valueType.valueTypeName.classNameOrNull()?.canonicalName != "kotlin.String" -> {
              addStatement(
                "%LappendAll(key, value.%M { it%L.toString() })",
                parametersBuilderNameReference?.let { "$it." }.orEmpty(),
                MemberName(
                  "kotlin.collections",
                  if (mapType.valueType.valueTypeName.isNullable) "mapNotNull" else "map"
                ),
                if (mapType.valueType.valueTypeName.isNullable) "?" else ""
              )
            }

            // Iterable<String?>
            mapType.valueType.valueTypeName.isNullable -> {
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

      if (mapType.valueType.typeName.isNullable) {
        endControlFlow()
      }

      endControlFlow()
    }
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
    val HTTP_CLIENT = HttpClient::class.asClassName()
    val HTTP_CLIENT_CALL = HttpClientCall::class.asClassName()
    val HTTP_METHOD = HttpMethod::class.asClassName()
    val HTTP_REQUEST_BUILDER = HttpRequestBuilder::class.asClassName()
    val PARAMETERS_BUILDER = ParametersBuilder::class.asClassName()
    val URL = Url::class.asClassName()
    val URL_BUILDER = URLBuilder::class.asClassName()
  }

  val COMPLETABLE_JOB = CompletableJob::class.asClassName()
  val JOB = Job::class.asClassName()
  val HTTP_BODY = HttpBody::class.asClassName()
  val HTTP_BODY_SERIALIZER = HttpBodySerializer::class.asClassName()
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
  const val HTTP_BODY_SERIALIZERS = "httpBodySerializers"
  const val HTTP_CLIENT = "httpClient"
  const val HTTP_INTERCEPTORS = "httpInterceptors"
}

private object FunctionNames {
  const val DYNAMIC_URL = "dynamicUrl"
  const val ENSURE_NO_PATH_TRAVERSAL = "ensureNoPathTraversal"
  const val ENSURE_VALID_BASE_URL = "ensureValidBaseUrl"
  const val EXECUTE = "execute"
  const val FIRST_READER_OF = "firstReaderOf"
  const val FIRST_WRITER_OF = "firstWriterOf"
  const val IS_FULL_URL = "isFullUrl"
  const val IS_PATH_TRAVERSAL_SEGMENT = "isPathTraversalSegment"
  const val TO_HTTP_REQUEST_BUILDER = "toHttpRequestBuilder"
}
