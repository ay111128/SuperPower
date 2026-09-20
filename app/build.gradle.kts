plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android {
    namespace = "com.paperbox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paperbox.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "0.2.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 服务器地址（用 IP 直连绕过域名 TLS 拦截）
        buildConfigField("String", "API_BASE_URL", "\"https://101.133.169.230\"")
    }

    signingConfigs {
        create("release") {
            // file() resolves relative to app/ module dir, so use just the filename
            storeFile = file(System.getenv("SIGNING_STORE_FILE")?.substringAfterLast("/") ?: "release-key.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.53.1")
    kapt("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Moshi (JSON)
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Splash
    implementation("androidx.core:core-splashscreen:1.0.1")
}

kapt {
    correctErrorTypes = true
}
