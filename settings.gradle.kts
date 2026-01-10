pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add jitpack if you use it for MPAndroidChart/ColorPicker
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Artham"
include(":app")