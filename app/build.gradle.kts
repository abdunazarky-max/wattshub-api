plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.hyzin.whtsappclone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hyzin.wattshub"
        minSdk = 23 // Required by Firebase Auth and modern dependencies
        targetSdk = 35 // Maximum available SDK

        versionCode = 31
        versionName = "3.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            // Support all common real-world phone architectures
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
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
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.android.gms:play-services-auth:21.5.1")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("io.coil-kt:coil-compose:2.4.0")

    // CameraX dependencies
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // WebRTC SDK
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Socket.io for Real-Time Pipeline
    implementation("io.socket:socket.io-client:2.1.0")

    // Play Integrity API
    implementation("com.google.android.play:integrity:1.4.0")
    
    // Fix for ListenableFuture access issues
    implementation("com.google.guava:guava:33.1.0-android")
    implementation(libs.androidx.concurrent.futures.ktx)

    // Video Playback (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Google Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.6.0")
}

// 🛠️ Task to automatically fix broken image assets (Renames JPEG-formatted PNGs to fix signatures)
tasks.register("fixImageAssets") {
    doFirst {
        val resDir = File(projectDir, "src/main/res")
        val filesToHandle = listOf(
            "drawable-nodpi/app_logo.png",
            "drawable/chat_bg_dark.png",
            "drawable-nodpi/chat_bg_dark.png",
            "drawable/whatsapp_3d_emblem.png"
        )
        filesToHandle.forEach { path ->
            val file = File(resDir, path)
            if (file.exists()) {
                if (path.contains("app_logo.png")) {
                    file.delete()
                    println("Deleted $file")
                } else {
                    val newFile = File(resDir, path.replace(".png", ".jpg"))
                    if (file.renameTo(newFile)) {
                        println("Renamed $file to $newFile")
                    }
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("fixImageAssets")
}

tasks.register("testClasses") {
    description = "Bridge task for legacy testClasses"
    group = "verification"
    dependsOn(tasks.matching { it.name.contains("UnitTest") && it.name.contains("Classes") })
}