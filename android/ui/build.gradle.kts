plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // The paywall viewmodel test launches the (fake) billing flow with a real Activity (B-050);
            // the share-card renderer smoke test (B-055) draws to a real android.graphics.Bitmap/Canvas.
            // Robolectric provides both on the JVM without an emulator. The rest is pure-JVM.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // :ui consumes persistence and metrics; it must NOT depend on :capture internals.
    implementation(project(":data"))
    implementation(project(":metrics"))
    // :sync for the percentile/benchmarks repositories (B-046) and the FiscalConfigRepository (B-051
    // IMSS threshold remote config); :billing for the premium capability flags that gate the
    // percentile UI (B-049 → B-050). :sync depends on :data/:metrics/:parsers only — never on :ui —
    // so this stays acyclic.
    implementation(project(":sync"))
    implementation(project(":billing"))
    implementation(libs.kotlinx.coroutines.core)
    // androidx.core: NotificationCompat / ContextCompat for the service-health watchdog (B-036).
    implementation(libs.androidx.core.ktx)
    // DataStore: the onboarding funnel counters share the app preferences store (B-036).
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager + Hilt integration: the month-end IMSS summary worker (B-051).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric + a real Activity: the paywall billing-flow launch test (B-050) and the share-card
    // bitmap render smoke test (B-055) both need real android.* on the JVM without an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
