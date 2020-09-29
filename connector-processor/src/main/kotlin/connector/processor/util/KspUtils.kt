package connector.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

internal val KSType.packageName get() = declaration.packageName.asString()

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
