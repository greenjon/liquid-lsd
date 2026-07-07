import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "llm.slop"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.3"
val imguiVersion = "1.86.11"

object BuildProjectConfig {
    const val PRODUCT_NAME = "Spirals"
    const val PRODUCT_SLUG = "spirals"
    const val APP_ARTIFACT_ID = "$PRODUCT_SLUG-desktop"
    const val PACKAGE_SEGMENT = PRODUCT_SLUG
    const val LAUNCHER_JAR_NAME = "$APP_ARTIFACT_ID-all.jar"
    const val DISTRIBUTION_DIR_NAME = APP_ARTIFACT_ID

    fun distributionName(platform: String) = "$DISTRIBUTION_DIR_NAME-$platform"
    fun archiveName(platform: String) = "${distributionName(platform)}.zip"
}

object BuildTaskConfig {
    const val APP_MAIN_CLASS = "llm.slop.${BuildProjectConfig.PACKAGE_SEGMENT}.MainKt"
    const val UI_LAB_WINDOW = "1440x900"
    const val UI_LAB_SCREENSHOT_FRAMES = 8
    const val APP_SCREENSHOT_FRAMES = 12
    const val UI_LAB_OUTPUT = "build/ui-lab/ui-lab.png"
    const val RESPONSIVE_OUTPUT_DIR = "build/ui-lab/responsive"
    val commonCaptureArgs = listOf("--no-audio", "--no-watchdog")
}

data class ResponsiveCapturePreset(val name: String, val size: String)

val responsiveCapturePresets = listOf(
    ResponsiveCapturePreset("compact", "900x700"),
    ResponsiveCapturePreset("laptop", "1280x720"),
    ResponsiveCapturePreset("desktop", "1440x900"),
    ResponsiveCapturePreset("wide", "1920x1080")
)

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

    // LWJGL - Natives for all platforms
    val lwjglNativesList = listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64", "natives-linux-arm64")
    lwjglNativesList.forEach { platform ->
        runtimeOnly("org.lwjgl", "lwjgl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = platform)
        runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = platform)
    }

    // ImGui
    implementation("io.github.spair", "imgui-java-binding", imguiVersion)
    implementation("io.github.spair", "imgui-java-lwjgl3", imguiVersion)
    implementation("io.github.spair", "imgui-java-natives-linux", imguiVersion)
    implementation("io.github.spair", "imgui-java-natives-windows", imguiVersion)
    implementation("io.github.spair", "imgui-java-natives-macos", imguiVersion)

    // JACK Audio (Linux only - will add fallbacks later)
    implementation("org.jaudiolibs:jnajack:1.4.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
}

tasks.register<JavaExec>("runUiLab") {
    group = "application"
    description = "Runs ${BuildProjectConfig.PRODUCT_NAME} in deterministic UI lab mode for visual iteration."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
    args(listOf("--ui-lab") + BuildTaskConfig.commonCaptureArgs)
}

tasks.register<JavaExec>("captureUiLab") {
    group = "verification"
    description = "Captures a deterministic UI lab screenshot to build/ui-lab/ui-lab.png."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
    args(
        "--ui-lab",
        "--window=${BuildTaskConfig.UI_LAB_WINDOW}",
        "--screenshot-ui=${BuildTaskConfig.UI_LAB_OUTPUT}",
        "--screenshot-after-frames=${BuildTaskConfig.UI_LAB_SCREENSHOT_FRAMES}"
    )
    args(BuildTaskConfig.commonCaptureArgs)
}

val responsiveUiLabTasks = responsiveCapturePresets.map { preset ->
    tasks.register<JavaExec>("captureUiLab${preset.name.replaceFirstChar { it.uppercase() }}") {
        group = "verification"
        description = "Captures the UI lab at ${preset.size}."
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
        args(
            "--ui-lab",
            "--window=${preset.size}",
            "--screenshot-ui=${BuildTaskConfig.RESPONSIVE_OUTPUT_DIR}/ui-lab-${preset.name}.png",
            "--screenshot-after-frames=${BuildTaskConfig.UI_LAB_SCREENSHOT_FRAMES}"
        )
        args(BuildTaskConfig.commonCaptureArgs)
    }
}

val responsiveAppTasks = responsiveCapturePresets.map { preset ->
    tasks.register<JavaExec>("captureApp${preset.name.replaceFirstChar { it.uppercase() }}") {
        group = "verification"
        description = "Captures the live app shell at ${preset.size}."
        dependsOn("classes")
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
        args(
            "--window=${preset.size}",
            "--screenshot-ui=${BuildTaskConfig.RESPONSIVE_OUTPUT_DIR}/app-${preset.name}.png",
            "--screenshot-after-frames=${BuildTaskConfig.APP_SCREENSHOT_FRAMES}"
        )
        args(BuildTaskConfig.commonCaptureArgs)
    }
}

val captureAppMaximized = tasks.register<JavaExec>("captureAppMaximized") {
    group = "verification"
    description = "Captures the live app shell in an OS-maximized window."
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(BuildTaskConfig.APP_MAIN_CLASS)
    args(
        "--window=maximized",
        "--screenshot-ui=${BuildTaskConfig.RESPONSIVE_OUTPUT_DIR}/app-maximized.png",
        "--screenshot-after-frames=${BuildTaskConfig.APP_SCREENSHOT_FRAMES}"
    )
    args(BuildTaskConfig.commonCaptureArgs)
}

tasks.register("captureResponsiveUiLab") {
    group = "verification"
    description = "Captures UI lab screenshots at compact, laptop, desktop, and wide sizes."
    dependsOn(responsiveUiLabTasks)
}

tasks.register("captureResponsiveApp") {
    group = "verification"
    description = "Captures live app screenshots at compact, laptop, desktop, wide, and maximized sizes."
    dependsOn(responsiveAppTasks + captureAppMaximized)
}

tasks.register("captureResponsiveUi") {
    group = "verification"
    description = "Captures both deterministic UI lab and live app responsive screenshot sets."
    dependsOn("captureResponsiveUiLab", "captureResponsiveApp")
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.exclude("**/ANDROID-REFERENCE/**")
        }
    }
}

tasks.withType<JavaExec> {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
    jvmArgs(
        "-ea",
        "-XX:+UseZGC",
        "-XX:MaxGCPauseMillis=2",
        "-Xms512m",
        "-Xmx2g"
    )
}

val generateDocs = tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates HTML documentation using mkdocs if available."
    inputs.files(fileTree("docs"), "mkdocs.yml")
    outputs.dir("src/main/resources/docs")

    doLast {
        val hasMkdocs = try {
            val pb = ProcessBuilder("mkdocs", "--version")
            val proc = pb.start()
            proc.waitFor() == 0
        } catch (e: java.io.IOException) {
            false
        }

        if (hasMkdocs) {
            project.exec {
                commandLine("mkdocs", "build", "-d", "${project.projectDir}/src/main/resources/docs")
            }
        } else {
            println("WARNING: 'mkdocs' executable not found. Skipping documentation generation, will use existing resource files if present.")
        }
    }
}

tasks.processResources {
    dependsOn(generateDocs)
}

val packageThumbDrive = tasks.register("packageThumbDrive") {
    group = "distribution"
    description = "Downloads platform JREs and packages the application for a thumb drive."
    dependsOn("shadowJar")

    val distDir = file("build/dist")
    val jreCacheDir = file("build/jre-cache")

    inputs.file(tasks.named("shadowJar").map { it.outputs.files.singleFile })
    outputs.dir(distDir)

    doLast {
        distDir.deleteRecursively()
        distDir.mkdirs()
        jreCacheDir.mkdirs()

        // 1. Copy shadowJar
        val jarFile = tasks.named("shadowJar").get().outputs.files.singleFile
        val destJar = file("$distDir/${BuildProjectConfig.LAUNCHER_JAR_NAME}")
        jarFile.copyTo(destJar, overwrite = true)
        println("Copied shadowJar to ${destJar.absolutePath}")

        // 2. Define platforms, their URLs, extension, and JRE folder
        val platforms = listOf(
            Triple("windows-x64", "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse", "zip"),
            Triple("linux-x64", "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jre/hotspot/normal/eclipse", "tar.gz"),
            Triple("linux-aarch64", "https://api.adoptium.net/v3/binary/latest/17/ga/linux/aarch64/jre/hotspot/normal/eclipse", "tar.gz"),
            Triple("macos-x64", "https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jre/hotspot/normal/eclipse", "tar.gz"),
            Triple("macos-aarch64", "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jre/hotspot/normal/eclipse", "tar.gz")
        )

        platforms.forEach { (name, url, ext) ->
            val cacheFile = file("$jreCacheDir/$name.$ext")
            if (!cacheFile.exists()) {
                println("Downloading JRE for $name...")
                try {
                    URL(url).openStream().use { input ->
                        Files.copy(input, cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    println("Successfully downloaded JRE for $name.")
                } catch (e: Exception) {
                    throw GradleException("Failed to download JRE for $name from $url: ${e.message}", e)
                }
            } else {
                println("Using cached JRE for $name.")
            }

            // Extract JRE
            val targetJreDir = file("$distDir/jre/$name")
            targetJreDir.mkdirs()
            println("Extracting JRE for $name to ${targetJreDir.absolutePath}...")

            if (ext == "zip") {
                copy {
                    from(zipTree(cacheFile)) {
                        eachFile {
                            val segments = relativePath.segments
                            if (segments.size > 1) {
                                relativePath = RelativePath(true, *segments.sliceArray(1 until segments.size))
                            } else {
                                exclude()
                            }
                        }
                    }
                    into(targetJreDir)
                    includeEmptyDirs = false
                }
            } else {
                copy {
                    from(tarTree(resources.gzip(cacheFile))) {
                        eachFile {
                            val segments = relativePath.segments
                            if (segments.size > 1) {
                                relativePath = RelativePath(true, *segments.sliceArray(1 until segments.size))
                            } else {
                                exclude()
                            }
                        }
                    }
                    into(targetJreDir)
                    includeEmptyDirs = false
                }
            }
        }

        // 3. Write launchers
        val runWindows = file("$distDir/run-windows.bat")
        runWindows.writeText("""
            @echo off
            setlocal
            cd /d "%~dp0"
            if exist "jre\windows-x64\bin\java.exe" (
                "jre\windows-x64\bin\java.exe" -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            ) else (
                echo Bundled JRE not found. Trying system java...
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            )
            endlocal
        """.trimIndent().replace("\n", "\r\n")) // Windows CRLF

        val runLinux = file("$distDir/run-linux.sh")
        runLinux.writeText("""
            #!/bin/bash
            SCRIPT_DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            cd "${'$'}SCRIPT_DIR"
            
            ARCH="${'$'}(uname -m)"
            if [ "${'$'}ARCH" = "x86_64" ]; then
                JRE_DIR="jre/linux-x64"
            elif [ "${'$'}ARCH" = "aarch64" ] || [ "${'$'}ARCH" = "arm64" ]; then
                JRE_DIR="jre/linux-aarch64"
            else
                echo "Unsupported architecture: ${'$'}ARCH. Trying system java..."
                exec java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            fi

            if [ -f "${'$'}JRE_DIR/bin/java" ]; then
                chmod +x "${'$'}JRE_DIR/bin/java"
                exec "./${'$'}JRE_DIR/bin/java" -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            else
                echo "Bundled JRE not found. Trying system java..."
                exec java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            fi
        """.trimIndent())
        runLinux.setExecutable(true)

        val runMacArm = file("$distDir/run-mac-arm.command")
        runMacArm.writeText("""
            #!/bin/bash
            SCRIPT_DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            cd "${'$'}SCRIPT_DIR"
            if [ -f "jre/macos-aarch64/bin/java" ]; then
                chmod +x jre/macos-aarch64/bin/java
                ./jre/macos-aarch64/bin/java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            else
                echo "Bundled JRE not found. Trying system java..."
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            fi
        """.trimIndent())
        runMacArm.setExecutable(true)

        val runMacIntel = file("$distDir/run-mac-intel.command")
        runMacIntel.writeText("""
            #!/bin/bash
            SCRIPT_DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            cd "${'$'}SCRIPT_DIR"
            if [ -f "jre/macos-x64/bin/java" ]; then
                chmod +x jre/macos-x64/bin/java
                ./jre/macos-x64/bin/java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            else
                echo "Bundled JRE not found. Trying system java..."
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar ${BuildProjectConfig.LAUNCHER_JAR_NAME}
            fi
        """.trimIndent())
        runMacIntel.setExecutable(true)

        println("Launcher scripts generated successfully.")
    }
}

val zipWindows = tasks.register<Zip>("zipWindows") {
    dependsOn(packageThumbDrive)
    archiveFileName.set(BuildProjectConfig.archiveName("windows-x64"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into(BuildProjectConfig.distributionName("windows-x64"))
    from("build/dist") {
        include("run-windows.bat")
        include(BuildProjectConfig.LAUNCHER_JAR_NAME)
        include("jre/windows-x64/**")
    }
}

val zipLinux = tasks.register<Zip>("zipLinux") {
    dependsOn(packageThumbDrive)
    archiveFileName.set(BuildProjectConfig.archiveName("linux-x64"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into(BuildProjectConfig.distributionName("linux-x64"))
    from("build/dist") {
        include("run-linux.sh")
        include(BuildProjectConfig.LAUNCHER_JAR_NAME)
        include("jre/linux-x64/**")
    }
    eachFile {
        if (name == "run-linux.sh" || path.endsWith("/bin/java")) {
            mode = 493 // 0755 in octal
        }
    }
}

val zipLinuxArm = tasks.register<Zip>("zipLinuxArm") {
    dependsOn(packageThumbDrive)
    archiveFileName.set(BuildProjectConfig.archiveName("linux-arm64"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into(BuildProjectConfig.distributionName("linux-arm64"))
    from("build/dist") {
        include("run-linux.sh")
        include(BuildProjectConfig.LAUNCHER_JAR_NAME)
        include("jre/linux-aarch64/**")
    }
    eachFile {
        if (name == "run-linux.sh" || path.endsWith("/bin/java")) {
            mode = 493 // 0755 in octal
        }
    }
}

val zipMacArm = tasks.register<Zip>("zipMacArm") {
    dependsOn(packageThumbDrive)
    archiveFileName.set(BuildProjectConfig.archiveName("macos-arm64"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into(BuildProjectConfig.distributionName("macos-arm64"))
    from("build/dist") {
        include("run-mac-arm.command")
        include(BuildProjectConfig.LAUNCHER_JAR_NAME)
        include("jre/macos-aarch64/**")
    }
    eachFile {
        if (name == "run-mac-arm.command" || path.endsWith("/bin/java")) {
            mode = 493 // 0755 in octal
        }
    }
}

val zipMacIntel = tasks.register<Zip>("zipMacIntel") {
    dependsOn(packageThumbDrive)
    archiveFileName.set(BuildProjectConfig.archiveName("macos-x64"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into(BuildProjectConfig.distributionName("macos-x64"))
    from("build/dist") {
        include("run-mac-intel.command")
        include(BuildProjectConfig.LAUNCHER_JAR_NAME)
        include("jre/macos-x64/**")
    }
    eachFile {
        if (name == "run-mac-intel.command" || path.endsWith("/bin/java")) {
            mode = 493 // 0755 in octal
        }
    }
}

val packageZips = tasks.register("packageZips") {
    group = "distribution"
    description = "Assembles all platform-specific distribution ZIP archives."
    dependsOn(zipWindows, zipLinux, zipLinuxArm, zipMacArm, zipMacIntel)
}
