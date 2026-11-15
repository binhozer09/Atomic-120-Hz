plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.atomic120hz"
    compileSdk = 35
    defaultConfig { applicationId = "com.atomic120hz"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    dependencies { implementation("dev.rikka.shizuku:api:12.2.0"); implementation("dev.rikka.shizuku:provider:12.2.0") }
}
