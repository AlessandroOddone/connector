package dev.aoddon.connector.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSCallableReference
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.util.StringValues

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

internal fun KSTypeReference.resolveTypeInfo(): TypeInfo? = collectTypeInfo(ksTypeArgument = null)

private fun KSTypeReference.collectTypeInfo(
  ksTypeArgument: KSTypeArgument?,
): TypeInfo? {
  return when (element) {
    is KSCallableReference -> {
      val callableReference = element as KSCallableReference
      val receiver = callableReference.receiverType?.run { collectTypeInfo(ksTypeArgument)?.typeName ?: return null }
      val parameters = callableReference.functionParameters.map { ksValueParameter ->
        ParameterSpec(
          name = ksValueParameter.name?.asString() ?: "",
          type = ksValueParameter.type.collectTypeInfo(ksTypeArgument)?.typeName ?: return null,
          modifiers = mutableListOf<KModifier>().apply {
            if (ksValueParameter.isCrossInline) add(KModifier.CROSSINLINE)
            if (ksValueParameter.isNoInline) add(KModifier.NOINLINE)
            if (ksValueParameter.isVararg) add(KModifier.VARARG)
          }
        )
      }
      val returnType = callableReference.returnType.collectTypeInfo(ksTypeArgument)?.typeName ?: return null

      val ksType = resolve()
      val typeName = LambdaTypeName.get(receiver, parameters, returnType)
        .copy(
          nullable = ksType.nullability == Nullability.NULLABLE,
          suspending = modifiers.contains(Modifier.SUSPEND) ||
            // Check resolved type name since we can't retrieve the SUSPEND modifier when the lambda is nullable
            // (https://github.com/google/ksp/issues/354)
            ksType.declaration.qualifiedName?.asString()
            ?.split("kotlin.coroutines.SuspendFunction")
            ?.let { splits -> splits.size == 2 && splits.last().toIntOrNull() != null } == true
        )

      return TypeInfo(
        ksType = ksType,
        typeName = typeName,
        arguments = emptyList(),
        ksTypeArgument = ksTypeArgument
      )
    }

    else -> {
      val ksType = resolve()
      val argumentTypeNames: List<TypeInfo> = ksType.arguments.map { argument ->
        if (argument.variance == Variance.STAR) {
          return@map TypeInfo(
            ksType = null,
            typeName = STAR,
            arguments = emptyList(),
            ksTypeArgument = argument
          )
        }
        val ksTypeReference = argument.type ?: return null
        ksTypeReference.collectTypeInfo(argument) ?: return null
      }
      val rawTypeName = ksType.className()?.let { className ->
        if (argumentTypeNames.isEmpty()) return@let className
        className
          .parameterizedBy(argumentTypeNames.map { it.typeName ?: return@let null })
          .run {
            val nullable = ksType.nullability == Nullability.NULLABLE
            if (isNullable != nullable) copy(nullable = nullable) else this
          }
      }
      val typeName = when (ksTypeArgument?.variance) {
        Variance.COVARIANT -> rawTypeName?.let { WildcardTypeName.producerOf(it) }
        Variance.CONTRAVARIANT -> rawTypeName?.let { WildcardTypeName.consumerOf(it) }
        Variance.STAR, Variance.INVARIANT, null -> rawTypeName
      }
      TypeInfo(
        ksType = ksType,
        typeName = typeName,
        arguments = argumentTypeNames,
        ksTypeArgument = ksTypeArgument
      )
    }
  }
}

internal data class TypeInfo(
  val ksType: KSType?,
  val typeName: TypeName?,
  val arguments: List<TypeInfo>,
  val ksTypeArgument: KSTypeArgument?
) {
  val qualifiedName = ksType?.declaration?.qualifiedName?.asString()
  val isSupportedIterable = SUPPORTED_ITERABLE_QUALIFIED_NAMES.contains(qualifiedName)
  val isSupportedMap = SUPPORTED_MAP_QUALIFIED_NAMES.contains(qualifiedName)
  val isSupportedKtorStringValues = SUPPORTED_STRING_VALUES_QUALIFIED_NAMES.contains(qualifiedName)
}

internal val SUPPORTED_ITERABLE_QUALIFIED_NAMES = listOf(
  "kotlin.collections.Collection",
  "kotlin.collections.Iterable",
  "kotlin.collections.List",
  "kotlin.collections.Set",
)

internal val SUPPORTED_MAP_QUALIFIED_NAMES = listOf(
  "kotlin.collections.Map"
)

internal val SUPPORTED_STRING_VALUES_QUALIFIED_NAMES = listOf(
  Headers::class.qualifiedName!!,
  Parameters::class.qualifiedName!!,
  StringValues::class.qualifiedName!!,
)
