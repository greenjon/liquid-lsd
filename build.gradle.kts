plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "llm.slop"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.3"
val imguiVersion = "1.86.11"

// Detect OS for native libraries
val lwjglNatives = "natives-linux"

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // LWJGL - Core
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-openal")
    implementation("org.lwjgl", "lwjgl-stb")

    // LWJGL - Natives
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = lwjglNatives)

    // ImGui
    implementation("io.github.spair", "imgui-java-binding", imguiVersion)
    implementation("io.github.spair", "imgui-java-lwjgl3", imguiVersion)
    implementation("io.github.spair", "imgui-java-$lwjglNatives", imguiVersion)

    // JACK Audio (Linux only - will add fallbacks later)
    implementation("org.jaudiolibs:jnajack:1.4.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass.set("llm.slop.spirals.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaExec> {
    // Enable assertions
    jvmArgs("-ea")
}
