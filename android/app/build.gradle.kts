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
        // 0.3.0 is the v4 launch set; codes 23–29 are left for the 0.2.x line that continues on another branch.
        versionCode = 30
        versionName = "0.3.0"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildTypes {
        // Side by side with 0.2.x on one phone: `-Pkompara.appIdSuffix=.v4` gives the debug build its own
        // application id and the label "Kompara v4". Without the property nothing changes.
        debug {
            providers.gradleProperty("kompara.appIdSuffix").orNull?.let { suffix ->
                applicationIdSuffix = suffix
                manifestPlaceholders["appLabel"] = "Kompara v4"
            }
        }
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
        // BuildConfig.DEBUG gates the debug-premium override out of release (GateModule).
        buildConfig = true
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
    implementation(project(":ocr"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // ShareImportActivity (PR-D3) drains shared files off the main thread via lifecycleScope.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // The app wires the offer-simulator route (:overlay) into the shared nav host (:ui), so it needs
    // the navigation API on its own classpath (KomparaApp's registerExtraDestinations lambda type).
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // WorkManager + Hilt integration: the app supplies the HiltWorkerFactory and schedules the
    // background workers — the telemetry upload worker (B-034) and the periodic OTA parser-config
    // refresh (B-033).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
