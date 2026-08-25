import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "zw.co.donnclab.calltape"
    compileSdk = 37

    defaultConfig {
        applicationId = "zw.co.donnclab.calltape"
        minSdk = 28
        targetSdk = 37
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose BOM & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // ViewModel & Coroutines for In-Memory Repository
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Vosk Offline Speech-to-Text & JNA (Required for Vosk C++ libs)
    implementation(libs.vosk.android) {
        artifact {
            type = "aar"
        }
    }
    implementation(libs.jna) {
        artifact {
            type = "aar"
        }
    }

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Generate UUID files for Vosk models so they unpack correctly
tasks.register("genVoskUuid") {
    description = "Vosk storage service quick fix. (Issue #846 and Issue #522)"
    doLast {
        val uuidString = UUID.randomUUID().toString()

        // Define paths to your asset folders
        val mainModelDir = file("src/main/assets/model-en-us")
        val spkModelDir = file("src/main/assets/spk-model")

        // Create UUID file for the main speech model
        if (mainModelDir.exists()) {
            val uuidFile = file("$mainModelDir/uuid")
            uuidFile.writeText(uuidString)
        }

        // Create UUID file for the speaker model
        if (spkModelDir.exists()) {
            val spkUuidFile = file("$spkModelDir/uuid")
            spkUuidFile.writeText(uuidString)
        }
    }
}

// Ensure the UUIDs are generated right before the app compiles
tasks.named("preBuild") {
    dependsOn("genVoskUuid")
}