plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atomic120hz"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.atomic120hz"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ADB client próprio do Atomic: pareamento TLS e shell sem depender do app Shizuku.
    // Kadb 1.2.1 mantém compatibilidade com compileSdk 35 / AGP 8.7.x.
    implementation("com.flyfishxu:kadb:1.2.1")
    // runBlocking usado no handshake de pareamento ADB.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
