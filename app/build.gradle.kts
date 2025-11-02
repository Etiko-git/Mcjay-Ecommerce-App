plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize") // Add this line
    // Removed Firebase Google Services plugin since no Firebase anymore
    // id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.solih.mcjay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.solih.mcjay"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ✅ Remove this old jvmTarget block
    // kotlinOptions {
    //     jvmTarget = "11"
    // }

    // ✅ Add the new one
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Supabase Kotlin SDK
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.cio)
    implementation("io.github.jan-tennert.supabase:auth-kt:3.2.2")



    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.0.0")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // CircleImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    implementation("io.coil-kt:coil:2.4.0")


    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle (for lifecycleScope in Fragments)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
// For JSON parsing

    implementation("com.facebook.shimmer:shimmer:0.5.0")

    implementation("androidx.fragment:fragment-ktx:1.8.2")
    // Lifecycle (for lifecycleScope in Fragments)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")

// Use the latest version
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
// For JSON parsing

    implementation("androidx.preference:preference-ktx:1.2.1") // Or the latest version

    // Lottie for animations
    implementation("com.airbnb.android:lottie:6.1.0")
// For QR code generation (optional)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
// For HTTP requests (if not already using)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("androidx.core:core-ktx:1.12.0")
// For coroutines/tasks


    // For coroutines support on Play Services Tasks (fixes 'tasks' and 'await')
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
// Use latest version; 1.8.1 as of Oct 2025
// Ensure these are also present for location and coroutines
    implementation("com.google.android.gms:play-services-location:21.3.0")
// Latest for FusedLocationClient
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.webkit:webkit:1.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")


    implementation("com.stripe:stripe-android:20.40.0")

}
