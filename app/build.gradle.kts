plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nytefall.nytegallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nytefall.nytegallery"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // FLAG_SECURE stays on (screenshots blocked) in every build except the "screenshot" build type below.
        buildConfigField("boolean", "SCREENSHOTS_ALLOWED", "false")
    }

    buildTypes {
        // For Play Store listing screenshots only: a separate app ("Nyte Gallery (screenshots)",
        // com.nytefall.nytegallery.shots) with its own empty vault and screenshots allowed. Debug-signed.
        create("screenshot") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".shots"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "SCREENSHOTS_ALLOWED", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
