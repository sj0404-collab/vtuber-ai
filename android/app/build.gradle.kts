import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val cubismCoreFile = file("src/main/assets/avatar/live2dcubismcore.min.js")
val cubismCoreUrls = listOf(
    "https://cubism.live2d.com/sdk-web/cubismcore/live2dcubismcore.min.js",
    "https://cdn.jsdelivr.net/gh/Live2D/CubismWebFramework@develop/Core/live2dcubismcore.min.js"
)

val downloadCubismCore by tasks.registering {
    description = "Downloads the Live2D Cubism Core runtime into the avatar assets"
    outputs.upToDateWhen { cubismCoreFile.exists() && cubismCoreFile.length() > 50 * 1024 }
    doLast {
        if (cubismCoreFile.exists() && cubismCoreFile.length() > 50 * 1024) {
            logger.lifecycle("Cubism Core уже на месте: ${cubismCoreFile.name}")
            return@doLast
        }
        cubismCoreFile.parentFile.mkdirs()
        var saved = false
        for (url in cubismCoreUrls) {
            runCatching {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.getInputStream().use { input ->
                    cubismCoreFile.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess {
                if (cubismCoreFile.length() > 50 * 1024) {
                    logger.lifecycle("Cubism Core скачан: $url")
                    saved = true
                } else {
                    cubismCoreFile.delete()
                }
            }.onFailure {
                logger.warn("Не удалось скачать Cubism Core с $url: ${it.message}")
                cubismCoreFile.delete()
            }
            if (saved) break
        }
        if (!saved) {
            logger.warn(
                "Live2D Cubism Core недоступен: аватар не отрендерится. " +
                    "Скачайте вручную с https://www.live2d.com/en/sdk/download/web/ " +
                    "и положите в ${cubismCoreFile.absolutePath}"
            )
        }
    }
}

android {
    namespace = "com.vtuber.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vtuber.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "3.0.0"
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

tasks.named("preBuild") {
    dependsOn(downloadCubismCore)
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
