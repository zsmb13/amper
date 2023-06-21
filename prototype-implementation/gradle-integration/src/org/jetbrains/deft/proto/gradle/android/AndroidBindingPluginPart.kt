package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.AndroidArtifactPart
import org.jetbrains.deft.proto.frontend.JavaArtifactPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.TestArtifact
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.deft.proto.gradle.kmpp.doDependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

fun applyAndroidAttributes(ctx: PluginPartCtx) = AndroidBindingPluginPart(ctx).apply()

@Suppress("LeakingThis")
open class AndroidAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.ANDROID), DeftNamingConventions {

    internal val androidPE = project.extensions.findByName("android") as BaseExtension?

    internal val androidSourceSets get() = androidPE?.sourceSets

}

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class AndroidBindingPluginPart(
    ctx: PluginPartCtx,
) : AndroidAwarePart(ctx), KMPEAware {

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    /**
     * Entry point for this plugin part.
     */
    fun apply() {
        adjustCompilations()
        adjustAndroidSourceSets()
        applySettings()
    }

    @Suppress("UnstableApiUsage")
    private fun adjustAndroidSourceSets() = with(AndroidDeftNamingConvention) {
        // Clear android source sets that are not created by us.
        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be called after project evaluation.
        androidSourceSets?.all {
            val fragment = it.deftFragment
            if (fragment != null) {
                it.kotlin.setSrcDirs(fragment.sourcePaths)
                it.java.setSrcDirs(fragment.sourcePaths)
                it.resources.setSrcDirs(fragment.resourcePaths)
                it.res.setSrcDirs(fragment.androidResPaths)
                it.manifest.srcFile("${fragment.sourcePath}/Manifest.xml")
            } else {
                it.kotlin.setSrcDirs(emptyList<Any>())
                it.java.setSrcDirs(emptyList<Any>())
                it.resources.setSrcDirs(emptyList<Any>())
            }
        }
    }

    private fun adjustCompilations() = with (KotlinDeftNamingConvention) {
        platformArtifacts.forEach { artifact ->
            artifact.platformFragments.forEach { fragment ->
                artifact.parts.find<JavaArtifactPart>()?.jvmTarget?.let { jvmTarget ->
                    project.afterEvaluate {
                        val androidTarget = fragment.target ?: return@afterEvaluate
                        val compilations = if (artifact is TestArtifact) {
                            androidTarget.compilations.matching { it.name.lowercase().contains("test") }
                        } else {
                            androidTarget.compilations.matching { !it.name.lowercase().contains("test") }
                        }
                        compilations.configureEach {
                            it.compileTaskProvider.configure {
                                it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(jvmTarget))
                            }

                            it.kotlinSourceSets.forEach { compilationSourceSet ->
                                if (compilationSourceSet != fragment.kotlinSourceSet) {
                                    println("Attaching fragment ${fragment.name} to compilation ${it.name}")
                                    compilationSourceSet.doDependsOn(fragment)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applySettings() {
        module.artifacts.forEach { artifact ->
            artifact.platforms.find { it == Platform.ANDROID } ?: return@forEach
            val part =
                artifact.parts.find<AndroidArtifactPart>()
                    ?: error("No android properties for an artifact ${artifact.name}")
            androidPE?.apply {
                part.compileSdkVersion?.let {
                    compileSdkVersion(it)
                }
                defaultConfig {
                    it.minSdkVersion(part.minSdkVersion ?: 24)
                }
                compileOptions {
                    it.setSourceCompatibility(part.sourceCompatibility)
                    it.setTargetCompatibility(part.targetCompatibility)
                }
            }
        }

        //        androidPE.apply {
//            allCollapsed["target.android.compileSdkVersion"]?.first()?.let { compileSdkVersion(it.toInt()) }
//            defaultConfig {
//                allCollapsed["target.android.minSdkVersion"]?.first()?.let { minSdkVersion(it) }
//                allCollapsed["target.android.targetSdkVersion"]?.first()?.let { targetSdkVersion(it) }
//                allCollapsed["target.android.versionCode"]?.first()?.let { versionCode(it.toInt()) }
//                allCollapsed["target.android.versionName"]?.first()?.let { versionName(it) }
//                allCollapsed["target.android.applicationId"]?.first()?.let { applicationId(it) }
//            }
//            compileOptions {
//                allCollapsed["target.android.sourceCompatibility"]?.first()?.let { sourceCompatibility(it) }
//                allCollapsed["target.android.targetCompatibility"]?.first()?.let { targetCompatibility(it) }
//            }
//        }
    }
}