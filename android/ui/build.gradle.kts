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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Verified public destinations are release configuration, never placeholder links.
        for ((property, field) in listOf("kompara.premiumTermsUrl" to "PREMIUM_TERMS_URL", "kompara.premiumPrivacyUrl" to "PREMIUM_PRIVACY_URL")) {
            val url = providers.gradleProperty(property).getOrElse("")
            require(url.isEmpty() || url.matches(Regex("https://[A-Za-z0-9./_-]+")))
            buildConfigField("String", field, "\"$url\"")
        }
        // Populate only after the destination has been verified for a release (S14 / TD-054).
        val support = providers.gradleProperty("kompara.supportWhatsApp").getOrElse("")
        require(support.isEmpty() || support.matches(Regex("[1-9][0-9]{7,14}")))
        val deletion = providers.gradleProperty("kompara.accountDeletionUrl").getOrElse("")
        require(deletion.isEmpty() || deletion == "https://kompara.mx/borrar")
        buildConfigField("String", "SUPPORT_WHATSAPP", "\"$support\"")
        buildConfigField("String", "ACCOUNT_DELETION_URL", "\"$deletion\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates the debug-only "Premium (prueba)" toggle in Ajustes (the way to
        // test the paywall before Play Billing works) so it's stripped from release builds.
        buildConfig = true
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

    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric + a real Activity: the paywall billing-flow launch test (B-050) and the share-card
    // bitmap render smoke test (B-055) both need real android.* on the JVM without an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}

// Explicit baseline recording only; normal tests compare reviewed, computed fixture PNGs.
tasks.withType<Test>().configureEach {
    systemProperty("v3.recordScreenshots", providers.gradleProperty("v3.recordScreenshots").getOrElse("false"))
    // v4 baselines record apart (docs/v4-simple-launch-plan.md §2), so recording one set never rewrites the other.
    systemProperty("v4.recordScreenshots", providers.gradleProperty("v4.recordScreenshots").getOrElse("false"))
    inputs.files(fileTree("src/test/snapshots/v4"))
    inputs.dir("src/test/snapshots/v3")
    outputs.dir(layout.buildDirectory.dir("outputs/v3-screenshots"))
    outputs.dir(layout.buildDirectory.dir("outputs/v4-screenshots"))
}
