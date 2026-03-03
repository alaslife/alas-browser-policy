import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val secureSyncUrl = localProperties.getProperty("secure.sync.url", "")
val secureSyncApiKey = localProperties.getProperty("secure.sync.api.key", "")

android {
    namespace = "com.sun.alasbrowser"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/Alas/keystore/alasbrowser-release.jks")
            storePassword = "Alas@2026#Secure!"
            keyAlias = "release"
            keyPassword = "Alas@2026#Secure!"
        }
    }

    defaultConfig {
        applicationId = "com.sun.alasbrowser"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SECURE_SYNC_URL", "\"$secureSyncUrl\"")
        buildConfigField("String", "SECURE_SYNC_API_KEY", "\"$secureSyncApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            pickFirsts.add("META-INF/androidx.autofill_autofill.version")
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
}

configurations.all {
    exclude(group = "androidx.annotation", module = "annotation-experimental")
}

dependencies {
    // Force Kotlin stdlib version to match KSP
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.1.0-rc01")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.15.0")
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    
    // Activity
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.ui.util)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // DataStore
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    
    // Media
    implementation(libs.androidx.media)
    
    // AppCompat & Fragment
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    
    // Other AndroidX
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.palette.ktx)
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
    
    // Glance for App Widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    
    // AndroidX WebKit for enhanced WebView features (duplicate removed — using 1.15.0 above)
    
    // Biometric authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    // Media3 for ExoPlayer and MediaSession (Latest version 1.8.0)
    implementation("androidx.media3:media3-exoplayer:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    implementation("androidx.media3:media3-session:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    implementation("androidx.media3:media3-common:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    implementation("androidx.media3:media3-ui:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0") {
        exclude(group = "androidx.appcompat")
        exclude(group = "androidx.autofill")
    }
    
    // QR Code / Barcode Scanning (thin/unbundled - model downloaded via Play Services)
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    val cameraXVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    
    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")

    // Google Sign-In via Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // MediaRouter for casting support
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.2.0")
    implementation(libs.material)

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Supabase Realtime & Postgres for Collaborative Jams
    val supabaseVersion = "2.2.3"
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:realtime-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    
    // Ktor Client for Supabase
    val ktorVersion = "2.3.9"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
