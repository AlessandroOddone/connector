package connector.processor

import com.squareup.kotlinpoet.TypeName
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.symbol.KSAnnotation
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSFunctionDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSNode
import org.jetbrains.kotlin.ksp.symbol.KSPropertyDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSVariableParameter
import org.jetbrains.kotlin.ksp.symbol.Modifier
import connector.codegen.Dynamic
import connector.codegen.HttpMethod
import connector.codegen.RelativeUrl
import connector.codegen.Service
import connector.codegen.Static
import connector.codegen.StringValue
import connector.processor.util.className
import connector.processor.util.isInterface
import connector.processor.util.isTopLevel
import connector.processor.util.qualifier
import connector.processor.util.typeName

class ServiceParser(private val logger: KSPLogger) {
    fun parse(classDeclaration: KSClassDeclaration): Service = with(classDeclaration) {
        if (!isInterface || !isTopLevel) {
            logger.error("@API target must be a top-level interface.", classDeclaration)
        }

        if (superTypes.isNotEmpty()) {
            logger.error("Supertypes are not allowed in @API interfaces.", classDeclaration)
        }

        if (typeParameters.isNotEmpty()) {
            logger.error("Type parameters are not allowed in @API interfaces.", classDeclaration)
        }

        val serviceName = simpleName.getShortName()
        val serviceFunctions = declarations
            .mapNotNull { declaration ->
                if (declaration is KSPropertyDeclaration) {
                    logger.error("Properties are not allowed in @API interfaces.", declaration)
                }
                (declaration as? KSFunctionDeclaration)?.parseServiceFunction()
            }

        return Service(
            name = serviceName,
            functions = serviceFunctions,
            existingParentInterface = className()
        )
    }

    private fun KSFunctionDeclaration.parseServiceFunction(): Service.Function? {
        val httpMethodAnnotations = findHttpMethodAnnotations()
        if (httpMethodAnnotations.isEmpty()) {
            logger.error("All functions in @API interfaces must be annotated with an HTTP method.", this)
        }
        if (httpMethodAnnotations.size > 1) {
            logger.error("Multiple HTTP method annotations are not allowed on a function.", this)
        }
        if (!modifiers.contains(Modifier.SUSPEND)) {
            logger.error("All functions in @API interfaces must be suspension functions.", this)
        }
        if (!isAbstract) {
            logger.error("Functions with a body are not allowed in @API interfaces.", this)
        }
        if (typeParameters.isNotEmpty()) {
            logger.error("Functions with type parameters are not allowed in @API interfaces.", this)
        }

        val allParameterAnnotations = parameters
            .flatMap { parameter ->
                val parameterAnnotations = parameter.findHttpParameterAnnotations()
                if (parameterAnnotations.isEmpty()) {
                    logger.error("@HTTP function parameter must have a valid annotation.", parameter)
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
                        badFormatException.message ?: "Bad Content-Type format: ${bodyAnnotation.contentType}.",
                        bodyAnnotation.annotation
                    )
                }
            }
            httpMethodAnnotations.forEach { httpMethodAnnotation ->
                if (!httpMethodAnnotation.method.allowsBody) {
                    logger.error(
                        "@Body is not allowed in ${httpMethodAnnotation.method} requests.",
                        bodyAnnotation.annotation
                    )
                }
            }
        }

        val urlAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Url>()
        if (urlAnnotations.size > 1) {
            logger.error("Multiple @Url parameters are not allowed.", this)
        }
        httpMethodAnnotations.forEach { httpMethodAnnotation ->
            if (httpMethodAnnotation.relativePath == null && urlAnnotations.isEmpty()) {
                logger.error("URL must be provided either by @${httpMethodAnnotation.method} or via @Url.", this)
            }
            if (httpMethodAnnotation.relativePath != null && urlAnnotations.isNotEmpty()) {
                logger.error("URL cannot be provided by both @${httpMethodAnnotation.method} and @Url.", this)
            }
        }

        val pathAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Path>()
        httpMethodAnnotations.forEach { httpMethodAnnotation ->
            val expectedPathParameterNames: MutableSet<String> =
                httpMethodAnnotation.relativePath
                    ?.asSequence()
                    ?.filterIsInstance<Dynamic>()
                    ?.map { it.parameterName }
                    .orEmpty()
                    .toMutableSet()

            pathAnnotations.groupBy { it.name }.forEach { (name, occurrences) ->
                if (occurrences.size > 1) {
                    logger.error(
                        "@Path '$name' is defined ${occurrences.size} times, but at most once is allowed.",
                        this
                    )
                }
                if (!expectedPathParameterNames.contains(name)) {
                    occurrences.forEach { pathAnnotation ->
                        logger.error(
                            "@${httpMethodAnnotation.method} URL does not define a dynamic path parameter matching '$name'.",
                            pathAnnotation.annotation
                        )
                    }
                }
                expectedPathParameterNames.remove(name)
            }
            expectedPathParameterNames.forEach { missingPathParameter ->
                logger.error(
                    "Missing @Path for '$missingPathParameter', which is defined in the @${httpMethodAnnotation.method} URL.",
                    this
                )
            }
        }

        val httpMethodAnnotation = httpMethodAnnotations.firstOrNull()
        val relativePath = httpMethodAnnotation?.relativePath
        val relativeUrl = when {
            relativePath != null -> {
                val urlQueryParameters = httpMethodAnnotation.queryParameters
                val dynamicQueryParameters = allParameterAnnotations
                    .filterIsInstance<HttpParameterAnnotation.Query>()
                    .map { it.name to Dynamic(parameterName = it.parameter.name!!.asString()) }

                val allQueryParameters: List<Pair<String, StringValue>> = urlQueryParameters + dynamicQueryParameters
                allQueryParameters.groupBy { it.first }.forEach { (name, occurrences) ->
                    if (occurrences.size > 1) {
                        logger.error(
                            "@Query '$name' is defined ${occurrences.size} times, but at most once is allowed.",
                            this
                        )
                    }
                }

                RelativeUrl.Template(
                    path = relativePath,
                    queryParameters = allQueryParameters.toMap()
                )
            }

            urlAnnotations.isNotEmpty() -> urlAnnotations.first().parameter.name?.asString()?.let { parameterName ->
                RelativeUrl.Parameter(name = parameterName)
            }

            else -> null
        }

        if (httpMethodAnnotation == null || relativeUrl == null) {
            return null
        }

        val serviceFunctionParameters: Map<String, TypeName>? = allParameterAnnotations
            .mapNotNull { httpParameterAnnotation ->
                val parameterName = httpParameterAnnotation.parameter.name!!.asString()
                val parameterType = httpParameterAnnotation.parameter.type!!.resolve()
                val typeName = if (httpParameterAnnotation is HttpParameterAnnotation.Body) {
                    parameterType?.typeNameWithSerializableChecks(
                        node = httpParameterAnnotation.annotation,
                        typeDescriptionForErrorMessage = "@Body parameter type"
                    )
                } else {
                    parameterType?.typeName()
                }
                if (typeName == null) {
                    val annotationName = httpParameterAnnotation.annotation.shortName.asString()
                    logger.error(
                        "Could not resolve the @$annotationName parameter type or one of its type arguments.",
                        httpParameterAnnotation.parameter
                    )
                    return@mapNotNull null
                }
                parameterName to typeName
            }
            .toMap()
            .takeIf { it.size == allParameterAnnotations.size }

        val headers = mutableListOf<Pair<String, StringValue>>()
        // dynamic headers
        headers.addAll(
            allParameterAnnotations
                .asSequence()
                .filterIsInstance<HttpParameterAnnotation.Header>()
                .mapNotNull { headerAnnotation ->
                    val headerName = headerAnnotation.name
                    if (headerName == HttpHeaders.ContentType) {
                        logger.error(
                            "${HttpHeaders.ContentType} header cannot be defined via @Header. " +
                                "Set the desired 'contentType' in a @Body parameter instead.",
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
                    parameterName?.let { headerName to Dynamic(parameterName) }
                }
        )
        headers.addAll(findStaticHeaders())

        val bodyParameterName = bodyAnnotations.getOrNull(0)?.parameter?.name?.asString()
        val bodyContentType = bodyAnnotations.getOrNull(0)?.contentType
        val requestBody = if (bodyParameterName != null && bodyContentType != null) {
            Service.Function.Http.RequestBody(parameterName = bodyParameterName, contentType = bodyContentType)
        } else {
            null
        }

        val returnType = returnType?.resolve()
        val returnTypeName = returnType
            ?.typeNameWithSerializableChecks(this, typeDescriptionForErrorMessage = "Return type")

        if (returnTypeName == null) {
            logger.error(
                "Could not resolve the HTTP function return type or one of its type arguments.",
                this
            )
            return null
        }

        if (serviceFunctionParameters == null) {
            return null
        }

        return Service.Function.Http(
            name = simpleName.asString(),
            parameters = serviceFunctionParameters,
            method = httpMethodAnnotation.method,
            relativeUrl = relativeUrl,
            headers = headers,
            requestBody = requestBody,
            returnType = returnTypeName
        )
    }

    private fun KSFunctionDeclaration.findHttpMethodAnnotations(): List<HttpMethodAnnotation> {
        return annotations.mapNotNull { annotation ->
            val httpMethod = when (annotation.shortName.asString()) {
                "DELETE" -> HttpMethod.DELETE
                "GET" -> HttpMethod.GET
                "HEAD" -> HttpMethod.HEAD
                "OPTIONS" -> HttpMethod.OPTIONS
                "PATCH" -> HttpMethod.PATCH
                "POST" -> HttpMethod.POST
                "PUT" -> HttpMethod.PUT
                else -> return@mapNotNull null
            }
            val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
            var relativePath: List<StringValue>? = null
            val queryParameters = mutableListOf<Pair<String, Static>>()
            annotation.arguments.getOrNull(0)?.let { urlArgument ->
                val url = urlArgument.value as String
                if (url.isBlank()) return@let

                val (path, query) = url
                    .split("?", limit = 2)
                    .let { it[0] to it.getOrNull(1) }

                relativePath = path.split('/').map { part -> part.parseUrlTemplatePart() }

                query?.split('&')?.forEach { queryParameter ->
                    val (name, value) = queryParameter
                        .split('=', limit = 2)
                        .let { it[0] to it.getOrNull(1) }

                    if (value != null) {
                        when (val stringValue = value.parseUrlTemplatePart()) {
                            is Dynamic -> {
                                val param = stringValue.parameterName
                                logger.error(
                                    "Dynamic query parameters are not allowed in the URL. Found: {$param}. " +
                                        "Use @Query function parameters instead.",
                                    annotation
                                )
                            }
                            is Static -> {
                                queryParameters.add(Pair(name, stringValue))
                            }
                        }
                    } else {
                        logger.error("Invalid query parameter format: $queryParameter.", annotation)
                    }
                }
            }
            HttpMethodAnnotation(
                annotation = annotation,
                annotationType = resolvedAnnotationType,
                method = httpMethod,
                relativePath = relativePath,
                queryParameters = queryParameters
            )
        }
    }

    private fun KSFunctionDeclaration.findStaticHeaders(): List<Pair<String, StringValue>> {
        return annotations
            .asSequence()
            .filter { it.shortName.asString() == "Headers" }
            .filter { it.resolveHttpAnnotationType() != null }
            .flatMap { annotation ->
                @Suppress("UNCHECKED_CAST")
                val headerStrings = annotation.arguments[0].value as List<String>
                headerStrings.asSequence().mapNotNull { headerString ->
                    val colonSplits = headerString.split(":", limit = 2)
                    if (colonSplits.size != 2) {
                        logger.error(
                            "@Headers values must be formatted as 'Name: Value'. Found: '$headerString'.",
                            annotation
                        )
                        return@mapNotNull null
                    }
                    val name = colonSplits[0].trim()
                    if (name == HttpHeaders.ContentType) {
                        logger.error(
                            "${HttpHeaders.ContentType} header cannot be defined via @Headers. " +
                                "Set the desired 'contentType' in a @Body parameter instead.",
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
                    val value = colonSplits[1].trim()
                    name to Static(value)
                }
            }
            .toList()
    }

    private fun KSVariableParameter.findHttpParameterAnnotations(): List<HttpParameterAnnotation> {
        return annotations.mapNotNull { annotation ->
            when (annotation.shortName.asString()) {
                "Body" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    val contentType = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
                    validateContentType(contentType, annotation)
                    HttpParameterAnnotation.Body(
                        contentType = contentType,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "JsonBody" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    HttpParameterAnnotation.Body(
                        contentType = ContentType.Application.Json.toString(),
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Header" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    val name = annotation.arguments.getOrNull(0)?.value ?: return@mapNotNull null
                    HttpParameterAnnotation.Header(
                        name = name as String,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Path" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
                    HttpParameterAnnotation.Path(
                        name = name,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Query" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    val name = annotation.arguments.getOrNull(0)?.value as? String ?: return@mapNotNull null
                    HttpParameterAnnotation.Query(
                        name = name,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Url" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
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

    private fun KSType.typeNameWithSerializableChecks(
        node: KSNode,
        typeDescriptionForErrorMessage: String
    ): TypeName? {
        fun KSDeclaration.isSerializable(): Boolean {
            return qualifiedName?.let { BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES.contains(it.asString()) } == true ||
                annotations.any {
                    it.shortName.asString() == "Serializable" &&
                        it.annotationType.resolve()?.qualifier() == "kotlinx.serialization"
                }
        }

        if (!declaration.isSerializable()) {
            val typeName = with(declaration) { (qualifiedName ?: simpleName).asString() }
            logger.error(
                "$typeDescriptionForErrorMessage '$typeName' is neither @Serializable " +
                    "nor one of: $BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES",
                node
            )
        }

        return typeName { typeArgument, type ->
            if (!type.declaration.isSerializable()) {
                val typeName = with(type.declaration) { (qualifiedName ?: simpleName).asString() }
                logger.error(
                    "$typeDescriptionForErrorMessage type argument '$typeName' is neither @Serializable " +
                        "nor one of: $BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES",
                    typeArgument
                )
            }
        }
    }

    private fun validateContentType(contentType: String, node: KSNode) = try {
        ContentType.parse(contentType)
    } catch (e: BadContentTypeFormatException) {
        logger.error("Invalid content type: '$contentType'", node)
    }
}

private data class HttpMethodAnnotation(
    val annotation: KSAnnotation,
    val annotationType: KSType,
    val method: HttpMethod,
    val relativePath: List<StringValue>?,
    val queryParameters: List<Pair<String, Static>>
)

private sealed class HttpParameterAnnotation {
    abstract val parameter: KSVariableParameter
    abstract val annotation: KSAnnotation
    abstract val annotationType: KSType

    data class Body(
        val contentType: String,
        override val parameter: KSVariableParameter,
        override val annotation: KSAnnotation,
        override val annotationType: KSType
    ) : HttpParameterAnnotation()

    data class Header(
        val name: String,
        override val parameter: KSVariableParameter,
        override val annotation: KSAnnotation,
        override val annotationType: KSType
    ) : HttpParameterAnnotation()

    data class Path(
        val name: String,
        override val parameter: KSVariableParameter,
        override val annotation: KSAnnotation,
        override val annotationType: KSType
    ) : HttpParameterAnnotation()

    data class Query(
        val name: String,
        override val parameter: KSVariableParameter,
        override val annotation: KSAnnotation,
        override val annotationType: KSType
    ) : HttpParameterAnnotation()

    data class Url(
        override val parameter: KSVariableParameter,
        override val annotation: KSAnnotation,
        override val annotationType: KSType
    ) : HttpParameterAnnotation()
}

private fun String.parseUrlTemplatePart(): StringValue =
    if (startsWith('{') && endsWith('}')) {
        Dynamic(parameterName = removePrefix("{").removeSuffix("}"))
    } else {
        Static(content = this)
    }

private fun KSAnnotation.resolveHttpAnnotationType(): KSType? =
    annotationType.resolve()?.takeIf { it.qualifier() == HTTP_ANNOTATIONS_PACKAGE_NAME }

private const val HTTP_ANNOTATIONS_PACKAGE_NAME = "connector.http"

private val BUILT_IN_SERIALIZABLE_TYPES_QUALIFIED_NAMES = listOf(
    "kotlin.Unit",

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
    "kotlin.collections.Set"
)
