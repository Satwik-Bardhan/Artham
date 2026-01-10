buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // This should match the version you set in libs.versions.toml
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath("com.google.gms:google-services:4.4.2")

        // This should match the new crashlytics version
        classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.1")
        classpath("com.google.firebase:firebase-appdistribution-gradle:5.0.0")
    }
}

plugins {
    // This should also match the version in libs.versions.toml
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}