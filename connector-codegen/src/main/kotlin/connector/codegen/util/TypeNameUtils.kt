package connector.codegen.util

import com.squareup.kotlinpoet.TypeName

internal fun TypeName.nonNull(): TypeName = if (isNullable) copy(nullable = false) else this
