package dev.aoddon.connector.http

/*
 * HTTP Methods
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

@Target(AnnotationTarget.FUNCTION)
public annotation class HTTP(val method: String, val url: String = "")

/*
 * URL
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Path(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Query(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class QueryMap

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class QueryName

/*
 * Headers
 */

@Target(AnnotationTarget.FUNCTION)
public annotation class Headers(vararg val values: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Header(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class HeaderMap

/*
 * Body
 */

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Body(val contentType: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Streaming

/*
 * URL Encoded Forms
 */

@Target(AnnotationTarget.FUNCTION)
public annotation class FormUrlEncoded

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Field(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class FieldMap

/*
 * Multipart
 */

@Target(AnnotationTarget.FUNCTION)
public annotation class Multipart(val subtype: String = "form-data")

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class Part(val contentType: String = "", val formFieldName: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class PartIterable(val contentType: String = "", val formFieldName: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER)
public annotation class PartMap(val contentType: String = "")
