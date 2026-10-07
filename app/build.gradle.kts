plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Compute dynamic build number from GitHub Actions or project property
val buildNumber: Int = (
    System.getenv("GITHUB_RUN_NUMBER")
        ?: project.findProperty("BUILD_NUMBER")?.toString()
        ?: "1"
).toIntOrNull() ?: 1

val appVersionCode: Int = 1000 + buildNumber
val appVersionName: String = "1.0.$buildNumber"

android {
    namespace = "com.rdm.remote.desktop.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rdm.remote.desktop.manager"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")
        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$appVersionCode")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootProject.projectDir}/keystore/release.keystore")
            storePassword = "rdm_release_key_2026"
            keyAlias = "rdmkey"
            keyPassword = "rdm_release_key_2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // AndroidX Core, Activity & Lifecycle
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Jetpack Compose UI & Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // JSON Parser for Backup / Export / Import
    implementation("com.google.code.gson:gson:2.10.1")

    // Tooling & Testing
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
