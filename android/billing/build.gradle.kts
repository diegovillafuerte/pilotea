plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.billing"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // One test launches the (fake) billing flow with a real Activity; Robolectric provides
            // it on the JVM without an emulator. The rest of the suite is pure-JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    // Play Billing (subscriptions). Wrapped behind BillingClientFacade so the lifecycle
    // logic and EntitlementRepository stay testable with a pure-JVM fake (no device/Play).
    implementation(libs.billing.ktx)

    // Last-known entitlement persistence (offline grace) — same preferences DataStore the rest
    // of the app uses. EntitlementStore reads/writes its own keys in a dedicated file.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // A real Activity for the launchBillingFlow account-linking test (no emulator).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
