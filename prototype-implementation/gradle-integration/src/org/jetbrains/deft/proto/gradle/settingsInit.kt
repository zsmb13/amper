package org.jetbrains.deft.proto.gradle

import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import kotlin.io.path.extension


/**
 * Gradle setting plugin, that is responsible for:
 * 1. Initializing gradle projects, based on model.
 * 2. Applying kotlin or KMP plugins.
 * 3. Associate gradle projects with modules.
 */
class SettingsPluginRun(
    private val settings: Settings,
    private val model: Model,
) {

    fun run() {
        settings.gradle.knownModel = model
        initProjects(settings, model)

        // Initialize plugins for each module.
        settings.gradle.beforeProject { project ->
            /**
             * Repositories for bundled plugins
             */
            project.repositories.google()
            project.repositories.jcenter()
            // To be able to have import using dev versions of kotlin
            project.repositories.maven { it.setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
            project.repositories.gradlePluginPortal()
            project.repositories.maven { it.setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }

            // Can be empty for root.
            val connectedModule = settings.gradle.projectPathToModule[project.path] ?: return@beforeProject

            // Apply Kotlin plugins.
            if (connectedModule.buildFile.extension == "yaml") {
                project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
            }

            // Apply Binding plugin if there is Pot file.
            if (connectedModule.buildFile.extension == "yaml") {
                project.plugins.apply(BindingProjectPlugin::class.java)
            }

            project.afterEvaluate {
                // W/A for XML factories mess within apple plugin classpath.
                val hasAndroidPlugin = it.plugins.hasPlugin("com.android.application") ||
                        it.plugins.hasPlugin("com.android.library")
                if (hasAndroidPlugin) {
                    adjustXmlFactories()
                }
            }
        }
    }
}
