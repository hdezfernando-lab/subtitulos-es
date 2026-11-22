plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "mx.hdez.subtitulos"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.hdez.subtitulos"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Reconocimiento de voz offline (inglés)
    implementation("com.alphacephei:vosk-android:0.3.47")
    // Traducción offline inglés -> español
    implementation("com.google.mlkit:translate:17.0.3")
}
