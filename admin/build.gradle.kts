import java.util.Properties

// Rental Solution Admin — private Super Admin app (installed from an APK, not published on Play).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.genusit.prms.admin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.genusit.prms.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        val apiUrl = (project.findProperty("admin.apiBaseUrl") as String?) ?: "https://rentalsolution.co.in/api/v1/"
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            val debugUrl = (project.findProperty("admin.apiBaseUrlDebug") as String?)?.takeIf { it.isNotBlank() }
            if (debugUrl != null) buildConfigField("String", "API_BASE_URL", "\"$debugUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed after the build with the admin app's own key (not the Play upload key).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
