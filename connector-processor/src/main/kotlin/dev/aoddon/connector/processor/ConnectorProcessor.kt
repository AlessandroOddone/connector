package dev.aoddon.connector.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.aoddon.connector.codegen.toFileSpec
import io.ktor.utils.io.core.use
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8

public class ConnectorProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return ConnectorProcessor(
      codeGenerator = environment.codeGenerator,
      serviceParser = ServiceParser(environment.logger)
    )
  }
}

private class ConnectorProcessor(
  private val codeGenerator: CodeGenerator,
  private val serviceParser: ServiceParser
) : SymbolProcessor {
  override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
      .getSymbolsWithAnnotation(SERVICE_ANNOTATION_QUALIFIED_NAME)
      .forEach { annotated ->
        val containingFile = (annotated as? KSClassDeclaration)?.containingFile ?: return@forEach
        val service = serviceParser.parse(annotated) ?: return@forEach
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

    return emptyList()
  }
}

private const val SERVICE_ANNOTATION_QUALIFIED_NAME = "dev.aoddon.connector.Service"
