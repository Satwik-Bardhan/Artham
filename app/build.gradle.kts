

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleGmsGoogleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

android {
    namespace = "com.phynix.artham"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.phynix.artham"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.9"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // --- AndroidX & UI Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // --- Lifecycle ---
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)

    // --- Firebase (Using BoM for version management) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // --- Google Play In-App Review ---
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    // --- Google Play In-App Update ---
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)

    // --- Google Services ---
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)

    // --- Third Party Libraries ---
    implementation(libs.mpandroidchart)
    implementation(libs.dhaval.colorpicker)
    implementation(libs.glide)
    implementation(libs.itextpdf)
    implementation(libs.skydoves.colorpickerview)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- UI Effects ---
    implementation(libs.shimmer)

    // --- Offline Support ---
    implementation(libs.gson)
}