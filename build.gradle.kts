import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    // Kotlin: version 2.3.0 (supports JDK 25+). Do not update automatically.
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
    // Shadow plugin: version 9.0.0 (com.gradleup.shadow). Do not update automatically.
    id("com.gradleup.shadow") version "9.0.0"
    id("com.github.ben-manes.versions") version "0.51.0"
}

group = "llm.slop"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.3"
// imgui-java: current is 1.86.11, latest is 1.92.0. Flag for dedicated smoke-test upgrade.
val imguiVersion = "1.86.11"

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
    implementation("ch.qos.logback:logback-classic:1.5.38")

    // Markdown processing for website/docs generator
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("org.yaml:snakeyaml:2.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("llm.slop.liquidlsd.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    // JDK 17 toolchain — Gradle auto-detects the provisioned JDK at ~/.gradle/jdks.
    // Produces JDK 17-compatible bytecode for cross-platform distribution.
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.exclude("**/ANDROID-REFERENCE/**")
        }
    }
}

tasks.withType<JavaExec> {
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

    // Gradle 9: exec is no longer available on Project; use providers.exec or ProcessBuilder directly.
    doLast {
        val hasMkdocs = try {
            val pb = ProcessBuilder("mkdocs", "--version")
            val proc = pb.start()
            proc.waitFor() == 0
        } catch (e: java.io.IOException) {
            false
        }

        if (hasMkdocs) {
            val result = ProcessBuilder("mkdocs", "build", "-d", "${project.projectDir}/src/main/resources/docs")
                .inheritIO()
                .start()
                .waitFor()
            if (result != 0) throw GradleException("mkdocs build failed with exit code $result")
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

        // TODO: update these checksums when upgrading the JRE version
        // Obtain from: https://api.adoptium.net/v3/assets/...
        val jreChecksums = mapOf(
            "windows-x64.zip" to "EXPECTED_SHA256_HERE",
            "linux-x64.tar.gz" to "EXPECTED_SHA256_HERE",
            "linux-aarch64.tar.gz" to "EXPECTED_SHA256_HERE",
            "macos-x64.tar.gz" to "EXPECTED_SHA256_HERE",
            "macos-aarch64.tar.gz" to "EXPECTED_SHA256_HERE"
        )

        fun verifyChecksum(file: File, expectedSha256: String) {
            if (expectedSha256 == "EXPECTED_SHA256_HERE") {
                logger.warn("Checksum not pinned for ${file.name} — update jreChecksums in build.gradle.kts")
                return
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = file.inputStream().use { digest.digest(it.readBytes()) }
                .joinToString("") { "%02x".format(it) }
            require(actual == expectedSha256) {
                "Checksum mismatch for ${file.name}: expected $expectedSha256, got $actual"
            }
        }

        inputs.file(tasks.named("shadowJar").map { it.outputs.files.singleFile })
        outputs.dir(distDir)

        doLast {
            distDir.deleteRecursively()
            distDir.mkdirs()
            jreCacheDir.mkdirs()

            // 1. Copy shadowJar
            val jarFile = tasks.named("shadowJar").get().outputs.files.singleFile
            val destJar = file("$distDir/lsd-all.jar")
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
                val filename = "$name.$ext"
                val cacheFile = file("$jreCacheDir/$filename")
                if (!cacheFile.exists()) {
                    println("Downloading JRE for $name...")
                    try {
                        URI(url).toURL().openStream().use { input ->
                            Files.copy(input, cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        println("Successfully downloaded JRE for $name.")
                    } catch (e: Exception) {
                        throw GradleException("Failed to download JRE for $name from $url: ${e.message}", e)
                    }
                } else {
                    println("Using cached JRE for $name.")
                }
                verifyChecksum(cacheFile, jreChecksums[filename] ?: "EXPECTED_SHA256_HERE")

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
                "jre\windows-x64\bin\java.exe" -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            ) else (
                echo Bundled JRE not found. Trying system java...
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
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
                exec java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            fi

            if [ -f "${'$'}JRE_DIR/bin/java" ]; then
                chmod +x "${'$'}JRE_DIR/bin/java"
                exec "./${'$'}JRE_DIR/bin/java" -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            else
                echo "Bundled JRE not found. Trying system java..."
                exec java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
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
                ./jre/macos-aarch64/bin/java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            else
                echo "Bundled JRE not found. Trying system java..."
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
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
                ./jre/macos-x64/bin/java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            else
                echo "Bundled JRE not found. Trying system java..."
                java -ea -XX:+UseZGC -XX:MaxGCPauseMillis=2 -Xms512m -Xmx2g -jar lsd-all.jar
            fi
        """.trimIndent())
        runMacIntel.setExecutable(true)

        println("Launcher scripts generated successfully.")
    }
}

val zipWindows = tasks.register<Zip>("zipWindows") {
    dependsOn(packageThumbDrive)
    archiveFileName.set("liquid-lsd-windows-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("liquid-lsd-windows-x64")
    from("build/dist") {
        include("run-windows.bat")
        include("lsd-all.jar")
        include("jre/windows-x64/**")
    }
}

val zipLinux = tasks.register<Zip>("zipLinux") {
    dependsOn(packageThumbDrive)
    archiveFileName.set("liquid-lsd-linux-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("liquid-lsd-linux-x64")
    from("build/dist") {
        include("run-linux.sh")
        include("lsd-all.jar")
        include("jre/linux-x64/**")
    }
    eachFile {
        if (name == "run-linux.sh" || path.endsWith("/bin/java")) {
            filePermissions { unix("755") } // Gradle 9: mode replaced by filePermissions
        }
    }
}

val zipLinuxArm = tasks.register<Zip>("zipLinuxArm") {
    dependsOn(packageThumbDrive)
    archiveFileName.set("liquid-lsd-linux-arm64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("liquid-lsd-linux-arm64")
    from("build/dist") {
        include("run-linux.sh")
        include("lsd-all.jar")
        include("jre/linux-aarch64/**")
    }
    eachFile {
        if (name == "run-linux.sh" || path.endsWith("/bin/java")) {
            filePermissions { unix("755") }
        }
    }
}

val zipMacArm = tasks.register<Zip>("zipMacArm") {
    dependsOn(packageThumbDrive)
    archiveFileName.set("liquid-lsd-macos-arm64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("liquid-lsd-macos-arm64")
    from("build/dist") {
        include("run-mac-arm.command")
        include("lsd-all.jar")
        include("jre/macos-aarch64/**")
    }
    eachFile {
        if (name == "run-mac-arm.command" || path.endsWith("/bin/java")) {
            filePermissions { unix("755") }
        }
    }
}

val zipMacIntel = tasks.register<Zip>("zipMacIntel") {
    dependsOn(packageThumbDrive)
    archiveFileName.set("liquid-lsd-macos-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("liquid-lsd-macos-x64")
    from("build/dist") {
        include("run-mac-intel.command")
        include("lsd-all.jar")
        include("jre/macos-x64/**")
    }
    eachFile {
        if (name == "run-mac-intel.command" || path.endsWith("/bin/java")) {
            filePermissions { unix("755") }
        }
    }
}

val packageZips = tasks.register("packageZips") {
    group = "distribution"
    description = "Assembles all platform-specific distribution ZIP archives."
    dependsOn(zipWindows, zipLinux, zipLinuxArm, zipMacArm, zipMacIntel)
}

val checkWebSync = tasks.register<Exec>("checkWebSync") {
    group = "verification"
    description = "Checks synchronization state and detects drift between Desktop and Web application assets."
    commandLine("python3", "scripts/sync_web.py", "--check")
}

val syncWeb = tasks.register<Exec>("syncWeb") {
    group = "build"
    description = "Synchronizes and transpiles Desktop shaders and assets into the WebGL2 web application (web/)."
    commandLine("python3", "scripts/sync_web.py", "--apply")
}

val buildWebsite = tasks.register<JavaExec>("buildWebsite") {
    group = "documentation"
    description = "Generates the static website and HTML documentation bundle into ./greenjon/ for greenjon.com FTP deployment."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("llm.slop.liquidlsd.tools.SiteGenerator")
    args(
        project.projectDir.absolutePath,
        project.file("greenjon").absolutePath,
        project.version.toString(),
        "https://github.com/greenjon/liquid-lsd"
    )
    inputs.files(fileTree("docs"), fileTree("website"), "mkdocs.yml", "RELEASE_NOTES.md")
    outputs.dir("greenjon")
}

val exportGreenjon = tasks.register("exportGreenjon") {
    group = "distribution"
    description = "Assembles and exports the complete greenjon.com website bundle into ./greenjon/."
    dependsOn(buildWebsite)
}
