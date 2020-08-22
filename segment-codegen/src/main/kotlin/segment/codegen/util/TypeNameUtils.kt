package segment.codegen.util

import com.squareup.kotlinpoet.TypeName

fun TypeName.nonNull(): TypeName = if (isNullable) copy(nullable = false) else this
