package segment.http

/**
 * Http method annotations
 */

@Target(AnnotationTarget.FUNCTION)
annotation class DELETE(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class GET(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class HEAD(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class OPTIONS(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class PATCH(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class POST(val url: String = "")

@Target(AnnotationTarget.FUNCTION)
annotation class PUT(val url: String = "")

/**
 * Parameter annotations
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Header(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Query(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Url
