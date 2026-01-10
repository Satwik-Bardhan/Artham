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

    // =========================================================
    // RESOURCE ORGANIZATION CONFIGURATION
    // =========================================================
    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res", // Default folder (Values, Layouts, etc.)

                // Your New Organized Graphics Folders
                "src/main/res-graphics/icons",
                "src/main/res-graphics/backgrounds",
                "src/main/res-graphics/illustrations",
                "src/main/res-graphics/buttons"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            configure<AppDistributionExtension> {
                serviceCredentialsFile = "${project.rootDir}/auth-key.json"
                testers = "satwikbardhan67@gmail.com"
                groups = "clg-frnds, family, clg-juniors, ttn-frnds, extras"
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
    // --- AndroidX & UI Core ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // --- Lifecycle ---
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")

    // --- Firebase (Using BoM for version management) ---
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appdistribution:16.0.0-beta14")

    // --- Google Services ---
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // --- Third Party Libraries ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.Dhaval2404:ColorPicker:2.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.itextpdf:itextpdf:5.5.13.3")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}