package connector.processor

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
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import connector.codegen.ServiceDescription
import connector.codegen.StringValue
import connector.codegen.UrlType
import connector.processor.util.OnTypeArgumentResolvedListener
import connector.processor.util.className
import connector.processor.util.isInterface
import connector.processor.util.isTopLevel
import connector.processor.util.packageName
import connector.processor.util.typeName
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
      logger.error("Multiple HTTP method annotations are not allowed.", this)
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

    val allParameterAnnotations = parameters
      .flatMap { parameter ->
        val parameterAnnotations = parameter.findHttpParameterAnnotations()
        if (parameterAnnotations.isEmpty()) {
          logger.error("Function parameter must have a valid connector annotation.", parameter)
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
          parameterNameReplacementMappings = parameterNameReplacementMappings,
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

        val typeName = if (httpParameterAnnotation is HttpParameterAnnotation.Body) {
          parameterType.typeNameWithValidation(
            node = httpParameterAnnotation.annotation,
            validationType = TypeNameValidationType.HTTP_BODY_ANNOTATED
          )
        } else {
          parameterType.typeName()
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
              "${HttpHeaders.ContentType} header cannot be defined via @Header, but only via @Body.",
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

    val bodyParameterName = bodyAnnotations.getOrNull(0)?.parameter?.name?.asString()
    val bodyContentType = bodyAnnotations.getOrNull(0)?.contentType
    val requestBody = if (bodyParameterName != null && bodyContentType != null) {
      ServiceDescription.HttpRequestBody(parameterName = bodyParameterName, contentType = bodyContentType)
    } else {
      null
    }

    val returnType = returnType?.resolve()
    val returnTypeName = returnType?.typeNameWithValidation(this, TypeNameValidationType.HTTP_FUNCTION_RETURN)

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
      requestBody = requestBody,
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
      val isBodyAllowed = method != "DELETE" && method != "GET" && method != "HEAD" && method != "OPTIONS"
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
        "JsonBody" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Body(
            contentType = ContentType.Application.Json.toString(),
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        "Header" -> {
          val resolvedAnnotationType = annotation.resolveConnectorHttpAnnotation() ?: return@mapNotNull null
          val name = annotation.arguments.getOrNull(0)?.value ?: return@mapNotNull null
          HttpParameterAnnotation.Header(
            name = name as String,
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
        "URL" -> {
          val resolvedAnnotationType = annotation.resolveConnectorCoreAnnotation() ?: return@mapNotNull null
          HttpParameterAnnotation.Url(
            parameter = this,
            annotation = annotation,
            annotationType = resolvedAnnotationType
          )
        }
        else -> null
      }
    }
  }

  private enum class TypeNameValidationType(
    val nonSerializableAllowedCanonicalNames: List<String>,
    val nullNotAllowedCanonicalNames: List<String>
  ) {
    HTTP_BODY_ANNOTATED(
      nonSerializableAllowedCanonicalNames = listOf("connector.http.HttpBody"),
      nullNotAllowedCanonicalNames = emptyList()
    ),
    HTTP_FUNCTION_RETURN(
      nonSerializableAllowedCanonicalNames = listOf(
        "kotlin.Unit",
        "connector.http.HttpBody"
      ) + NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES,
      nullNotAllowedCanonicalNames = NON_ERROR_HTTP_RESULT_TYPES_QUALIFIED_NAMES + "kotlin.Unit"
    )
  }

  private fun KSType.typeNameWithValidation(
    node: KSNode,
    validationType: TypeNameValidationType
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
      !validationType.nonSerializableAllowedCanonicalNames.contains(qualifiedOrSimpleName) &&
      !declaration.isSerializable()
    ) {
      val errorSubject = when (validationType) {
        TypeNameValidationType.HTTP_BODY_ANNOTATED -> "@Body type"
        TypeNameValidationType.HTTP_FUNCTION_RETURN -> "return type"
      }

      logger.error(
        "Invalid $errorSubject: '$qualifiedOrSimpleName'. Expected either a @Serializable type, " +
          "${validationType.nonSerializableAllowedCanonicalNames.joinToString()}, " +
          "or a built-in serializable type (${BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.joinToString()})",
        node
      )
    }

    if (
      nullability == Nullability.NULLABLE &&
      validationType.nullNotAllowedCanonicalNames.contains(qualifiedOrSimpleName)
    ) {
      val errorSubject = when (validationType) {
        TypeNameValidationType.HTTP_BODY_ANNOTATED -> "@Body type"
        TypeNameValidationType.HTTP_FUNCTION_RETURN -> "return type"
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
            "connector.http.HttpResult" -> true
            "connector.http.HttpResponse" -> true
            "connector.http.HttpResponse.Success" -> true
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

          if (isHttpBodyAllowed && qualifiedName == "connector.http.HttpBody") {
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
            errorMessageBuilder.append(", connector.http.HttpBody")
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
}

private data class HttpMethodAnnotation(
  val annotation: KSAnnotation,
  val annotationType: KSType,
  val isBodyAllowed: Boolean,
  val method: String,
  val name: String,
  val urlTemplate: String?
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

  data class Header(
    val name: String,
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

  data class Url(
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

private const val CORE_ANNOTATIONS_PACKAGE_NAME = "connector"

private const val HTTP_ANNOTATIONS_PACKAGE_NAME = "connector.http"

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
  "connector.http.HttpResult",
  "connector.http.HttpResponse",
  "connector.http.HttpResponse.Success",
)
