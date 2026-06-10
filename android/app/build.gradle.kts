plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.kompara.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // The app wires together all feature modules.
    implementation(project(":capture"))
    implementation(project(":parsers"))
    implementation(project(":overlay"))
    implementation(project(":metrics"))
    implementation(project(":data"))
    implementation(project(":sync"))
    implementation(project(":ui"))
    implementation(project(":billing"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt worker factory for the telemetry upload worker (B-034).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
