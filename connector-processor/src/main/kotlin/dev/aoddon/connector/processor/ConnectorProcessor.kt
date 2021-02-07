package dev.aoddon.connector.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.aoddon.connector.codegen.toFileSpec
import io.ktor.utils.io.core.use
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8

public class ConnectorProcessor : SymbolProcessor {
  private lateinit var codeGenerator: CodeGenerator
  private lateinit var serviceParser: ServiceParser

  override fun init(
    options: Map<String, String>,
    kotlinVersion: KotlinVersion,
    codeGenerator: CodeGenerator,
    logger: KSPLogger
  ) {
    this.codeGenerator = codeGenerator
    this.serviceParser = ServiceParser(logger)
  }

  override fun process(resolver: Resolver) {
    resolver
      .getSymbolsWithAnnotation(SERVICE_ANNOTATION_QUALIFIED_NAME)
      .forEach { annotated ->
        val containingFile = (annotated as? KSClassDeclaration)?.containingFile ?: return@forEach
        val service = serviceParser.parse(annotated)
        OutputStreamWriter(
          codeGenerator.createNewFile(
            dependencies = Dependencies(
              aggregating = false,
              sources = arrayOf(containingFile)
            ),
            packageName = annotated.packageName.asString(),
            fileName = service.name,
            extensionName = "kt"
          ),
          UTF_8
        ).use(service.toFileSpec()::writeTo)
      }
  }

  override fun finish() {
  }
}

private const val SERVICE_ANNOTATION_QUALIFIED_NAME = "dev.aoddon.connector.Service"
