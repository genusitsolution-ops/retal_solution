import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing comes ONLY from the project owner's own keystore.properties (never committed)
// or from CI secrets. No signing credentials are included in this project.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.genusit.prms"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.genusit.prms"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.5.0"
        val apiUrl = (project.findProperty("prms.apiBaseUrl") as String?) ?: "https://www.genusitsolution.com/rentalsolutionfinal/api/v1/"
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only the three supported app languages are packaged
        resourceConfigurations += listOf("en", "hi", "mr")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "PRMS_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "PRMS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PRMS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "PRMS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            val debugUrl = (project.findProperty("prms.apiBaseUrlDebug") as String?)?.takeIf { it.isNotBlank() }
            if (debugUrl != null) {
                buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
                // Emulator/LAN addresses do not work on a real phone – say so loudly.
                if (Regex("://(10\\.0\\.2\\.2|127\\.0\\.0\\.1|localhost)").containsMatchIn(debugUrl)) {
                    logger.warn("WARNING: this DEBUG APK will call $debugUrl – that only works in the Android emulator, NOT on a phone.")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (dates on bills, receipts, reports) only exists natively from Android 8.0;
        // desugaring makes it work on Android 7.0/7.1 too (minSdk 24).
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(project(":data"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // on-device OCR for tenant documents (bundled Latin model: works offline, nothing leaves the phone)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.exifinterface)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
