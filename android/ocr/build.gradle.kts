plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "mx.kompara.ocr"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // BuildConfig.DEBUG gates the fixture recorder (screen text must never persist in release).
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":capture"))
    implementation(project(":data"))
    implementation(project(":parsers"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
}
