plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinxSerialization)
}

android {
    namespace = "com.phynix.artham"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.phynix.artham"
        minSdk = 24
        targetSdk = 36
        versionCode = 25
        versionName = "2.5.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://pgrgcpyysvuzozylgump.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBncmdjcHl5c3Z1em96eWxndW1wIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM5NTA3ODMsImV4cCI6MjA5OTUyNjc4M30.BhM1BPFcKyKwH0jvFowyl8TguSNiTaEWNJlwQWQ1_kM\"")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
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

    // --- Firebase removed: All data now uses Room + Supabase ---

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

    // --- Room Database ---
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)

    // --- Supabase ---
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.gotrue)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}