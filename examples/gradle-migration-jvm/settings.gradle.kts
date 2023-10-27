pluginManagement {
    repositories {
        mavenCentral()
        // add repositories:
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/deft/deft-prototype")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.amper.settings.plugin").version("221-NIGHTLY")
}

rootProject.name = "my-project-name"

include(":lib")
include(":app")


