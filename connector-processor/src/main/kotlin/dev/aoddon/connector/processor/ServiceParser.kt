package dev.aoddon.connector.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import dev.aoddon.connector.codegen.ServiceDescription
import dev.aoddon.connector.codegen.UrlType
import dev.aoddon.connector.processor.util.OnTypeArgumentResolvedListener
import dev.aoddon.connector.processor.util.className
import dev.aoddon.connector.processor.util.classNameOrNull
import dev.aoddon.connector.processor.util.isInterface
import dev.aoddon.connector.processor.util.isTopLevel
import dev.aoddon.connector.processor.util.packageName
import dev.aoddon.connector.processor.util.typeName
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.IllegalHeaderNameException
import io.ktor.http.IllegalHeaderValueException
import io.ktor.http.content.PartData
import java.lang.StringBuilder

public class ServiceParser(private val logger: KSPLogger) {
  public fun parse(classDeclaration: KSClassDeclaration): ServiceDescription = with(classDeclaration) {
    if (!isInterface || !isTopLevel) {
      logger.error("@Service target must be a top-level interface.", classDeclaration)
    }

    if (superTypes.isNotEmpty()) {
      logger.error("@Service interfaces cannot have supertypes.", classDeclaration)
    }

    if (typeParameters.isNotEmpty()) {
      logger.error("@Service interfaces cannot have type parameters.", classDeclaration)
    }

    val serviceName = simpleName.getShortName()
    val serviceFunctions = declarations
      .mapNotNull { declaration ->
        if (declaration is KSPropertyDeclaration) {
          logger.error("Properties are not allowed in @Service interfaces.", declaration)
        }
        (declaration as? KSFunctionDeclaration)?.parseServiceFunction()
      }

    return ServiceDescription(
      name = serviceName,
      functions = serviceFunctions,
      parentInterface = className()!!
    )
  }

  private fun KSFunctionDeclaration.parseServiceFunction(): ServiceDescription.Function? {
    val httpMethodAnnotations = findHttpMethodAnnotations()
    if (httpMethodAnnotations.isEmpty()) {
      logger.error("All functions in @Service interfaces must be annotated with an HTTP method.", this)
    }
    if (httpMethodAnnotations.size > 1) {
      logger.error(
        "Multiple HTTP method annotations are not allowed. " +
          "Found: ${httpMethodAnnotations.joinToString { it.name }}",
        this
      )
    }
    if (!modifiers.contains(Modifier.SUSPEND)) {
      logger.error("All functions in @Service interfaces must be suspension functions.", this)
    }
    if (!isAbstract) {
      logger.error("Functions with a body are not allowed in @Service interfaces.", this)
    }
    if (typeParameters.isNotEmpty()) {
      logger.error("Functions with type parameters are not allowed in @Service interfaces.", this)
    }

    val formUrlEncodedAnnotations = findFormUrlEncodedAnnotations()
    if (formUrlEncodedAnnotations.size > 1) {
      logger.error("@FormUrlEncoded is allowed at most once on a function.", this)
    }
    val multipartAnnotations = findMultipartAnnotations()
    if (multipartAnnotations.size > 1) {
      logger.error("@Multipart is allowed at most once on a function.", this)
    }
    if (formUrlEncodedAnnotations.isNotEmpty() && multipartAnnotations.isNotEmpty()) {
      logger.error("@FormUrlEncoded and @Multipart are not allowed together on the same function.", this)
    }
    if (formUrlEncodedAnnotations.isNotEmpty()) {
      httpMethodAnnotations.forEach { httpMethodAnnotation ->
        if (!httpMethodAnnotation.isBodyAllowed) {
          logger.error(
            "@FormUrlEncoded can only be used with HTTP methods that allow a request body, " +
              "but found method: ${httpMethodAnnotation.method}",
            this
          )
        }
      }
    }
    if (multipartAnnotations.isNotEmpty()) {
      httpMethodAnnotations.forEach { httpMethodAnnotation ->
        if (!httpMethodAnnotation.isBodyAllowed) {
          logger.error(
            "@Multipart can only be used with HTTP methods that allow a request body, " +
              "but found method: ${httpMethodAnnotation.method}",
            this
          )
        }
      }
    }

    val allParameterAnnotations = parameters
      .flatMap { parameter ->
        val parameterAnnotations = parameter.findHttpParameterAnnotations()
        if (parameterAnnotations.isEmpty()) {
          logger.error(
            "All parameters in a @Service function must have an appropriate Connector annotation.",
            parameter
          )
        }
        if (parameterAnnotations.size > 1) {
          logger.error(
            "Multiple Connector annotations are not allowed on the same parameter.",
            parameter
          )
        }
        parameterAnnotations
      }

    val bodyAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Body>()
    if (bodyAnnotations.size > 1) {
      logger.error("Multiple @Body parameters are not allowed.", this)
    }
    bodyAnnotations.forEach { bodyAnnotation ->
      if (bodyAnnotation.contentType.isNotEmpty()) {
        try {
          ContentType.parse(bodyAnnotation.contentType)
        } catch (badFormatException: BadContentTypeFormatException) {
          logger.error(
            "Invalid Content-Type format: '${bodyAnnotation.contentType}'.",
            bodyAnnotation.annotation
          )
        }
      }
      httpMethodAnnotations.forEach { httpMethodAnnotation ->
        if (!httpMethodAnnotation.isBodyAllowed) {
          logger.error(
            "@Body is not allowed in ${httpMethodAnnotation.method} requests.",
            bodyAnnotation.annotation
          )
        }
      }
      if (formUrlEncodedAnnotations.isNotEmpty()) {
        logger.error(
          "@Body is not allowed in @FormUrlEncoded requests.",
          bodyAnnotation.annotation
        )
      }
      if (multipartAnnotations.isNotEmpty()) {
        logger.error(
          "@Body is not allowed in @Multipart requests.",
          bodyAnnotation.annotation
        )
      }
    }

    val fieldAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Field>()
    if (formUrlEncodedAnnotations.isEmpty()) {
      fieldAnnotations.forEach { fieldAnnotation ->
        val annotationName = fieldAnnotation.annotation.shortName.asString()
        logger.error(
          "@$annotationName can only be used in @FormUrlEncoded requests.",
          fieldAnnotation.annotation
        )
      }
    }
    if (formUrlEncodedAnnotations.isNotEmpty() && fieldAnnotations.isEmpty()) {
      logger.error(
        "@FormUrlEncoded functions must have at least one @Field or @FieldMap parameter.",
        this
      )
    }

    val partAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Part>()
    if (multipartAnnotations.isEmpty()) {
      partAnnotations.forEach { partAnnotation ->
        val annotationName = partAnnotation.annotation.shortName.asString()
        logger.error(
          "@$annotationName can only be used in @Multipart requests.",
          partAnnotation.annotation
        )
      }
    }
    if (multipartAnnotations.isNotEmpty() && partAnnotations.isEmpty()) {
      logger.error(
        "@Multipart functions must have at least one @Part, @PartIterable, or @PartMap parameter.",
        this
      )
    }

    val urlAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Url>()
    if (urlAnnotations.size > 1) {
      logger.error("Multiple @URL parameters are not allowed.", this)
    }
    httpMethodAnnotations.forEach { httpMethodAnnotation ->
      if (httpMethodAnnotation.urlTemplate == null && urlAnnotations.isEmpty()) {
        logger.error("URL must be provided either by @${httpMethodAnnotation.name} or via @URL.", this)
      }
      if (httpMethodAnnotation.urlTemplate != null && urlAnnotations.isNotEmpty()) {
        logger.error("URL cannot be provided by both @${httpMethodAnnotation.name} and @URL.", this)
      }
    }

    val pathAnnotationsByName = allParameterAnnotations
      .filterIsInstance<HttpParameterAnnotation.Path>()
      .groupBy { it.name }

    httpMethodAnnotations.forEach { httpMethodAnnotation ->
      val urlTemplate = httpMethodAnnotation.urlTemplate
      val urlTemplateParameters = urlTemplate?.let { URL_TEMPLATE_PARAMETER_REGEX.findAll(it) }
      val questionMarkIndex = urlTemplate?.indexOf('?') ?: -1

      val dynamicQueryParameters = mutableListOf<String>()
      val expectedPathParameterNames = urlTemplateParameters
        ?.filter { matchResult ->
          if (questionMarkIndex >= 0 && matchResult.range.first > questionMarkIndex) {
            dynamicQueryParameters.add(matchResult.value)
            return@filter false
          }
          return@filter true
        }
        ?.map { it.value.removeSurrounding(prefix = "{", suffix = "}") }
        .orEmpty()
        .toMutableSet()

      if (dynamicQueryParameters.isNotEmpty()) {
        logger.error(
          "Dynamic query parameters must be provided via @Query function parameters. " +
            "Found in the query string: ${dynamicQueryParameters.joinToString()}",
          httpMethodAnnotation.annotation
        )
      }

      pathAnnotationsByName.forEach { (name, occurrences) ->
        if (occurrences.size > 1) {
          logger.error(
            "@Path '$name' is defined ${occurrences.size} times, but at most once is allowed.",
            this
          )
        }
        if (!name.matches(URL_TEMPLATE_PARAMETER_NAME_REGEX)) {
          occurrences.forEach { pathAnnotation ->
            logger.error(
              "Invalid @Path parameter name '$name'. Expected format: $URL_TEMPLATE_PARAMETER_NAME_PATTERN",
              pathAnnotation.annotation
            )
          }
        } else if (!expectedPathParameterNames.contains(name)) {
          occurrences.forEach { pathAnnotation ->
            logger.error(
              "@${httpMethodAnnotation.name} URL does not define a dynamic path parameter matching '$name'.",
              pathAnnotation.annotation
            )
          }
        }
        expectedPathParameterNames.remove(name)
      }
      expectedPathParameterNames.forEach { missingPathParameter ->
        logger.error(
          "Missing @Path for '$missingPathParameter', which is defined in the @${httpMethodAnnotation.name} URL.",
          this
        )
      }
    }

    val dynamicQueryParameters: List<ServiceDescription.Url.QueryContent> = allParameterAnnotations
      .asSequence()
      .filterIsInstance<HttpParameterAnnotation.Query>()
      .mapNotNull { queryAnnotation ->
        when (queryAnnotation) {
          is HttpParameterAnnotation.Query.Single -> {
            ServiceDescription.Url.QueryContent.Parameter(
              parameterName = queryAnnotation.parameter.name?.asString() ?: return@mapNotNull null,
              key = queryAnnotation.name
            )
          }
          is HttpParameterAnnotation.Query.Map -> {
            ServiceDescription.Url.QueryContent.Map(
              parameterName = queryAnnotation.parameter.name?.asString() ?: return@mapNotNull null
            )
          }
        }
      }
      .toList()

    val httpMethodAnnotation = httpMethodAnnotations.firstOrNull()
    val url = httpMethodAnnotation?.let {
      if (httpMethodAnnotation.urlTemplate != null) {
        val parameterNamesByReplaceBlock = pathAnnotationsByName
          .asSequence()
          .mapNotNull { (name, pathAnnotations) ->
            val annotation = pathAnnotations.firstOrNull() ?: return@mapNotNull null
            val parameterName = annotation.parameter.name?.asString() ?: return@mapNotNull null
            "{$name}" to parameterName
          }
          .toMap()

        ServiceDescription.Url.Template(
          value = httpMethodAnnotation.urlTemplate,
          type = httpMethodAnnotation.urlTemplate.urlType(httpMethodAnnotation.annotation),
          parameterNamesByReplaceBlock = parameterNamesByReplaceBlock,
          dynamicQueryParameters = dynamicQueryParameters
        )
      } else {
        urlAnnotations.firstOrNull()?.parameter?.name?.asString()?.let { parameterName ->
          ServiceDescription.Url.Dynamic(
            parameterName = parameterName,
            dynamicQueryParameters = dynamicQueryParameters
          )
        }
      }
    }

    val serviceFunctionParameters: Map<String, TypeName>? = allParameterAnnotations
      .mapNotNull { httpParameterAnnotation ->
        val parameterName = httpParameterAnnotation.parameter.name?.asString()
        val parameterType = httpParameterAnnotation.parameter.type.resolve()

        if (
          httpParameterAnnotation is HttpParameterAnnotation.Path &&
          parameterType.nullability == Nullability.NULLABLE
        ) {
          logger.error(
            "Nullable types are not allowed for @Path parameters.",
            httpParameterAnnotation.annotation
          )
        }

        if (
          httpParameterAnnotation is HttpParameterAnnotation.Url &&
          parameterType.nullability == Nullability.NULLABLE
        ) {
          logger.error(
            "Nullable types are not allowed for @URL parameters.",
            httpParameterAnnotation.annotation
          )
        }

        val typeName = when (httpParameterAnnotation) {
          is HttpParameterAnnotation.Body -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_BODY_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Field.Map -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Header.Map -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Part.Single -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_PART_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Part.Iterable -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_PART_ITERABLE_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Part.Map -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_PART_MAP_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Query.Map -> parameterType.typeNameWithValidation(
            target = TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER,
            targetNode = httpParameterAnnotation.annotation,
          )

          is HttpParameterAnnotation.Field.Single,
          is HttpParameterAnnotation.Header.Single,
          is HttpParameterAnnotation.Path,
          is HttpParameterAnnotation.Query.Single,
          is HttpParameterAnnotation.Url -> parameterType.typeName()
        }

        if (parameterName != null && typeName != null) {
          parameterName to typeName
        } else {
          null
        }
      }
      .toMap()
      .takeIf { it.size == allParameterAnnotations.size }

    val headers: List<ServiceDescription.Function.Http.HeaderContent> = findStaticHeaders() + allParameterAnnotations
      .asSequence()
      .filterIsInstance<HttpParameterAnnotation.Header>()
      .mapNotNull { headerAnnotation ->
        when (headerAnnotation) {
          is HttpParameterAnnotation.Header.Single -> {
            val headerName = headerAnnotation.name
            if (headerName == HttpHeaders.ContentType) {
              logger.error(
                "${HttpHeaders.ContentType} header cannot be defined via @Header.",
                headerAnnotation.annotation
              )
              return@mapNotNull null
            }
            if (headerName == HttpHeaders.ContentLength) {
              logger.error(
                "${HttpHeaders.ContentLength} header cannot be defined via @Header.",
                headerAnnotation.annotation
              )
              return@mapNotNull null
            }
            ServiceDescription.Function.Http.HeaderContent.Parameter(
              parameterName = headerAnnotation.parameter.name?.asString() ?: return@mapNotNull null,
              headerName = headerName
            )
          }

          is HttpParameterAnnotation.Header.Map -> {
            ServiceDescription.Function.Http.HeaderContent.Map(
              parameterName = headerAnnotation.parameter.name?.asString() ?: return@mapNotNull null
            )
          }
        }
      }
      .toList()

    val returnType = returnType?.resolve()
    val returnTypeName = returnType?.typeNameWithValidation(
      target = TypeValidationTarget.HTTP_FUNCTION_RETURN,
      targetNode = this
    )

    // Validate return type for @HEAD
    if (
      httpMethodAnnotations.any { it.name == "HEAD" } &&
      returnTypeName?.isValidReturnTypeForHttpHead() != true
    ) {
      logger.error("@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type.", this)
    }

    val isMultipartForm = multipartAnnotations.any { it.subtype == "form-data" }
    val parts: List<ServiceDescription.HttpContent.Multipart.PartContent> = partAnnotations
      .mapNotNull { partAnnotation ->
        val parameterName = partAnnotation.parameter.name?.asString() ?: return@mapNotNull null

        if (partAnnotation is HttpParameterAnnotation.Part.Map) {
          return@mapNotNull ServiceDescription.HttpContent.Multipart.PartContent.Map(
            parameterName = parameterName,
            contentType = partAnnotation.contentType
          )
        }

        val partParameterType = serviceFunctionParameters?.get(parameterName) ?: return@mapNotNull null

        val isPartData = with(partParameterType) {
          val canonicalName = classNameOrNull()?.canonicalName
          when {
            canonicalName == null -> false

            PART_DATA_TYPE_QUALIFIED_NAMES.contains(canonicalName) -> true

            ServiceDescription.SUPPORTED_ITERABLE_TYPES.any { it.canonicalName == canonicalName } -> {
              this as ParameterizedTypeName
              typeArguments.any { typeArgument ->
                val typeArgumentCanonicalName = typeArgument.classNameOrNull()?.canonicalName
                PART_DATA_TYPE_QUALIFIED_NAMES.contains(typeArgumentCanonicalName)
              }
            }

            else -> false
          }
        }

        val contentType = when (partAnnotation) {
          is HttpParameterAnnotation.Part.Single -> partAnnotation.contentType
          is HttpParameterAnnotation.Part.Iterable -> partAnnotation.contentType
          else -> error("Unexpected annotation type: $partAnnotation")
        }

        val formFieldName = when (partAnnotation) {
          is HttpParameterAnnotation.Part.Single -> partAnnotation.formFieldName
          is HttpParameterAnnotation.Part.Iterable -> partAnnotation.formFieldName
          else -> error("Unexpected annotation type: $partAnnotation")
        }

        if (isPartData) {
          if (contentType?.isNotEmpty() == true) {
            logger.error(
              if (partAnnotation is HttpParameterAnnotation.Part.Iterable) {
                "@PartIterable must not define a 'contentType' when the parts are of type PartData."
              } else {
                "@Part must not define a 'contentType' when the parameter type is PartData."
              },
              partAnnotation.annotation
            )
          }
          if (formFieldName?.isNotEmpty() == true) {
            logger.error(
              if (partAnnotation is HttpParameterAnnotation.Part.Iterable) {
                "@PartIterable must not define a 'formFieldName' when the parts are of type PartData."
              } else {
                "@Part must not define a 'formFieldName' when the parameter type is PartData."
              },
              partAnnotation.annotation
            )
          }
          return@mapNotNull when (partAnnotation) {
            is HttpParameterAnnotation.Part.Single -> {
              ServiceDescription.HttpContent.Multipart.PartContent.Parameter(
                parameterName = parameterName,
                metadata = null
              )
            }
            is HttpParameterAnnotation.Part.Iterable -> {
              ServiceDescription.HttpContent.Multipart.PartContent.Iterable(
                parameterName = parameterName,
                metadata = null
              )
            }
            else -> error("Unexpected annotation type: $partAnnotation")
          }
        }

        // Parameter type is NOT PartData

        if (contentType.isNullOrBlank()) {
          logger.error(
            if (partAnnotation is HttpParameterAnnotation.Part.Iterable) {
              "@PartIterable must provide a non-blank 'contentType' or use parts of type PartData."
            } else {
              "@Part must provide a non-blank 'contentType' or use PartData as the parameter type."
            },
            partAnnotation.annotation
          )
        }

        if (isMultipartForm && formFieldName.isNullOrBlank()) {
          logger.error(
            if (partAnnotation is HttpParameterAnnotation.Part.Iterable) {
              "When the @Multipart subtype is 'form-data' (the default), @PartIterable must provide a non-blank 'formFieldName' or use parts of type PartData."
            } else {
              "When the @Multipart subtype is 'form-data' (the default), @Part must provide a non-blank 'formFieldName' or use PartData as the parameter type."
            },
            partAnnotation.annotation
          )
        }
        val partMetadata = ServiceDescription.HttpContent.Multipart.PartMetadata(
          contentType = contentType ?: return@mapNotNull null,
          formFieldName = formFieldName
        )
        when (partAnnotation) {
          is HttpParameterAnnotation.Part.Single -> ServiceDescription.HttpContent.Multipart.PartContent.Parameter(
            parameterName = parameterName,
            metadata = partMetadata
          )
          is HttpParameterAnnotation.Part.Iterable -> ServiceDescription.HttpContent.Multipart.PartContent.Iterable(
            parameterName = parameterName,
            metadata = partMetadata
          )
          else -> error("Unexpected partAnnotation type: $partAnnotation")
        }
      }

    val bodyParameterName = bodyAnnotations.getOrNull(0)?.parameter?.name?.asString()
    val bodyContentType = bodyAnnotations.getOrNull(0)?.contentType

    fun content(): ServiceDescription.HttpContent? = when {
      bodyParameterName != null && bodyContentType != null -> ServiceDescription.HttpContent.Body(
        parameterName = bodyParameterName,
        contentType = bodyContentType
      )

      fieldAnnotations.isNotEmpty() -> ServiceDescription.HttpContent.FormUrlEncoded(
        fields = fieldAnnotations.map { fieldAnnotation ->
          when (fieldAnnotation) {
            is HttpParameterAnnotation.Field.Single -> {
              ServiceDescription.HttpContent.FormUrlEncoded.FieldContent.Parameter(
                parameterName = fieldAnnotation.parameter.name?.asString() ?: return null,
                fieldName = fieldAnnotation.name
              )
            }
            is HttpParameterAnnotation.Field.Map -> {
              ServiceDescription.HttpContent.FormUrlEncoded.FieldContent.Map(
                parameterName = fieldAnnotation.parameter.name?.asString() ?: return null,
              )
            }
          }
        }
      )

      parts.isNotEmpty() -> ServiceDescription.HttpContent.Multipart(
        subtype = multipartAnnotations.first().subtype,
        parts = parts
      )

      else -> null
    }

    if (
      httpMethodAnnotation == null ||
      url == null ||
      serviceFunctionParameters == null ||
      returnTypeName == null
    ) {
      return null
    }

    return ServiceDescription.Function.Http(
      name = simpleName.asString(),
      parameters = serviceFunctionParameters,
      method = httpMethodAnnotation.method,
      url = url,
      headers = headers,
      content = content(),
      returnType = returnTypeName
    )
  }

  private fun KSFunctionDeclaration.findHttpMethodAnnotations(): List<HttpMethodAnnotation> {
    return annotations.mapNotNull { annotation ->
      val annotationName = annotation.shortName.asString()
      val method = when (annotationName) {
        "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT" -> annotationName

        "HTTP" ->
          annotation.arguments
            .find { it.name?.asString() == "method" }
            ?.value as? String
            ?: return@mapNotNull null

        else -> return@mapNotNull null
      }
      val isBodyAllowed = annotationName != "DELETE" &&
        annotationName != "GET" &&
        annotationName != "HEAD" &&
        annotationName != "OPTIONS"

      val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null

      val urlTemplate: String? = annotation.arguments.find { it.name?.asString() == "url" }?.let { urlArgument ->
        urlArgument.value as? String ?: return@let null
      }
      HttpMethodAnnotation(
        isBodyAllowed = isBodyAllowed,
        method = method,
        name = annotationName,
        urlTemplate = urlTemplate,
        annotation = annotation,
        annotationType = resolvedAnnotationType
      )
    }
  }

  private fun KSFunctionDeclaration.findFormUrlEncodedAnnotations(): List<FormUrlEncodedAnnotation> {
    return annotations.mapNotNull { annotation ->
      when (annotation.shortName.asString()) {
        "FormUrlEncoded" -> annotation.resolveConnectorHttpAnnotation()?.let { type ->
          FormUrlEncodedAnnotation(annotation, type)
        }
        else -> null
      }
    }
  }

  private fun KSFunctionDeclaration.findMultipartAnnotations(): List<MultipartAnnotation> {
    return annotations.mapNotNull { annotation ->
      when (annotation.shortName.asString()) {
        "Multipart" -> annotation.resolveConnectorHttpAnnotation()?.let { type ->
          MultipartAnnotation(
            subtype = annotation.arguments.getOrNull(0)?.value as? String ?: "form-data",
            annotation = annotation,
            annotationType = type
          )
        }
        else -> null
      }
    }
  }

  // Logs an error at 'node' if 'name' is NOT a valid header name.
  private fun validateHeaderName(name: String, node: KSNode) {
    try {
      HttpHeaders.checkHeaderName(name)
    } catch (e: IllegalHeaderNameException) {
      logger.error(
        "Header name contains the illegal character '${name[e.position].toString().escape()}' " +
          "(code ${(name[e.position].toInt() and 0xff)})",
        node
      )
    }
  }

  // Logs an error at 'node' if 'value' is NOT a valid header value.
  private fun validateHeaderValue(value: String, node: KSNode) {
    try {
      HttpHeaders.checkHeaderValue(value)
    } catch (e: IllegalHeaderValueException) {
      logger.error(
        "Header value contains the illegal character '${value[e.position].toString().escape()}' " +
          "(code ${(value[e.position].toInt() and 0xff)})",
        node
      )
    }
  }

  private fun KSFunctionDeclaration.findStaticHeaders(): List<ServiceDescription.Function.Http.HeaderContent.Static> {
    return annotations
      .asSequence()
      .filter { it.shortName.asString() == "Headers" }
      .filter { it.resolveConnectorHttpAnnotation() != null }
      .flatMap { annotation ->
        @Suppress("UNCHECKED_CAST")
        val headerStrings = annotation.arguments[0].value as List<String>
        headerStrings.asSequence().mapNotNull { headerString ->
          val colonSplits = headerString.split(":", limit = 2)
          if (colonSplits.size != 2) {
            logger.error(
              "@Headers values must be formatted as '<name>: <value>'. Found: '$headerString'.",
              annotation
            )
            return@mapNotNull null
          }
          val name = colonSplits[0].trimEnd()
          validateHeaderName(name, annotation)
          if (name == HttpHeaders.ContentType) {
            logger.error(
              "${HttpHeaders.ContentType} header cannot be defined via @Headers, but only via @Body.",
              annotation
            )
            return@mapNotNull null
          }
          if (name == HttpHeaders.ContentLength) {
            logger.error(
              "${HttpHeaders.ContentLength} header cannot be defined via @Headers.",
              annotation
            )
            return@mapNotNull null
          }
          val value = colonSplits[1].trimStart()
          validateHeaderValue(value, annotation)
          ServiceDescription.Function.Http.HeaderContent.Static(name = name, value = value)
        }
      }
      .toList()
  }

  private fun KSValueParameter.findHttpParameterAnnotations(): List<HttpParameterAnnotation> {
    return annotations.mapNotNull { annotation ->
      when (annotation.shortName.asString()) {
        "Body" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val contentType = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          HttpParameterAnnotation.Body(
            contentType = contentType,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Header" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          validateHeaderName(name, annotation)
          HttpParameterAnnotation.Header.Single(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "HeaderMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Header.Map(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Path" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          HttpParameterAnnotation.Path(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Query" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          HttpParameterAnnotation.Query.Single(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "QueryMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Query.Map(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "URL" -> {
          val resolvedAnnotationType = annotation.resolveConnectorCoreAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Url(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Field" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          HttpParameterAnnotation.Field.Single(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "FieldMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Field.Map(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Part" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null

          val contentType = annotation.arguments
            .find { it.name?.asString() == "contentType" }
            ?.value as? String

          val formFieldName = annotation.arguments
            .find { it.name?.asString() == "formFieldName" }
            ?.value as? String

          HttpParameterAnnotation.Part.Single(
            contentType = contentType,
            formFieldName = formFieldName,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "PartIterable" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null

          val contentType = annotation.arguments
            .find { it.name?.asString() == "contentType" }
            ?.value as? String

          val formFieldName = annotation.arguments
            .find { it.name?.asString() == "formFieldName" }
            ?.value as? String

          HttpParameterAnnotation.Part.Iterable(
            contentType = contentType,
            formFieldName = formFieldName,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "PartMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val contentType = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
          HttpParameterAnnotation.Part.Map(
            contentType = contentType,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        else -> null
      }
    }
  }

  private enum class TypeValidationTarget(
    val expectsSerializableContent: Boolean,
    val expectsMany: ExpectsManyType? = null,
    val nonSerializableAllowedCanonicalNames: List<String> = emptyList(),
    val nullNotAllowedCanonicalNames: List<String> = emptyList()
  ) {
    HTTP_BODY_PARAMETER(
      expectsSerializableContent = true,
      nonSerializableAllowedCanonicalNames = listOf(HTTP_BODY_QUALIFIED_NAME)
    ),
    HTTP_FIELD_MAP_PARAMETER(
      expectsMany = ExpectsManyType.MAP,
      expectsSerializableContent = false,
      nonSerializableAllowedCanonicalNames = listOf(HTTP_BODY_QUALIFIED_NAME)
    ),
    HTTP_FUNCTION_RETURN(
      expectsSerializableContent = true,
      nonSerializableAllowedCanonicalNames = listOf(
        "kotlin.Unit",
        HTTP_BODY_QUALIFIED_NAME
      ) + NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES,
      nullNotAllowedCanonicalNames = NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES + "kotlin.Unit"
    ),
    HTTP_HEADER_MAP_PARAMETER(
      expectsMany = ExpectsManyType.MAP,
      expectsSerializableContent = false
    ),
    HTTP_PART_PARAMETER(
      expectsSerializableContent = true,
      nonSerializableAllowedCanonicalNames = listOf(
        HTTP_BODY_QUALIFIED_NAME
      ) + PART_DATA_TYPE_QUALIFIED_NAMES
    ),
    HTTP_PART_ITERABLE_PARAMETER(
      expectsMany = ExpectsManyType.ITERABLE,
      expectsSerializableContent = true,
      nonSerializableAllowedCanonicalNames = listOf(
        HTTP_BODY_QUALIFIED_NAME
      ) + PART_DATA_TYPE_QUALIFIED_NAMES
    ),
    HTTP_PART_MAP_PARAMETER(
      expectsMany = ExpectsManyType.MULTIMAP,
      expectsSerializableContent = true,
      nonSerializableAllowedCanonicalNames = listOf(HTTP_BODY_QUALIFIED_NAME)
    ),
    HTTP_QUERY_MAP_PARAMETER(
      expectsMany = ExpectsManyType.MAP,
      expectsSerializableContent = false
    );

    enum class ExpectsManyType { ITERABLE, MAP, MULTIMAP }
  }

  private fun KSType.typeNameWithValidation(
    target: TypeValidationTarget,
    targetNode: KSNode,
  ): TypeName? {
    fun KSDeclaration.isSerializable(): Boolean {
      val qualifiedName = qualifiedName?.asString() ?: return false
      return BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.contains(qualifiedName) ||
        annotations.any {
          it.shortName.asString() == "Serializable" &&
            it.annotationType.resolve().packageName == "kotlinx.serialization"
        }
    }

    val qualifiedOrSimpleName = (declaration.qualifiedName ?: declaration.simpleName).asString()

    when (target.expectsMany) {
      TypeValidationTarget.ExpectsManyType.MAP -> {
        if (
          qualifiedOrSimpleName != "kotlin.collections.Map" &&
          qualifiedOrSimpleName != "io.ktor.util.StringValues"
        ) {
          val errorSubject = when (target) {
            TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap parameter type"
            TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap parameter type"
            TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap parameter type"
            TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap parameter type"
            else -> error("Unexpected type: $target")
          }
          logger.error(
            "$errorSubject must be either 'kotlin.collections.Map' or 'io.ktor.util.StringValues'. " +
              "Found: '$qualifiedOrSimpleName'.",
            targetNode
          )
        }
      }

      TypeValidationTarget.ExpectsManyType.MULTIMAP -> {
        if (
          qualifiedOrSimpleName != "kotlin.collections.Map" &&
          qualifiedOrSimpleName != "io.ktor.util.StringValues" &&
          !ServiceDescription.SUPPORTED_ITERABLE_TYPES.any { it.canonicalName == qualifiedOrSimpleName }
        ) {
          val errorSubject = when (target) {
            TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap parameter type"
            TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap parameter type"
            TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap parameter type"
            TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap parameter type"
            else -> error("Unexpected type: $target")
          }
          logger.error(
            "$errorSubject must be either 'kotlin.collections.Map', 'io.ktor.util.StringValues'," +
              " or an Iterable of key-value pairs. Found: '$qualifiedOrSimpleName'.",
            targetNode
          )
        }
      }

      TypeValidationTarget.ExpectsManyType.ITERABLE -> {
        if (!ServiceDescription.SUPPORTED_ITERABLE_TYPES.any { it.canonicalName == qualifiedOrSimpleName }) {
          val errorSubject = when (target) {
            TypeValidationTarget.HTTP_PART_ITERABLE_PARAMETER -> "@PartIterable parameter type"
            else -> error("Unexpected type: $target")
          }
          logger.error(
            "$errorSubject must be one of: ${ServiceDescription.SUPPORTED_ITERABLE_TYPES.joinToString()}. " +
              "Found: '$qualifiedOrSimpleName'.",
            targetNode
          )
        }
      }

      null -> {
      }
    }

    if (
      target.expectsSerializableContent &&
      target.expectsMany == null &&
      !target.nonSerializableAllowedCanonicalNames.contains(qualifiedOrSimpleName) &&
      !declaration.isSerializable()
    ) {
      val errorSubject = when (target) {
        TypeValidationTarget.HTTP_BODY_PARAMETER -> "@Body type"
        TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap type"
        TypeValidationTarget.HTTP_FUNCTION_RETURN -> "return type"
        TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap type"
        TypeValidationTarget.HTTP_PART_PARAMETER -> "@Part type"
        TypeValidationTarget.HTTP_PART_ITERABLE_PARAMETER -> "@PartIterable type"
        TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap type"
        TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap type"
      }
      logger.error(
        "Invalid $errorSubject: '$qualifiedOrSimpleName'. Expected either a @Serializable type, " +
          "${target.nonSerializableAllowedCanonicalNames.joinToString()}, " +
          "or a built-in serializable type (${BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.joinToString()})",
        targetNode
      )
    }

    if (
      nullability == Nullability.NULLABLE &&
      target.nullNotAllowedCanonicalNames.contains(qualifiedOrSimpleName)
    ) {
      val errorSubject = when (target) {
        TypeValidationTarget.HTTP_BODY_PARAMETER -> "@Body type"
        TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap type"
        TypeValidationTarget.HTTP_FUNCTION_RETURN -> "return type"
        TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap type"
        TypeValidationTarget.HTTP_PART_PARAMETER -> "@Part type"
        TypeValidationTarget.HTTP_PART_ITERABLE_PARAMETER -> "@PartIterable type"
        TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap type"
        TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap type"
      }
      logger.error(
        "Nullable '$qualifiedOrSimpleName' is not allowed as the $errorSubject. Must be non-null.",
        targetNode
      )
    }

    return typeName(
      onTypeArgumentResolvedListener = object : OnTypeArgumentResolvedListener {
        override fun onTypeArgumentResolved(
          argument: KSTypeArgument,
          argumentTypeName: TypeName?,
          argumentTypeDeclaration: KSDeclaration?,
          argumentIndex: Int,
          pathToArgument: List<KSType>,
        ) {
          val typeArgumentNameForDiagnostics by lazy {
            argumentTypeDeclaration?.qualifiedName?.asString()
              ?: argumentTypeDeclaration?.simpleName?.asString()
              ?: argumentTypeName
          }

          val argumentDepth = pathToArgument.lastIndex

          val isMapKeyType = when (target.expectsMany) {
            // Map<K, V> -> K
            TypeValidationTarget.ExpectsManyType.MAP -> argumentDepth == 0 && argumentIndex == 0

            TypeValidationTarget.ExpectsManyType.MULTIMAP -> when {
              // Map<K, V> -> K
              argumentDepth == 0 && argumentIndex == 0 &&
                pathToArgument.last().declaration.qualifiedName?.asString() == "kotlin.collections.Map" -> true

              // List<Pair<K, V>> -> K
              argumentDepth == 1 && argumentIndex == 0 &&
                pathToArgument.last().declaration.qualifiedName?.asString() == "kotlin.Pair" -> true

              else -> false
            }

            else -> false
          }
          if (isMapKeyType) {
            val errorSubject by lazy {
              when (target) {
                TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap"
                TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap"
                TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap"
                TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap"
                else -> error("Unexpected target: $target")
              }
            }

            if (argumentTypeDeclaration?.qualifiedName?.asString() != "kotlin.String") {
              logger.error(
                "$errorSubject keys must be of type '$STRING'. Found: '$typeArgumentNameForDiagnostics'.",
                targetNode
              )
            }

            if (argumentTypeName?.isNullable == true) {
              logger.error(
                "$errorSubject keys must be non-nullable.",
                targetNode
              )
            }

            return
          }

          // Validate multimap type arguments other than Map keys
          if (target.expectsMany == TypeValidationTarget.ExpectsManyType.MULTIMAP) {
            // List<T> -> T
            val isListItemType = argumentDepth == 0 && argumentIndex == 0 &&
              ServiceDescription.SUPPORTED_ITERABLE_TYPES.any {
                it.canonicalName == pathToArgument.last().declaration.qualifiedName?.asString()
              }
            if (isListItemType) {
              val errorSubject by lazy {
                when (target) {
                  TypeValidationTarget.HTTP_FIELD_MAP_PARAMETER -> "@FieldMap"
                  TypeValidationTarget.HTTP_HEADER_MAP_PARAMETER -> "@HeaderMap"
                  TypeValidationTarget.HTTP_PART_MAP_PARAMETER -> "@PartMap"
                  TypeValidationTarget.HTTP_QUERY_MAP_PARAMETER -> "@QueryMap"
                  else -> error("Unexpected target: $target")
                }
              }

              if (argumentTypeDeclaration?.qualifiedName?.asString() != "kotlin.Pair") {
                logger.error(
                  "Invalid $errorSubject Iterable type argument. " +
                    "Expected: 'kotlin.Pair'. Found: '$typeArgumentNameForDiagnostics'.",
                  targetNode
                )
              }

              if (argumentTypeName?.isNullable == true) {
                logger.error(
                  "The type argument of a $errorSubject Iterable must be non-nullable.",
                  targetNode
                )
              }

              return
            }
          }

          if (!target.expectsSerializableContent || argumentTypeDeclaration?.isSerializable() == true) {
            return
          }

          val isParentConnectorResultType = when (pathToArgument.last().declaration.qualifiedName?.asString()) {
            "dev.aoddon.connector.http.HttpResult" -> true
            "dev.aoddon.connector.http.HttpResponse" -> true
            "dev.aoddon.connector.http.HttpResponse.Success" -> true
            else -> false
          }

          val qualifiedName = argumentTypeDeclaration?.qualifiedName?.asString()
          if (isParentConnectorResultType && qualifiedName == "kotlin.Unit") {
            if (argumentTypeName?.isNullable == true) {
              logger.error(
                "Nullable 'kotlin.Unit' type argument is not allowed. Must be non-null.",
                argument
              )
            }
            return
          }

          if (isParentConnectorResultType && argumentTypeName == STAR) {
            return
          }

          if (isParentConnectorResultType && qualifiedName == HTTP_BODY_QUALIFIED_NAME) {
            return
          }

          val checkNonSerializableAllowed = when (target.expectsMany) {
            null -> false
            // Iterable<T> -> T
            TypeValidationTarget.ExpectsManyType.ITERABLE -> argumentDepth == 0 && argumentIndex == 0

            // Map<K, V> -> V
            TypeValidationTarget.ExpectsManyType.MAP -> argumentDepth == 0 && argumentIndex == 1

            TypeValidationTarget.ExpectsManyType.MULTIMAP -> {
              if (pathToArgument.first().declaration.qualifiedName?.asString() == "kotlin.collections.Map") {
                // Map<K, V> -> V
                argumentDepth == 0 && argumentIndex == 1
              } else {
                // Iterable<Pair<K, V>> -> V
                argumentDepth == 1 && argumentIndex == 1
              }
            }
          }

          if (checkNonSerializableAllowed && target.nonSerializableAllowedCanonicalNames.contains(qualifiedName)) {
            return
          }

          val errorMessageBuilder = StringBuilder(
            "Invalid type argument: '$typeArgumentNameForDiagnostics'. Expected either a @Serializable type",
          )
          if (isParentConnectorResultType) {
            errorMessageBuilder.append(", kotlin.Unit, dev.aoddon.connector.http.HttpBody,")
          }
          if (checkNonSerializableAllowed && target.nonSerializableAllowedCanonicalNames.isNotEmpty()) {
            errorMessageBuilder.append(
              target.nonSerializableAllowedCanonicalNames.run {
                if (isParentConnectorResultType) {
                  minus(listOf("kotlin.Unit", HTTP_BODY_QUALIFIED_NAME))
                } else {
                  this
                }
              }.joinToString(
                prefix = if (isParentConnectorResultType) " " else ", ",
                postfix = ","
              )
            )
          }
          errorMessageBuilder.append(
            " or a built-in serializable type (${BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.joinToString()})"
          )
          logger.error(errorMessageBuilder.toString(), argument)
        }
      }
    )
  }

  private fun String.urlType(node: KSNode): UrlType = when {
    startsWith("//") -> UrlType.PROTOCOL_RELATIVE
    startsWith("/") -> UrlType.ABSOLUTE
    isFullUrl() -> {
      if (!startsWith("http:") && !startsWith("https:")) {
        logger.error(
          "URL protocol must be HTTP or HTTPS. Found: '${substringBefore(':')}'.",
          node
        )
      }
      UrlType.FULL
    }
    else -> UrlType.RELATIVE
  }

  private fun String.isFullUrl(): Boolean {
    for (index in indices) {
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
  }

  private fun TypeName.isValidReturnTypeForHttpHead(): Boolean {
    if (this is ClassName) {
      return canonicalName == "kotlin.Unit"
    }
    if (this is ParameterizedTypeName) {
      return when (rawType.canonicalName) {
        "dev.aoddon.connector.http.HttpResult", "dev.aoddon.connector.http.HttpResponse", "dev.aoddon.connector.http.HttpResponse.Success" -> {
          val successBodyType = typeArguments.first()
          return successBodyType == STAR || successBodyType.classNameOrNull()?.canonicalName == "kotlin.Unit"
        }

        else -> false
      }
    }
    // Avoid a potentially confusing error message if the return type is '*' (which is already a compilation error).
    return this != STAR
  }
}

private data class HttpMethodAnnotation(
  val isBodyAllowed: Boolean,
  val method: String,
  val name: String,
  val urlTemplate: String?,
  val annotation: KSAnnotation,
  val annotationType: KSType
)

private data class FormUrlEncodedAnnotation(
  val annotation: KSAnnotation,
  val annotationType: KSType
)

private data class MultipartAnnotation(
  val subtype: String,
  val annotation: KSAnnotation,
  val annotationType: KSType
)

private sealed class HttpParameterAnnotation {
  abstract val parameter: KSValueParameter
  abstract val annotation: KSAnnotation
  abstract val annotationType: KSType

  data class Body(
    val contentType: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  sealed class Header : HttpParameterAnnotation() {
    data class Single(
      val name: String,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Header()

    data class Map(
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Header()
  }

  data class Path(
    val name: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  sealed class Query : HttpParameterAnnotation() {
    data class Single(
      val name: String,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Query()

    data class Map(
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Query()
  }

  data class Url(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  sealed class Field : HttpParameterAnnotation() {
    data class Single(
      val name: String,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Field()

    data class Map(
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Field()
  }

  sealed class Part : HttpParameterAnnotation() {
    data class Single(
      val contentType: String?,
      val formFieldName: String?,
      override val annotationType: KSType,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation
    ) : HttpParameterAnnotation.Part()

    data class Iterable(
      val contentType: String?,
      val formFieldName: String?,
      override val annotationType: KSType,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation
    ) : HttpParameterAnnotation.Part()

    data class Map(
      val contentType: String,
      override val parameter: KSValueParameter,
      override val annotation: KSAnnotation,
      override val annotationType: KSType
    ) : HttpParameterAnnotation.Part()
  }
}

private const val URL_TEMPLATE_PARAMETER_NAME_PATTERN = "[a-zA-Z][a-zA-Z0-9_-]*"
private val URL_TEMPLATE_PARAMETER_NAME_REGEX = Regex(URL_TEMPLATE_PARAMETER_NAME_PATTERN)
private val URL_TEMPLATE_PARAMETER_REGEX = Regex("\\{($URL_TEMPLATE_PARAMETER_NAME_PATTERN)}")

private fun KSAnnotation.resolveConnectorCoreAnnotation(): KSType? =
  resolveAnnotationFromPackage(CORE_ANNOTATIONS_PACKAGE_NAME)

private fun KSAnnotation.resolveConnectorHttpAnnotation(): KSType? =
  resolveAnnotationFromPackage(HTTP_ANNOTATIONS_PACKAGE_NAME)

private fun KSAnnotation.resolveAnnotationFromPackage(packageName: String): KSType? =
  annotationType.resolve().takeIf { it.packageName == packageName }

private const val CORE_ANNOTATIONS_PACKAGE_NAME = "dev.aoddon.connector"

private const val HTTP_ANNOTATIONS_PACKAGE_NAME = "dev.aoddon.connector.http"

private val BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES = listOf(
  // primitives
  "kotlin.Boolean",
  "kotlin.Byte",
  "kotlin.Char",
  "kotlin.Double",
  "kotlin.Float",
  "kotlin.Int",
  "kotlin.Long",
  "kotlin.Short",
  "kotlin.String",

  // tuples
  "kotlin.Pair",
  "kotlin.Triple",
  "kotlin.collections.Map.Entry",

  // arrays
  "kotlin.Array",
  "kotlin.BooleanArray",
  "kotlin.ByteArray",
  "kotlin.CharArray",
  "kotlin.DoubleArray",
  "kotlin.FloatArray",
  "kotlin.IntArray",
  "kotlin.LongArray",
  "kotlin.ShortArray",
  "kotlin.StringArray",

  // collections
  "kotlin.collections.List",
  "kotlin.collections.Map",
  "kotlin.collections.Set",
)

private val NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES = listOf(
  "dev.aoddon.connector.http.HttpResult",
  "dev.aoddon.connector.http.HttpResponse",
  "dev.aoddon.connector.http.HttpResponse.Success",
)

private val PART_DATA_TYPE_QUALIFIED_NAMES = listOf(
  PartData::class.qualifiedName!!,
  PartData.BinaryItem::class.qualifiedName!!,
  PartData.FileItem::class.qualifiedName!!,
  PartData.FormItem::class.qualifiedName!!,
)

private const val HTTP_BODY_QUALIFIED_NAME = "dev.aoddon.connector.http.HttpBody"

private fun String.escape(): String {
  val stringBuilder = StringBuilder()
  forEach { char ->
    stringBuilder.append(
      when (char) {
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\'' -> "\\\'"
        '\"' -> "\\\""
        '\\' -> "\\\\"
        '$' -> "\\$"
        else -> char
      }
    )
  }
  return stringBuilder.toString()
}
