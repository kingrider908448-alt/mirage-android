plugins { id("com.android.application") }

android {
    namespace = "dev.mirage.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.mirage.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":core"))
    compileOnly("de.robv.android.xposed:api:82")
}
