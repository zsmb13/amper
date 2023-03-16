package org.jetbrains.deft.proto.gradle.android

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.DefaultConfig
import org.jetbrains.deft.proto.gradle.BindingPluginPart
import org.jetbrains.deft.proto.gradle.PluginPartCtx

fun applyAndroidAttributes(ctx: PluginPartCtx) = AndroidBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class AndroidBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx {

    private val androidPE: CommonExtension<*, *, DefaultConfig, *> =
        project.extensions.getByType(CommonExtension::class.java) as CommonExtension<*, *, DefaultConfig, *>

    fun apply() {
        androidPE.apply {
            sourceSets.maybeCreate("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")

            allCollapsed["target.android.compileSdkVersion"]?.first()?.let { compileSdkVersion(it.toInt()) }
            defaultConfig {
                allCollapsed["target.android.minSdkVersion"]?.first()?.let { minSdkVersion(it) }
                allCollapsed["target.android.targetSdkVersion"]?.first()?.let { targetSdkVersion(it) }
                allCollapsed["target.android.versionCode"]?.first()?.let { versionCode(it.toInt()) }
                allCollapsed["target.android.versionName"]?.first()?.let { versionName(it) }
                allCollapsed["target.android.applicationId"]?.first()?.let { applicationId(it) }
            }
            compileOptions {
                allCollapsed["target.android.sourceCompatibility"]?.first()?.let { sourceCompatibility(it) }
                allCollapsed["target.android.targetCompatibility"]?.first()?.let { targetCompatibility(it) }
            }
        }
    }
}