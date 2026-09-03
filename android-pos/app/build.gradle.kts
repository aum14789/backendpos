plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "sun.clientpos"
    compileSdk = 36

    defaultConfig {
        applicationId = "sun.clientpos"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend connection config — override via gradle.properties or -P flags
        buildConfigField("String", "BACKEND_BASE_URL", "\"${project.findProperty("BACKEND_BASE_URL") ?: "http://10.0.2.2:8080/api/v1/"}\"")
        buildConfigField("String", "DEVICE_ID", "\"${project.findProperty("DEVICE_ID") ?: "pos-device-001"}\"")
        buildConfigField("String", "BRANCH_ID", "\"${project.findProperty("BRANCH_ID") ?: "branch-001"}\"")
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room Local Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager Background Sync
    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking (Retrofit & Gson)
    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Cryptography & BCrypt for secure offline PIN verification
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}