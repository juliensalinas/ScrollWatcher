// Prefer GOOGLE_MAVEN_MIRROR when dl.google.com is unreachable (same pattern as other projects on this box).
pluginManagement {
    val googleMirror: String? = System.getenv("GOOGLE_MAVEN_MIRROR")
    repositories {
        if (googleMirror.isNullOrBlank()) google() else maven(googleMirror)
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val googleMirror: String? = System.getenv("GOOGLE_MAVEN_MIRROR")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (googleMirror.isNullOrBlank()) google() else maven(googleMirror)
        mavenCentral()
    }
}

rootProject.name = "ScrollWatcher"
include(":app")
