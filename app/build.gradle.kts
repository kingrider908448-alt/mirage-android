plugins { id("com.android.application") }

android {
    namespace = "dev.ghostviki.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.ghostviki.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0-privacy"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":core"))
    compileOnly("de.robv.android.xposed:api:82")
}
