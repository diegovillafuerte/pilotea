plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.overlay"
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
            // A handful of tests touch android.* types (LayoutParams gravity flags); Robolectric
            // gives us those on the JVM without an emulator.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // The overlay maps a parsed OfferCard -> TripOffer, runs the engine, and renders the verdict in
    // the shared design language. It is attached to the window by :capture's AccessibilityService.
    implementation(project(":capture"))
    implementation(project(":parsers"))
    implementation(project(":metrics"))
    implementation(project(":data"))
    implementation(project(":ui"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // OverlayPrefs persists the chip position in the shared preferences DataStore.
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // The in-app offer simulator screen (B-037) renders the verdict chip inline and needs the
    // material icons + Compose tooling preview the floating overlay path didn't.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Manual LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner wiring for a ComposeView
    // hosted in a Service (no Activity to inherit these from).
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    // The simulator's ViewModel (viewModelScope), reactive Compose collection, and nav-graph entry.
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
