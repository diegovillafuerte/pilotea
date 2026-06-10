plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.sync"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL — overridable per build type. The debug default points
        // at the loopback alias the Android emulator uses to reach the host
        // machine (10.0.2.2). Release ships the real API host.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
    }

    buildTypes {
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.kompara.mx\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":data"))
    // :parsers for the ParserSpec/SpecBundle model + signature verifier (B-033 OTA specs) and the
    // ParserSnapshot deserialized when rebuilding a fixture-report payload (B-034).
    implementation(project(":parsers"))
    // :metrics for the B-039 RollupRecomputer (turns the cost profile into a marginal $/km for net).
    implementation(project(":metrics"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Ktor HTTP client (OkHttp engine) for the thin backend.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Anonymous device id + session token persistence.
    implementation(libs.androidx.datastore.preferences)

    // WorkManager: periodic + on-demand telemetry upload (B-034) and periodic OTA parser-config
    // refresh (B-033).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    // Fake HTTP transport for ApiClient/AuthRepository tests.
    testImplementation(libs.ktor.client.mock)
}
