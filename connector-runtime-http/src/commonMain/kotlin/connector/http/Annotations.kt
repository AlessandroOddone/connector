package connector.http

/**
 * Http Methods
 */

@Target(AnnotationTarget.FUNCTION)
public annotation class DELETE(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class GET(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class HEAD(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class OPTIONS(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class PATCH(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class POST(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
public annotation class PUT(val url: String = "")

/**
 * URL
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Path(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Query(val name: String)

/**
 * Headers
 */

@Target(AnnotationTarget.FUNCTION)
public annotation class Headers(vararg val values: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Header(val name: String)

/**
 * Body
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Body(val contentType: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class JsonBody
