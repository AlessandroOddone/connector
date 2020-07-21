package segment.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.ksp.symbol.ClassKind
import org.jetbrains.kotlin.ksp.symbol.KSAnnotation
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSFunctionDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSNode
import org.jetbrains.kotlin.ksp.symbol.KSPropertyDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSVariableParameter
import org.jetbrains.kotlin.ksp.symbol.Modifier
import org.jetbrains.kotlin.ksp.symbol.Nullability
import segment.codegen.Static
import segment.codegen.HttpMethod
import segment.codegen.Dynamic
import segment.codegen.Type
import segment.codegen.RelativeUrl
import segment.codegen.Service
import segment.codegen.StringValue

class ServiceParser {
    fun parse(classDeclaration: KSClassDeclaration): Service = with(classDeclaration) {
        if (!isInterface || !isTopLevel) {
            fail(classDeclaration, "@API target must be a top-level interface.")
        }

        if (superTypes.isNotEmpty()) {
            fail(classDeclaration, "Supertypes are not allowed in @API interfaces.")
        }

        if (typeParameters.isNotEmpty()) {
            fail(classDeclaration, "Type parameters are not allowed in @API interfaces.")
        }

        val serviceName = simpleName.getShortName()
        val serviceFunctions = declarations
            .mapNotNull { declaration ->
                if (declaration is KSPropertyDeclaration) {
                    fail(declaration, "Properties are not allowed in @API interfaces.")
                }
                (declaration as? KSFunctionDeclaration)?.parseServiceFunction()
            }

        return Service(
            name = serviceName,
            functions = serviceFunctions,
            existingParentInterface = ClassName.bestGuess(qualifiedName!!.asString())
        )
    }

    private fun KSFunctionDeclaration.parseServiceFunction(): Service.Function? {
        val httpMethodAnnotations = findHttpMethodAnnotations()
        if (httpMethodAnnotations.isEmpty()) {
            fail(this, "All functions in @API interfaces must be annotated with an HTTP method.")
        }
        if (httpMethodAnnotations.size > 1) {
            fail(this, "Multiple HTTP method annotations are not allowed on a function.")
        }
        if (!modifiers.contains(Modifier.SUSPEND)) {
            fail(this, "All functions in @API interfaces must be suspension functions.")
        }
        if (!isAbstract) {
            fail(this, "Functions with a body are not allowed in @API interfaces.")
        }
        if (typeParameters.isNotEmpty()) {
            fail(this, "Functions with type parameters are not allowed in @API interfaces.")
        }

        val allParameterAnnotations = parameters
            .flatMap { parameter ->
                val parameterAnnotations = parameter.findHttpParameterAnnotations()
                if (parameterAnnotations.isEmpty()) {
                    fail(parameter, "Missing Segment annotation.")
                }
                if (parameterAnnotations.size > 1) {
                    fail(parameter, "Multiple Segment annotations are not allowed on the same parameter.")
                }
                parameterAnnotations
            }

        val bodyAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Body>()
        if (bodyAnnotations.size > 1) {
            fail(this, "Multiple @Body parameters are not allowed.")
        }
        bodyAnnotations.forEach { bodyAnnotation ->
            httpMethodAnnotations.forEach { httpMethodAnnotation ->
                if (!httpMethodAnnotation.method.allowsBody) {
                    fail(
                        bodyAnnotation.annotation,
                        "@Body is not allowed in ${httpMethodAnnotation.method} requests."
                    )
                }
            }
        }

        val urlAnnotations = allParameterAnnotations.filterIsInstance<HttpParameterAnnotation.Url>()
        if (urlAnnotations.size > 1) {
            fail(this, "Multiple @Url parameters are not allowed.")
        }
        httpMethodAnnotations.forEach { httpMethodAnnotation ->
            if (httpMethodAnnotation.relativePath == null && urlAnnotations.isEmpty()) {
                fail(this, "URL must be provided either by @${httpMethodAnnotation.method} or via @Url.")
            }
            if (httpMethodAnnotation.relativePath != null && urlAnnotations.isNotEmpty()) {
                fail(this, "URL cannot be provided by both @${httpMethodAnnotation.method} and @Url.")
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
                    fail(
                        this,
                        "@Path '$name' was defined ${occurrences.size} times, but at most once is allowed."
                    )
                }
                if (!expectedPathParameterNames.contains(name)) {
                    occurrences.forEach { pathAnnotation ->
                        fail(
                            pathAnnotation.annotation,
                            "@${httpMethodAnnotation.method} URL does not define a dynamic path parameter matching '$name'."
                        )
                    }
                }
                expectedPathParameterNames.remove(name)
            }
            expectedPathParameterNames.forEach { missingPathParameter ->
                fail(
                    this,
                    "Missing @Path for '$missingPathParameter', which is defined in the @${httpMethodAnnotation.method} URL."
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
                        fail(
                            this,
                            "@Query '$name' was defined ${occurrences.size} times, but at most once is allowed."
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

        val serviceFunctionParameters: Map<String, Type.Existing> = parameters.associate { parameter ->
            val parameterName = parameter.name!!.asString()
            val typeName = parameter.type!!.resolve()!!.typeName()!!
            parameterName to Type.Existing(typeName)
        }

        val staticHeaders: List<Pair<String, StringValue>> = findStaticHeaders()
        val dynamicHeaders: List<Pair<String, StringValue>> = allParameterAnnotations
            .filterIsInstance<HttpParameterAnnotation.Header>()
            .mapNotNull { headerAnnotation ->
                val headerName = headerAnnotation.name
                val parameterName = headerAnnotation.parameter.name?.asString()
                parameterName?.let { headerName to Dynamic(parameterName) }
            }

        val requestBodyParameterName = bodyAnnotations.getOrNull(0)?.parameter?.name?.asString()
        val responseBodyType = Type.Existing(name = returnType!!.resolve()!!.typeName()!!)

        return Service.Function.Http(
            name = simpleName.asString(),
            parameters = serviceFunctionParameters,
            method = httpMethodAnnotation.method,
            relativeUrl = relativeUrl,
            headers = staticHeaders + dynamicHeaders,
            requestBodyParameterName = requestBodyParameterName,
            responseBodyType = responseBodyType
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
                                fail(
                                    annotation,
                                    "Dynamic query parameters are not allowed in the URL, but {$param} was found. " +
                                            "Use @Query function parameters instead."
                                )
                            }
                            is Static -> {
                                queryParameters.add(Pair(name, stringValue))
                            }
                        }
                    } else {
                        fail(annotation, "Invalid query parameter format: $queryParameter")
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
                        fail(
                            annotation,
                            "@Headers values must be formatted as 'Name: Value', but '$headerString' was found."
                        )
                        return@mapNotNull null
                    }
                    colonSplits[0].trim() to Static(colonSplits[1].trim())
                }
            }
            .toList()
    }

    private fun KSVariableParameter.findHttpParameterAnnotations(): List<HttpParameterAnnotation> {
        return annotations.mapNotNull { annotation ->
            when (annotation.shortName.asString()) {
                "Body" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    HttpParameterAnnotation.Body(
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Header" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    HttpParameterAnnotation.Header(
                        name = annotation.arguments[0].value as String,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Path" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    HttpParameterAnnotation.Path(
                        name = annotation.arguments[0].value as String,
                        parameter = this,
                        annotation = annotation,
                        annotationType = resolvedAnnotationType
                    )
                }
                "Query" -> {
                    val resolvedAnnotationType = annotation.resolveHttpAnnotationType() ?: return@mapNotNull null
                    HttpParameterAnnotation.Query(
                        name = annotation.arguments[0].value as String,
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

    private fun fail(target: KSNode, message: String) {
        throw IllegalStateException(message)
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

private fun KSType.qualifier() = declaration.qualifiedName?.getQualifier()

private val KSClassDeclaration.isInterface get() = classKind == ClassKind.INTERFACE

private val KSClassDeclaration.isTopLevel get() = parentDeclaration == null

private fun KSType.typeName(): TypeName? {
    return declaration.qualifiedName?.asString()
        ?.let { ClassName.bestGuess(it) }
        ?.copy(nullable = nullability == Nullability.NULLABLE)
}

private const val HTTP_ANNOTATIONS_PACKAGE_NAME = "segment.http"
