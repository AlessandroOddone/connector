package connector.processor.util

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.ksp.symbol.ClassKind
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSType
import org.jetbrains.kotlin.ksp.symbol.KSTypeArgument
import org.jetbrains.kotlin.ksp.symbol.Nullability

internal fun KSType.qualifier() = declaration.qualifiedName?.getQualifier()

internal val KSClassDeclaration.isInterface get() = classKind == ClassKind.INTERFACE

internal val KSClassDeclaration.isTopLevel get() = parentDeclaration == null

internal fun KSClassDeclaration.className(): ClassName {
    val packageName = packageName.asString()
    val simpleNames = qualifiedName!!.asString()
        .removePrefix("$packageName.")
        .split(".")
        .toList()

    return ClassName(packageName = packageName, simpleNames = simpleNames)
}

internal fun KSType.className(): ClassName? {
    val packageName = declaration.packageName.asString()
    val simpleNames = declaration.qualifiedName?.asString()
        ?.removePrefix("$packageName.")
        ?.split(".")
        ?.toList()
        ?: return null

    val className = ClassName(packageName = packageName, simpleNames = simpleNames)
    return if (nullability == Nullability.NULLABLE) {
        className.copy(nullable = true) as ClassName
    } else {
        className
    }
}

internal fun KSType.typeName(
    doOnTypeArgumentResolved: ((KSTypeArgument, KSType) -> Unit)? = null
): TypeName? {
    return if (arguments.isEmpty()) {
        className()
    } else {
        className()
            ?.parameterizedBy(
                typeArguments = arguments.map { typeArgument ->
                    typeArgument.type?.resolve()
                        ?.also { type -> doOnTypeArgumentResolved?.invoke(typeArgument, type) }
                        ?.typeName(doOnTypeArgumentResolved)
                        ?: return null
                }
            )
            ?.run {
                val nullable = nullability == Nullability.NULLABLE
                if (isNullable != nullable) {
                    copy(nullable = nullable)
                } else {
                    this
                }
            }
    }
}
