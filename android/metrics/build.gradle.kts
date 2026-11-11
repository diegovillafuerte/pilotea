plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "mx.kompara.metrics"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":data"))
    implementation(libs.kotlinx.coroutines.core)
    // RoomDatabase.withTransaction — the rollup replace (delete+upsert) must commit atomically.
    implementation(libs.androidx.room.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The v4 repositories and OfferRecorder against a real Room database and DataStore (as :data does).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.datastore.preferences)
}
