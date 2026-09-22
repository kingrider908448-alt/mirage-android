plugins { id("com.android.application") }

android {
    namespace = "dev.mirage.probe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.mirage.probe"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
