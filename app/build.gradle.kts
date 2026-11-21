import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

val signingProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** A signing value from the environment (CI) or keystore.properties (local), else null. */
fun signingSetting(property: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProperties.getProperty(property)?.takeIf { it.isNotBlank() }

/**
 * Sign-in and cloud backup settings come from google-services.json (kept out of git; CI
 * writes it from a secret), read here instead of by Google's plugin so that builds without it
 * still work, just without sign-in.
 */
val firebase: Map<String, String> = run {
    // read through Gradle so that a new google-services.json is noticed (configuration cache)
    val text = listOf(rootProject.layout.projectDirectory.file("google-services.json"), layout.projectDirectory.file("google-services.json"))
        .firstNotNullOfOrNull { providers.fileContents(it).asText.orNull }
        ?: return@run emptyMap()
    @Suppress("UNCHECKED_CAST")
    fun Any?.obj() = this as? Map<String, Any?> ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    fun Any?.list() = this as? List<Any?> ?: emptyList()
    val root = groovy.json.JsonSlurper().parseText(text).obj()
    val project = root["project_info"].obj()
    val client = root["client"].list().map { it.obj() }.firstOrNull {
        it["client_info"].obj()["android_client_info"].obj()["package_name"] == "com.example.mediminder"
    } ?: return@run emptyMap()
    val oauth = client["oauth_client"].list().map { it.obj() } +
        client["services"].obj()["appinvite_service"].obj()["other_platform_oauth_client"].list().map { it.obj() }
    mapOf(
        "API_KEY" to client["api_key"].list().firstOrNull().obj()["current_key"]?.toString().orEmpty(),
        "APP_ID" to client["client_info"].obj()["mobilesdk_app_id"]?.toString().orEmpty(),
        "PROJECT_ID" to project["project_id"]?.toString().orEmpty(),
        "SENDER_ID" to project["project_number"]?.toString().orEmpty(),
        "STORAGE_BUCKET" to project["storage_bucket"]?.toString().orEmpty(),
        "WEB_CLIENT_ID" to (oauth.firstOrNull { it["client_type"].toString() == "3" }?.get("client_id")?.toString().orEmpty()),
    )
}

android {
    defaultConfig {
        for (key in listOf("API_KEY", "APP_ID", "PROJECT_ID", "SENDER_ID", "STORAGE_BUCKET", "WEB_CLIENT_ID")) {
            buildConfigField("String", "FIREBASE_$key", "\"${firebase[key].orEmpty()}\"")
        }
    }
    // The privacy policy and terms are written once, in docs/legal, and shipped inside the app too
    sourceSets.getByName("main").assets.srcDir("../docs/legal")
    namespace = "com.example.mediminder"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.mediminder"
        minSdk = 24
        targetSdk = 36
        versionCode = 30
        versionName = "1.0.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Real phones are ARM; the x86 copies of the scanner's native code (22 MB) only serve emulators
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // Release signing never lives in this repository: it comes from environment variables (the
    // release workflow) or a git-ignored keystore.properties (a developer's PC). Without either,
    // release builds are simply unsigned. tools/sign_release.py then adds the key rotation.
    signingConfigs {
        create("release") {
            signingSetting("storeFile", "KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = signingSetting("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = signingSetting("keyAlias", "KEY_ALIAS")
            keyPassword = signingSetting("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingSetting("storeFile", "KEYSTORE_PATH") != null) signingConfigs.getByName("release") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (LocalDate etc.) is used everywhere; this makes it work on Android 7.x (API 24–25)
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    // The medicine catalogue is a SQLite file; keep it uncompressed so Room can copy it quickly
    androidResources {
        noCompress += "db"
        // Per-app language picker (Android 13+ system settings)
        generateLocaleConfig = true
    }

    testOptions {
        // Robolectric tests open the real databases (including the bundled catalogue asset)
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Room writes its schema here; the migration test and the catalogue builder rely on it
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  // Reliable photo orientation on every Android version (the platform one has old bugs)
  implementation("androidx.exifinterface:exifinterface:1.3.7")
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat) // per-app language on Android 7–12
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Robolectric (real SQLite on the JVM)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.room.testing)
  // Robolectric UI smoke test of the real app (Compose + Hilt)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.compiler)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  // Background safety net that re-arms alarms (OEM battery savers can drop them)
  implementation(libs.androidx.work.runtime)

  // Backup / restore file format
  implementation(libs.kotlinx.serialization.json)

  // ML Kit
  implementation(libs.mlkit.text.recognition)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // Image Cropper
  implementation(libs.android.image.cropper)

  // Coil
  implementation("io.coil-kt:coil-compose:2.6.0")

  // Sign-in and cloud backup (optional; off when the build has no Firebase settings)
  implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  implementation("androidx.credentials:credentials:1.3.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  // Firebase brings Guava at run time; CameraX needs its ListenableFuture when compiling too
  implementation("com.google.guava:guava:33.3.1-android")
}
