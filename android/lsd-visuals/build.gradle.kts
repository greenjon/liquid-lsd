// lsd-visuals: the Liquid LSD visuals for Android apps (the web TV's engine in a WebView, content updates,
// the audio feed). The source of truth is liquid-lsd's android/lsd-visuals/; apps get a copy of this folder,
// with the web engine as assets, from liquid-lsd's scripts/sync_lsd_visuals.sh. Don't edit the copy in an app.
//
// The host build supplies the plugins (com.android.application on its classpath brings com.android.library;
// the Kotlin Android and Compose compiler plugins by id), so this file carries no plugin versions.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.liquidlsd.visuals"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")
}
