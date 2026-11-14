import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = rootProject.file(".signing/release.properties")
val releaseKey = rootProject.file(".signing/release.jks")

android {
    namespace = "com.ccbsport.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ccbsport.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.5.1"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    if (signingProperties.isFile && releaseKey.isFile) {
        val credentials = Properties().apply { signingProperties.inputStream().use(::load) }
        val localRelease = signingConfigs.create("localRelease") {
            storeFile = releaseKey
            storePassword = credentials.getProperty("storePassword")
            keyAlias = "ccbsport"
            keyPassword = credentials.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = localRelease
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha07")
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")
    implementation("org.mapsforge:mapsforge-themes:0.25.0")
}
