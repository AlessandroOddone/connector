package connector.processor

import com.squareup.kotlinpoet.FileSpec
import connector.codegen.toFileSpec
import org.jetbrains.kotlin.ksp.processing.CodeGenerator
import org.jetbrains.kotlin.ksp.processing.KSPLogger
import org.jetbrains.kotlin.ksp.processing.Resolver
import org.jetbrains.kotlin.ksp.processing.SymbolProcessor
import org.jetbrains.kotlin.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.ksp.symbol.KSNode
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8

class ConnectorProcessor : SymbolProcessor {
  private lateinit var codeGenerator: CodeGenerator
  private lateinit var serviceParser: ServiceParser

  override fun init(
    options: Map<String, String>,
    kotlinVersion: KotlinVersion,
    codeGenerator: CodeGenerator,
    logger: KSPLogger
  ) {
    this.codeGenerator = codeGenerator
    this.serviceParser = ServiceParser(
      // workaround until error logging works correctly with Gradle plugin
      // https://github.com/android/kotlin/issues/1
      object : KSPLogger by logger {
        override fun error(message: String, symbol: KSNode?) {
          throw IllegalStateException(message)
        }
      }
    )
  }

  override fun process(resolver: Resolver) {
    resolver
      .getSymbolsWithAnnotation(SERVICE_ANNOTATION_QUALIFIED_NAME)
      .map { annotated -> serviceParser.parse(annotated as KSClassDeclaration) }
      .forEach { service -> service.toFileSpec().writeTo(codeGenerator) }
  }

  override fun finish() {
  }
}

private fun FileSpec.writeTo(codeGenerator: CodeGenerator) {
  OutputStreamWriter(codeGenerator.createNewFile(packageName, name), UTF_8).use(::writeTo)
}

private const val SERVICE_ANNOTATION_QUALIFIED_NAME = "connector.Service"
