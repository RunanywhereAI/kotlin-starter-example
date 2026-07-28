import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hfTokenForBuild = (localProperties.getProperty("hf.token") ?: "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.runanywhere.kotlin_starter_example"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.runanywhere.kotlin_starter_example"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // QHexRT + QAIRT ship arm64-only native slices. Keep a single ABI so
        // Hexagon skels and librac_backend_qhexrt.so always travel together.
        ndk {
            abiFilters += "arm64-v8a"
        }

        // Optional gated-HNPU downloads (see local.properties.example).
        buildConfigField("String", "HF_TOKEN", "\"$hfTokenForBuild\"")

        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    packaging {
        jniLibs {
            // Large QAIRT / QHexRT .so files exceed the compressed-JNI limit on
            // some devices unless they are extracted at install time.
            useLegacyPackaging = true
            // AGP stripDebugDebugSymbols was collapsing local QHexRT builds from
            // ~41MB → ~2MB and dropping engine symbols. Keep natives intact.
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Default: Maven Central. For local QHexRT engine fixes (e.g. Kitten live-window
// stitching), stage libs/runanywhere-qhexrt.aar then pass
// -Prunanywhere.useLocalQhexrt=true.
val useLocalQhexrt = providers.gradleProperty("runanywhere.useLocalQhexrt")
    .map { it.toBoolean() }
    .orElse(false)
    .get()

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // RunAnywhere SDK
    implementation(libs.runanywhere.sdk)
    implementation(libs.runanywhere.llamacpp)
    implementation(libs.runanywhere.onnx)
    if (useLocalQhexrt) {
        val localQhexrt = rootProject.file("libs/runanywhere-qhexrt.aar")
        require(localQhexrt.isFile) {
            "Missing ${localQhexrt.path}. Build/stage the QHexRT AAR or omit " +
                "-Prunanywhere.useLocalQhexrt."
        }
        implementation(files(localQhexrt))
    } else {
        implementation(libs.runanywhere.qhexrt)
    }
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
