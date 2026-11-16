plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiUrlRelease = providers.gradleProperty("cafetin.apiUrl").get()

android {
    namespace = "pe.edu.ucv.cafetin.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "pe.edu.ucv.cafetin.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // El emulador ve el localhost de la PC como 10.0.2.2. Solo aquí se permite HTTP (ver src/debug).
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "API_URL", "\"$apiUrlRelease\"")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
