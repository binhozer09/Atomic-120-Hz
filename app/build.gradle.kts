plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atomic120hz"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.atomic120hz"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Bouncy Castle ships the same OSGi manifest in multiple JARs.
    // It is metadata, not required at Android runtime.
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}


dependencies {
    // Kadb 2.1.5 alinha o protocolo ADB com o AOSP atual.
    implementation("com.flyfishxu:kadb:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
