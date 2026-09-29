buildscript {
    repositories {
        mavenCentral()
        val googleMirror: String? = System.getenv("GOOGLE_MAVEN_MIRROR")
        if (googleMirror.isNullOrBlank()) {
            google()
        } else {
            maven(googleMirror)
        }
    }
    dependencies {
        // Pin Kotlin compiler used by AGP's built-in Kotlin support (AGP 9+).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
