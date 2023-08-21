package org.jetbrains.deft.proto.gradle.base

import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.Fragment
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.gradle.ArtifactWrapper
import org.jetbrains.deft.proto.gradle.PlatformAware
import org.jetbrains.deft.proto.gradle.PotatoModuleWrapper
import org.jetbrains.deft.proto.gradle.buildDir
import java.nio.file.Path

/**
 * Shared module plugin properties.
 */
interface BindingPluginPart {
    val project: Project
    val model: Model
    val module: PotatoModuleWrapper
    val moduleToProject: Map<Path, String>

    val PotatoModule.linkedProject
        get() = project.project(
            moduleToProject[buildDir]
                ?: error("No linked Gradle project found for module $userReadableName")
        )

    // FIXME Rewrite path conventions completely!
    val Fragment.path get() = module.buildDir.resolve(name)

    val needToApply: Boolean get() = false

    /**
     * Logic, that needs to be applied before project evaluation.
     */
    fun applyBeforeEvaluate() {}

    /**
     * Invoked during configuration, when deft extensions value is changed.
     */
    // Workaround for those settings, that need to be adjusted
    // after [beforeProject], but before [afterProject].
    // (For example, Android source sets.)
    fun onDefExtensionChanged() {}

    /**
     * Logic, that needs to be applied after project evaluation.
     */
    fun applyAfterEvaluate() {}
}

open class NoneAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, null), DeftNamingConventions

/**
 * Arguments deduplication class (by delegation in constructor).
 */
open class PluginPartCtx(
    override val project: Project,
    override val model: Model,
    override val module: PotatoModuleWrapper,
    override val moduleToProject: Map<Path, String>,
) : BindingPluginPart

/**
 * Part with utilities to get specific artifacts and fragments.
 */
open class SpecificPlatformPluginPart(
    ctx: BindingPluginPart,
    private val platform: Platform?,
) : BindingPluginPart by ctx {

    @Suppress("LeakingThis")
    val platformArtifacts = module.artifacts.filterByPlatform(platform)

    val ArtifactWrapper.platformFragments get() = fragments.filterByPlatform(platform)

    @Suppress("LeakingThis")
    val platformFragments = module.fragments.filterByPlatform(platform)

    @Suppress("LeakingThis")
    val leafPlatformFragments = module.leafFragments.filterByPlatform(platform)

    private fun <T : PlatformAware> Collection<T>.filterByPlatform(platform: Platform?) =
        if (platform != null) filter { it.platforms.contains(platform) } else emptyList()

}