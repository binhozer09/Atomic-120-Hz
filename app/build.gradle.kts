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
        versionCode = 5
        versionName = "2.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
}
