package connector.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName

internal val KSType.packageName get() = declaration.packageName.asString()

internal val KSClassDeclaration.isInterface get() = classKind == ClassKind.INTERFACE

internal val KSClassDeclaration.isTopLevel get() = parentDeclaration == null

internal fun KSDeclaration.className(): ClassName? {
  val packageName = packageName.asString()
  val simpleNames = qualifiedName?.asString()
    ?.removePrefix("$packageName.")
    ?.split(".")
    ?.toList()
    ?: return null

  return ClassName(packageName = packageName, simpleNames = simpleNames)
}

internal fun KSType.className(): ClassName? {
  val className = declaration.className() ?: return null
  return if (nullability == Nullability.NULLABLE) {
    className.copy(nullable = true) as ClassName
  } else {
    className
  }
}

internal fun KSType.typeName(
  onTypeArgumentResolvedListener: OnTypeArgumentResolvedListener? = null
): TypeName? {
  return if (arguments.isEmpty()) {
    className()
  } else {
    className()
      ?.parameterizedBy(
        typeArguments = arguments.map { typeArgument ->
          val resolvedType = typeArgument.type?.resolve()
          val resolvedTypeName = resolvedType?.typeName(onTypeArgumentResolvedListener)
          return@map when (typeArgument.variance) {
            Variance.STAR -> STAR
            Variance.COVARIANT -> WildcardTypeName.producerOf(resolvedTypeName ?: return null)
            Variance.CONTRAVARIANT -> WildcardTypeName.consumerOf(resolvedTypeName ?: return null)
            Variance.INVARIANT -> resolvedTypeName ?: return null
          }.also {
            onTypeArgumentResolvedListener?.onTypeArgumentResolved(
              argument = typeArgument,
              argumentTypeName = it,
              argumentTypeDeclaration = resolvedType?.declaration,
              argumentOwner = this
            )
          }
        }
      )
      ?.run {
        val nullable = nullability == Nullability.NULLABLE
        if (isNullable != nullable) copy(nullable = nullable) else this
      }
  }
}

internal interface OnTypeArgumentResolvedListener {
  fun onTypeArgumentResolved(
    argument: KSTypeArgument,
    argumentTypeName: TypeName,
    argumentTypeDeclaration: KSDeclaration?,
    argumentOwner: KSType,
  )
}
