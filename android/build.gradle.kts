plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

subprojects {
    afterEvaluate {
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
    }
}
