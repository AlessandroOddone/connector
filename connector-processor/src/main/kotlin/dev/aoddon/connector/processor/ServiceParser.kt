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
import dev.aoddon.connector.codegen.StringValue
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

    val httpFormEncodingAnnotations = findHttpFormEncodingAnnotations()
    if (httpFormEncodingAnnotations.size > 1) {
      logger.error(
        "Multiple HTTP form encoding annotations are not allowed. " +
          "Found: ${httpFormEncodingAnnotations.joinToString { it.annotation.shortName.asString() }}",
        this
      )
    }
    val isFormUrlEncoded = httpFormEncodingAnnotations.any { it is HttpFormEncodingAnnotation.FormUrlEncoded }
    val isMultipart = httpFormEncodingAnnotations.any { it is HttpFormEncodingAnnotation.Multipart }
    if (isFormUrlEncoded) {
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
    if (isMultipart) {
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
            "All parameters in a @Service function must have an appropriate connector annotation.",
            parameter
          )
        }
        if (parameterAnnotations.size > 1) {
          logger.error(
            "Multiple connector annotations are not allowed on the same parameter.",
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
      if (isFormUrlEncoded) {
        logger.error(
          "@Body is not allowed in @FormUrlEncoded requests.",
          bodyAnnotation.annotation
        )
      }
      if (isMultipart) {
        logger.error(
          "@Body is not allowed in @Multipart requests.",
          bodyAnnotation.annotation
        )
      }
    }

    val fieldAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Field>()
    val fieldMapAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.FieldMap>()
    if (!isFormUrlEncoded) {
      fieldAnnotations.forEach { fieldAnnotation ->
        logger.error(
          "@Field can only be used in @FormUrlEncoded requests.",
          fieldAnnotation.annotation
        )
      }
      fieldMapAnnotations.forEach { fieldMapAnnotation ->
        logger.error(
          "@FieldMap can only be used in @FormUrlEncoded requests.",
          fieldMapAnnotation.annotation
        )
      }
    }
    if (isFormUrlEncoded && fieldAnnotations.isEmpty() && fieldMapAnnotations.isEmpty()) {
      logger.error(
        "@FormUrlEncoded functions must have at least one @Field or @FieldMap parameter.",
        this
      )
    }

    val partAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Part>()
    val partMapAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.PartMap>()
    if (!isMultipart) {
      partAnnotations.forEach { partAnnotation ->
        logger.error(
          "@Part can only be used in @Multipart requests.",
          partAnnotation.annotation
        )
      }
      partMapAnnotations.forEach { partMapAnnotation ->
        logger.error(
          "@PartMap can only be used in @Multipart requests.",
          partMapAnnotation.annotation
        )
      }
    }
    if (isMultipart && partAnnotations.isEmpty() && partMapAnnotations.isEmpty()) {
      logger.error(
        "@Multipart functions must have at least one @Part or @PartMap parameter.",
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

    val dynamicQueryParameters = allParameterAnnotations
      .asSequence()
      .filterIsInstance<HttpParameterAnnotation.Query>()
      .mapNotNull { queryAnnotation ->
        queryAnnotation.parameter.name?.asString()?.let { parameterName ->
          StringValue.Dynamic(name = queryAnnotation.name, parameterName = parameterName)
        }
      }
      .toList()

    val queryMapParameterNames = allParameterAnnotations
      .asSequence()
      .filterIsInstance<HttpParameterAnnotation.QueryMap>()
      .mapNotNull { it.parameter.name?.asString() }
      .toList()

    val httpMethodAnnotation = httpMethodAnnotations.firstOrNull()
    val url = httpMethodAnnotation?.let {
      if (httpMethodAnnotation.urlTemplate != null) {
        val parameterNameReplacementMappings = pathAnnotationsByName
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
          replaceBlockToParameterMap = parameterNameReplacementMappings,
          dynamicQueryParameters = dynamicQueryParameters,
          queryMapParameterNames = queryMapParameterNames
        )
      } else {
        urlAnnotations.firstOrNull()?.parameter?.name?.asString()?.let { parameterName ->
          ServiceDescription.Url.Dynamic(
            parameterName = parameterName,
            dynamicQueryParameters = dynamicQueryParameters,
            queryMapParameterNames = queryMapParameterNames
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

        val typeName = if (httpParameterAnnotation is HttpParameterAnnotation.Body) {
          parameterType.typeNameWithValidation(
            node = httpParameterAnnotation.annotation,
            validation = SerializableTypeValidation.HTTP_BODY_ANNOTATED
          )
        } else {
          parameterType.typeName()
        }

        if (
          httpParameterAnnotation is HttpParameterAnnotation.FieldMap ||
          httpParameterAnnotation is HttpParameterAnnotation.HeaderMap ||
          httpParameterAnnotation is HttpParameterAnnotation.PartMap ||
          httpParameterAnnotation is HttpParameterAnnotation.QueryMap
        ) {
          val annotationName = httpParameterAnnotation.annotation.shortName.asString()
          val qualifiedName = parameterType.declaration.qualifiedName?.asString()
          if (qualifiedName == "kotlin.collections.Map") {
            (typeName as? ParameterizedTypeName)?.run {
              val keyTypeClassName = typeArguments.getOrNull(0)?.classNameOrNull()
              if (keyTypeClassName != STRING) {
                logger.error(
                  "@$annotationName keys must be of type '$STRING'.",
                  httpParameterAnnotation.annotation
                )
              }
            }
          } else if (qualifiedName != "io.ktor.util.StringValues") {
            logger.error(
              "@$annotationName parameter type must be either 'kotlin.collections.Map' or 'io.ktor.util.StringValues'.",
              httpParameterAnnotation.annotation
            )
          }
        }

        if (parameterName != null && typeName != null) {
          parameterName to typeName
        } else {
          null
        }
      }
      .toMap()
      .takeIf { it.size == allParameterAnnotations.size }

    val headers = mutableListOf<StringValue>()
    // dynamic headers
    headers.addAll(
      allParameterAnnotations
        .asSequence()
        .filterIsInstance<HttpParameterAnnotation.Header>()
        .mapNotNull
        { headerAnnotation ->
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
          val parameterName = headerAnnotation.parameter.name?.asString()
          parameterName?.let { StringValue.Dynamic(name = headerName, parameterName = parameterName) }
        }
    )
    headers.addAll(findStaticHeaders())

    val headerMapParameterNames = allParameterAnnotations
      .asSequence()
      .filterIsInstance<HttpParameterAnnotation.HeaderMap>()
      .mapNotNull { it.parameter.name?.asString() }
      .toList()

    val bodyParameterName = bodyAnnotations.getOrNull(0)?.parameter?.name?.asString()
    val bodyContentType = bodyAnnotations.getOrNull(0)?.contentType

    fun content(): ServiceDescription.HttpContent? = when {
      bodyParameterName != null && bodyContentType != null -> ServiceDescription.HttpContent.Body(
        parameterName = bodyParameterName,
        contentType = bodyContentType
      )

      fieldAnnotations.isNotEmpty() || fieldMapAnnotations.isNotEmpty() -> {
        ServiceDescription.HttpContent.FormUrlEncoded(
          parameterToFieldNameMap = fieldAnnotations.associate { fieldAnnotation ->
            (fieldAnnotation.parameter.name?.asString() ?: return null) to fieldAnnotation.name
          },
          fieldMapParameterNames = fieldMapAnnotations.map { fieldAnnotation ->
            fieldAnnotation.parameter.name?.asString() ?: return null
          }
        )
      }

      else -> null
    }

    val returnType = returnType?.resolve()
    val returnTypeName = returnType?.typeNameWithValidation(this, SerializableTypeValidation.HTTP_FUNCTION_RETURN)

    // Validate return type for @HEAD
    if (
      httpMethodAnnotations.any { it.name == "HEAD" } &&
      returnTypeName?.isValidReturnTypeForHttpHead() != true
    ) {
      logger.error("@HEAD can only be used with 'kotlin.Unit' or '*' as the success body type.", this)
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
      headerMapParameterNames = headerMapParameterNames,
      content = content(),
      returnType = returnTypeName
    )
  }

  private fun KSFunctionDeclaration.findHttpMethodAnnotations(): List<HttpMethodAnnotation> {
    return annotations.mapNotNull { annotation ->
      val annotationName = annotation.shortName.asString()
      val method = when (annotationName) {
        "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT" -> annotationName
        "HTTP" -> annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
        else -> return@mapNotNull null
      }
      val isBodyAllowed = annotationName != "DELETE" &&
        annotationName != "GET" &&
        annotationName != "HEAD" &&
        annotationName != "OPTIONS"

      val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null

      val urlTemplateArgumentIndex = if (annotationName == "HTTP") 1 else 0
      val urlTemplate: String? = annotation.arguments.getOrNull(urlTemplateArgumentIndex)?.let { urlArgument ->
        urlArgument.value as? String ?: return@let null
      }
      HttpMethodAnnotation(
        annotation = annotation,
        annotationType = resolvedAnnotationType,
        isBodyAllowed = isBodyAllowed,
        method = method,
        name = annotationName,
        urlTemplate = urlTemplate
      )
    }
  }

  private fun KSFunctionDeclaration.findHttpFormEncodingAnnotations(): List<HttpFormEncodingAnnotation> {
    return annotations.mapNotNull { annotation ->
      when (annotation.shortName.asString()) {
        "FormUrlEncoded" -> annotation.resolveConnectorHttpAnnotation()?.let { type ->
          HttpFormEncodingAnnotation.FormUrlEncoded(annotation, type)
        }
        "Multipart" -> annotation.resolveConnectorHttpAnnotation()?.let { type ->
          HttpFormEncodingAnnotation.Multipart(annotation, type)
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

  private fun KSFunctionDeclaration.findStaticHeaders(): List<StringValue.Static> {
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
          StringValue.Static(name = name, value = value)
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
          HttpParameterAnnotation.Header(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "HeaderMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.HeaderMap(
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
          HttpParameterAnnotation.Query(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "QueryMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.QueryMap(
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
          HttpParameterAnnotation.Field(
            name = name,
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "FieldMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.FieldMap(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Part" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Part(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "PartMap" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.PartMap(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        else -> null
      }
    }
  }

  private enum class SerializableTypeValidation(
    val nonSerializableAllowedCanonicalNames: List<String>,
    val nullNotAllowedCanonicalNames: List<String>
  ) {
    HTTP_BODY_ANNOTATED(
      nonSerializableAllowedCanonicalNames = listOf("dev.aoddon.connector.http.HttpBody"),
      nullNotAllowedCanonicalNames = emptyList()
    ),
    HTTP_FUNCTION_RETURN(
      nonSerializableAllowedCanonicalNames = listOf(
        "kotlin.Unit",
        "dev.aoddon.connector.http.HttpBody"
      ) + NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES,
      nullNotAllowedCanonicalNames = NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES + "kotlin.Unit"
    )
  }

  private fun KSType.typeNameWithValidation(
    node: KSNode,
    validation: SerializableTypeValidation
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

    if (
      !validation.nonSerializableAllowedCanonicalNames.contains(qualifiedOrSimpleName) &&
      !declaration.isSerializable()
    ) {
      val errorSubject = when (validation) {
        SerializableTypeValidation.HTTP_BODY_ANNOTATED -> "@Body type"
        SerializableTypeValidation.HTTP_FUNCTION_RETURN -> "return type"
      }

      logger.error(
        "Invalid $errorSubject: '$qualifiedOrSimpleName'. Expected either a @Serializable type, " +
          "${validation.nonSerializableAllowedCanonicalNames.joinToString()}, " +
          "or a built-in serializable type (${BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.joinToString()})",
        node
      )
    }

    if (
      nullability == Nullability.NULLABLE &&
      validation.nullNotAllowedCanonicalNames.contains(qualifiedOrSimpleName)
    ) {
      val errorSubject = when (validation) {
        SerializableTypeValidation.HTTP_BODY_ANNOTATED -> "@Body type"
        SerializableTypeValidation.HTTP_FUNCTION_RETURN -> "return type"
      }
      logger.error(
        "Nullable '$qualifiedOrSimpleName' is not allowed as the $errorSubject. Must be non-null.",
        node
      )
    }

    return typeName(
      onTypeArgumentResolvedListener = object : OnTypeArgumentResolvedListener {
        override fun onTypeArgumentResolved(
          argument: KSTypeArgument,
          argumentTypeName: TypeName?,
          argumentTypeDeclaration: KSDeclaration?,
          argumentOwner: KSType
        ) {
          if (argumentTypeDeclaration?.isSerializable() == true) {
            return
          }
          val isUnitAllowed = when (argumentOwner.declaration.qualifiedName?.asString()) {
            "dev.aoddon.connector.http.HttpResult" -> true
            "dev.aoddon.connector.http.HttpResponse" -> true
            "dev.aoddon.connector.http.HttpResponse.Success" -> true
            else -> false
          }
          @Suppress("UnnecessaryVariable") val isHttpBodyAllowed = isUnitAllowed

          val qualifiedName = argumentTypeDeclaration?.qualifiedName?.asString()
          if (isUnitAllowed && qualifiedName == "kotlin.Unit") {
            if (argumentTypeName?.isNullable == true) {
              logger.error(
                "Nullable 'kotlin.Unit' type argument is not allowed. Must be non-null.",
                argument
              )
            }
            return
          }

          if (isUnitAllowed && argumentTypeName == STAR) {
            return
          }

          if (isHttpBodyAllowed && qualifiedName == "dev.aoddon.connector.http.HttpBody") {
            return
          }

          val typeArgumentName = argumentTypeDeclaration?.qualifiedName?.asString()
            ?: argumentTypeDeclaration?.simpleName?.asString()
            ?: argumentTypeName

          val errorMessageBuilder = StringBuilder(
            "Invalid type argument: '$typeArgumentName'. Expected either a @Serializable type",
          )
          if (isUnitAllowed) {
            errorMessageBuilder.append(", kotlin.Unit")
          }
          if (isHttpBodyAllowed) {
            errorMessageBuilder.append(", dev.aoddon.connector.http.HttpBody")
          }
          if (isUnitAllowed || isHttpBodyAllowed) {
            errorMessageBuilder.append(",")
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
  val annotation: KSAnnotation,
  val annotationType: KSType,
  val isBodyAllowed: Boolean,
  val method: String,
  val name: String,
  val urlTemplate: String?
)

private sealed class HttpFormEncodingAnnotation {
  abstract val annotation: KSAnnotation
  abstract val annotationType: KSType

  data class FormUrlEncoded(
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpFormEncodingAnnotation()

  data class Multipart(
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpFormEncodingAnnotation()
}

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

  data class Header(
    val name: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class HeaderMap(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class Path(
    val name: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class Query(
    val name: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class QueryMap(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class Url(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class Field(
    val name: String,
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class FieldMap(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class Part(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()

  data class PartMap(
    override val parameter: KSValueParameter,
    override val annotation: KSAnnotation,
    override val annotationType: KSType
  ) : HttpParameterAnnotation()
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
