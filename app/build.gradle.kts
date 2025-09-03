plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    // Removed Firebase Google Services plugin since no Firebase anymore
    // id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
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
   // implementation("io.github.jan-tennert.supabase:auth-kt:3.2.2")

    // Testing
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    implementation("io.github.jan-tennert.supabase:supabase-kt:2.0.0")

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

    implementation("io.github.jan-tennert.supabase:storage-kt:3.2.2")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.2.2")    // PostgREST
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.0.0")

    //implementation("io.github.jan-tennert.supabase:supabase-kt:3.2.2")
    // OR you can use the all-in-one dependency:
    implementation("io.github.jan-tennert.supabase:supabase-kt:2.0.0")

    // Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
// Make sure you have this
    implementation("com.facebook.shimmer:shimmer:0.5.0")

}
