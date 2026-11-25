// Android application build settings, API URL injection, and app dependencies.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

/** Escapes a property value so Gradle can emit it as a valid BuildConfig string. */
fun asBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "")}\""

val apiBaseUrl = localProperties.getProperty("API_BASE_URL", "")
val firebaseApiKey = localProperties.getProperty("FIREBASE_API_KEY", "")
val firebaseAppId = localProperties.getProperty("FIREBASE_APP_ID", "")
val firebaseProjectId = localProperties.getProperty("FIREBASE_PROJECT_ID", "")
val firebaseSenderId = localProperties.getProperty("FIREBASE_SENDER_ID", "")

android {
    namespace = "com.nearnest"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.nearnest"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", asBuildConfigString(apiBaseUrl))
        buildConfigField("String", "FIREBASE_API_KEY", asBuildConfigString(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", asBuildConfigString(firebaseAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", asBuildConfigString(firebaseProjectId))
        buildConfigField("String", "FIREBASE_SENDER_ID", asBuildConfigString(firebaseSenderId))
        if (firebaseAppId.isNotBlank()) resValue("string", "google_app_id", firebaseAppId)
        if (firebaseApiKey.isNotBlank()) resValue("string", "google_api_key", firebaseApiKey)
        if (firebaseProjectId.isNotBlank()) resValue("string", "project_id", firebaseProjectId)
        if (firebaseSenderId.isNotBlank()) resValue("string", "gcm_defaultSenderId", firebaseSenderId)
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.maplibre.gl:android-sdk:13.7.1")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
