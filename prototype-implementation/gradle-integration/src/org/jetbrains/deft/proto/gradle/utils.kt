package org.jetbrains.deft.proto.gradle

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import org.jetbrains.deft.proto.frontend.forClosure
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.*

val PotatoModule.buildFile get() = (source as PotatoModuleFileSource).buildFile

val PotatoModule.buildDir get() = buildFile.parent

/**
 * Get or create string key-ed binding map from extension properties.
 */
@Suppress("UNCHECKED_CAST")
fun <K, V> ExtraPropertiesExtension.getBindingMap(name: String) = try {
    this[name] as MutableMap<K, V>
} catch (cause: ExtraPropertiesExtension.UnknownPropertyException) {
    val bindingMap = mutableMapOf<K, V>()
    this[name] = bindingMap
    bindingMap
}

/**
 * Check if the requested platform is included in module.
 */
operator fun PotatoModuleWrapper.contains(platform: Platform) =
    artifactPlatforms.contains(platform)

/**
 * Try extract zero or single element from collection,
 * running [onMany] in other case.
 */
fun <T> Collection<T>.singleOrZero(onMany: () -> Unit): T? =
    if (size > 1) onMany().run { null }
    else singleOrNull()

/**
 * Require exact one element, throw error otherwise.
 */
fun <T> Collection<T>.requireSingle(errorMessage: () -> String): T =
    if (size > 1 || isEmpty()) error(errorMessage())
    else first()

/**
 * Try to find entry point for application.
 */
// TODO Add caching for separated fragments.
enum class EntryPointType(val symbolName: String) { NATIVE("main"), JVM("MainKt") }

val BindingPluginPart.hasGradleScripts get() = module.buildDir.run {
    resolve("build.gradle.kts").exists() || resolve("build.gradle").exists()
}

context(DeftNamingConventions)
@OptIn(ExperimentalPathApi::class)
internal fun findEntryPoint(
    fragment: LeafFragmentWrapper,
    entryPointType: EntryPointType,
    logger: Logger,
): String = with(module) {
    // Collect all fragment paths.
    val allSources = buildSet<Path> {
        fragment.forClosure {
            add(it.wrapped.sourcePath.absolute().normalize())
        }
    }

    val implicitMainFile = allSources.firstNotNullOfOrNull { sourceFolder ->
        sourceFolder.walk(PathWalkOption.BREADTH_FIRST)
            .find { it.name.equals("main.kt", ignoreCase = true) }
            ?.normalize()
            ?.toAbsolutePath()
    }

    if (implicitMainFile == null) {
        val result = entryPointType.symbolName
        logger.warn("Entry point cannot be discovered for ${fragment}. Defaulting to $result")
        return result
    }

    val packageRegex = "^package\\s+([\\w.]+)".toRegex(RegexOption.MULTILINE)
    val pkg = packageRegex.find(implicitMainFile.readText())?.let { it.groupValues[1].trim() }

    val result = if (pkg != null) "$pkg.${entryPointType.symbolName}" else entryPointType.symbolName

    logger.info("Entry point discovered at $result")
    return result
}