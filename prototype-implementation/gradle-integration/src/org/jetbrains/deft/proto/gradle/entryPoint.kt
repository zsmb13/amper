package org.jetbrains.deft.proto.gradle

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.propagate.resolved

class BindingSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootPath = settings.rootDir.toPath().toAbsolutePath()
        val model = ModelInit.getModel(rootPath).resolved
        // Use [ModelWrapper] to cache and preserve links on [PotatoModule].
        val modelWrapper = ModelWrapper(model)
        SettingsPluginRun(settings, modelWrapper).run()
    }
}