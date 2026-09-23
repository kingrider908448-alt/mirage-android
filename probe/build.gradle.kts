plugins { id("com.android.application") }

android {
    namespace = "dev.ghostviki.probe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.ghostviki.probe"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0-profiles100"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    lint { abortOnError = true }
}
