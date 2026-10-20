plugins {
    id("com.android.application")
    // Kotlin viene integrado en AGP 9: no se aplica org.jetbrains.kotlin.android
}

android {
    namespace = "com.iot.canjeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iot.canjeapp"
        // API 26 (Android 8.0): requerido por PBKDF2WithHmacSHA256 para el hash de contraseñas
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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

    // AGP 9 exige JDK 17. El jvmTarget de Kotlin toma este mismo valor
    // automáticamente (ya no se usa el bloque kotlinOptions).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // lifecycleScope: tareas de BD/criptografía fuera del hilo de UI
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
