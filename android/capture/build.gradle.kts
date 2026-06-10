plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.capture"
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
            // Robolectric needs merged resources (the accessibility-service XML/strings) and the
            // android.* classes on the unit-test classpath.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":parsers"))
    implementation(project(":metrics"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Runtime only: serializes the `:parsers` @Serializable ParserSnapshot for fixture reports.
    // No serialization compiler plugin needed here — the serializer is generated in :parsers.
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric gives us a real android.graphics.Rect + AccessibilityNodeInfo on the JVM so the
    // flattener/model tests run in CI without an emulator (instrumented tests are out of scope).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
