# Build for ARM64 Linux

This guide details how to restore the **Linux ARM64 (`aarch64`)** build target for Liquid LSD Desktop by building the missing native binaries via GitHub Actions and integrating them into the project.

---

## 1. Background & Problem Statement

Linux ARM64 support was dropped as a distribution target because upstream `imgui-java` ([`io.github.spair:imgui-java`](https://github.com/SpaiR/imgui-java)) does not publish ARM64 ELF native `.so` binaries for Linux:
- Upstream issue [#105 (*"Make it work in arm64 linux"*)] (https://github.com/SpaiR/imgui-java/issues/105) remains open.
- The `io.github.spair:imgui-java-natives-linux` artifact contains only `x86_64` ELF binaries (`io/imgui/java/native-bin/libimgui-java64.so`).
- Upstream added Apple Silicon support in `1.86.12` via universal Mach-O binaries (`x86_64` + `arm64`), but never added Linux aarch64.

### What Liquid LSD Already Has Ready
All other dependencies and subsystems already have first-class Linux ARM64 support:
- **LWJGL 3.3.3**: Already includes `natives-linux-arm64` runtime dependencies in `build.gradle.kts` (GLFW, OpenGL, OpenAL, STB).
- **JRE / JVM**: Eclipse Temurin (Adoptium) provides production JRE 17 binaries for `linux-aarch64`.
- **Audio (JACK) & Video (PipeWire)**: Link dynamically against standard distro packages via JNA (`libjack.so`, `libpipewire-0.3.so`), which are fully native on ARM64 distributions (Debian, Ubuntu, Fedora, Raspberry Pi OS 64-bit).
- **Ableton Link**: If `link_jni` is missing, Liquid LSD gracefully falls back to Carabiner TCP synchronization.

The **sole hard blocker** is the native JNI library for Dear ImGui (`libimgui-java64.so`).

---

## 2. Compiling `imgui-java` on GitHub Actions (No Docker Required)

Rather than maintaining a local cross-compilation toolchain or Docker environment, you can compile native ARM64 binaries using **GitHub's free native ARM64 runners** (`ubuntu-24.04-arm`).

### Step 1: Fork and Branch
1. Fork [`SpaiR/imgui-java`](https://github.com/SpaiR/imgui-java) on GitHub.
2. Clone your fork locally and check out the version pinned by Liquid LSD (`v1.86.12`):
   ```bash
   git clone https://github.com/<your-username>/imgui-java.git
   cd imgui-java
   git checkout -b build-arm64-1.86.12 v1.86.12
   git submodule update --init --recursive
   ```

### Step 2: Add Native ARM64 Workflow
Create `.github/workflows/build-arm64.yml` in your fork:

```yaml
name: Build Linux ARM64 Natives

on:
  workflow_dispatch:

jobs:
  build-arm64:
    name: Build Linux aarch64
    runs-on: ubuntu-24.04-arm  # Native GitHub-hosted 64-bit ARM Linux runner
    steps:
      - name: Checkout Repository and Submodules
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          submodules: recursive

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Install Compiler Tools
        run: sudo apt-get update && sudo apt-get install -y build-essential

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Java Bindings & Generate JNI Headers
        run: |
          ./gradlew :imgui-binding:compileJava

      - name: Generate and Compile Native Libraries
        run: |
          # SpaiR's jnigen invokes host g++ to compile libimgui-java64.so.
          # Because this runs on an ubuntu-24.04-arm runner, g++ naturally outputs an ARM64 ELF library.
          ./gradlew :imgui-binding:generateLibs -Denvs=linux -Dlocal

      - name: Package Natives JAR
        run: |
          ./gradlew :imgui-binding:jar

      - name: Upload ARM64 Native Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: imgui-java-linux-arm64
          path: |
            **/build/**/libimgui-java64.so
            imgui-binding/build/libs/*.jar
```

### Step 3: Run the Workflow
1. Push the branch to your fork on GitHub:
   ```bash
   git push origin build-arm64-1.86.12
   ```
2. Navigate to **Actions** in your GitHub repository, select **Build Linux ARM64 Natives**, and click **Run workflow**.
3. Download the zipped artifact `imgui-java-linux-arm64` containing `libimgui-java64.so`.

---

## 3. Integrating `libimgui-java64.so` into Liquid LSD

`imgui.ImGui` looks for native binaries using three fallback mechanisms:
1. `System.getProperty("imgui.library.path")` — path to directory containing `libimgui-java64.so`.
2. `System.loadLibrary("imgui-java64")`.
3. Extraction from the classpath under `/native-bin/libimgui-java64.so`.

We can wire the downloaded binary into Liquid LSD using either of the following approaches:

### Approach A: Embedded Resource & Dynamic Loader Hook (Recommended)

1. Place the compiled `libimgui-java64.so` in Liquid LSD resources:
   ```
   src/main/resources/natives/linux-arm64/libimgui-java64.so
   ```

2. In `NativeLibraryLoader.kt`, add an ImGui initialization helper:
   ```kotlin
   fun prepareImGuiNatives() {
       if (currentOs == OS.LINUX && currentArch == Arch.ARM64) {
           val libFileName = "libimgui-java64.so"
           val resourcePath = "/natives/linux-arm64/$libFileName"
           val stream = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
           if (stream != null) {
               val tempDir = File(System.getProperty("java.io.tmpdir"), "liquid_lsd_imgui_arm64")
               tempDir.mkdirs()
               val destFile = File(tempDir, libFileName)
               if (!destFile.exists() || destFile.length() == 0L) {
                   stream.use { input ->
                       destFile.outputStream().use { output -> input.copyTo(output) }
                   }
                   destFile.setExecutable(true)
               }
               System.setProperty("imgui.library.path", tempDir.absolutePath)
               logger.info { "Configured ImGui ARM64 library path: ${tempDir.absolutePath}" }
           }
       }
   }
   ```

3. Call `NativeLibraryLoader.prepareImGuiNatives()` early in `Main.kt` before calling any `ImGui` methods or loading UI layouts.

---

## 4. Optional: Compiling `link_jni` (Ableton Link) for ARM64

To get native sample-accurate Ableton Link C++ bindings on Linux ARM64:

```bash
cd src/main/cpp/link_jni
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)
```

Place the resulting `liblink_jni.so` into:
```
src/main/resources/natives/linux-arm64/liblink_jni.so
```
If omitted, Liquid LSD automatically falls back to Carabiner TCP sync without user intervention.

---

## 5. Re-Enabling Distribution Packaging

Once the native binary is staged:

1. **`build.gradle.kts`**:
   - Re-add `zipLinuxArm` task targeting `Adoptium` JRE 17 `linux-aarch64`.
   - Include `zipLinuxArm` in `packageZips`.
2. **CI / Smoke Tests**:
   - Re-enable `linux-arm64` in `.github/workflows/smoke-test.yml` and `.github/workflows/release.yml` using `runs-on: ubuntu-24.04-arm`.
3. **Verify PipeWire Structure Alignment**:
   - Verify `SpaData` and `PipeWireBufferStruct` in `PipeWireLibrary.kt` on aarch64.
