// [FIX] Add this import at the very top
import com.google.firebase.appdistribution.gradle.AppDistributionExtension

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.appdistribution")
}

android {
    namespace = "com.phynix.artham"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phynix.artham"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // [FIX] Correct way to configure Firebase App Distribution in Kotlin DSL
        debug {
            configure<AppDistributionExtension> {
                serviceCredentialsFile = "${project.rootDir}/auth-key.json"

                // MAKE SURE THIS LINE IS HERE AND HAS YOUR REAL EMAIL
                testers = "satwikbardhan67@gmail.com"
                groups = "clg-frnds, family"

                releaseNotes = "Testing shake feature"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX & UI
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Core KTX
    implementation("androidx.core:core-ktx:1.15.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.4")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // Auth & Location
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 3rd Party
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.QuadFlask:colorpicker:0.0.15")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.itextpdf:itextpdf:5.5.13.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // App Distribution SDK
    implementation("com.google.firebase:firebase-appdistribution:16.0.0-beta14")

    implementation("com.google.android.material:material:1.12.0")
}