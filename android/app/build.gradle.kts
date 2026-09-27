plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vtuber.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vtuber.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
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
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
        force("androidx.activity:activity:1.8.2")
        force("androidx.fragment:fragment:1.6.2")
        force("androidx.lifecycle:lifecycle-runtime:2.7.0")
        force("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
        force("androidx.lifecycle:lifecycle-viewmodel:2.7.0")
        force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
        force("androidx.lifecycle:lifecycle-common:2.7.0")
        force("androidx.appcompat:appcompat:1.6.1")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
