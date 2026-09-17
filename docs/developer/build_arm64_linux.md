# Build for ARM64 Linux

Linux ARM64 (`aarch64`) is a supported Liquid LSD Desktop distribution target. This
guide explains how the one hard native-library blocker is solved, and how to refresh
that native binary if the pinned `imgui-java` version ever changes.

---

## 1. Background

Upstream `imgui-java` ([`io.github.spair:imgui-java`](https://github.com/SpaiR/imgui-java))
does not publish Linux ARM64 native binaries:
- Upstream issue [#105](https://github.com/SpaiR/imgui-java/issues/105) ("Make it work in
  arm64 linux") has been open since 2022 and remains open through the currently pinned
  `1.92.7.1` (see [`imgui_upgrade_guide.md`](imgui_upgrade_guide.md) for the version
  history).
- The `io.github.spair:imgui-java-natives-linux` artifact only ever contains `x86_64`
  ELF binaries.

Every other dependency already has first-class Linux ARM64 support:
- **LWJGL 3.3.3** ships `natives-linux-arm64` (GLFW, OpenGL, OpenAL, STB) — see
  `build.gradle.kts`.
- **JRE**: Eclipse Temurin provides `linux-aarch64` JRE 17 builds.
- **Audio (JACK) & Video (PipeWire)**: linked dynamically via JNA against distro
  packages, which are native on ARM64 distros (Debian, Ubuntu, Fedora, Raspberry Pi OS
  64-bit).
- **Ableton Link**: if `link_jni` is unavailable, Liquid LSD falls back to Carabiner
  TCP sync automatically.

So `libimgui-java64.so` for `linux-arm64` was the sole hard blocker.

---

## 2. Where the native binary comes from

Rather than cross-compiling `imgui-java` on every Liquid LSD release — this project
ships multiple times a day, while `imgui-java` changes far less often — the ARM64
native is built once per `imgui-java` version in a separate, standalone repo:

**[`greenjon/imgui-java-natives-linux-arm64`](https://github.com/greenjon/imgui-java-natives-linux-arm64)**

That repo's workflow checks out `SpaiR/imgui-java` at a given tag on a native
`ubuntu-24.04-arm` GitHub-hosted runner and runs its normal `generateLibs` build,
patched to select gdx-jnigen's existing (but upstream-unused) ARM64 Linux build target
instead of the x86 one `imgui-java`'s own script always requests.

That's the actual root cause of Linux ARM64 never having worked here, including
several earlier in-repo attempts: `imgui-java`'s `GenerateLibs.groovy` hardcodes
`BuildTarget.newDefaultTarget(Os.Linux, Architecture.Bitness._64)`, which defaults to
`Architecture.x86` and bakes in x86-only compiler flags (`-mfpmath=sse -msse -m64`)
that a native aarch64 `g++` rejects outright — *even though the runner itself is
ARM64*. `gdx-jnigen` 2.5.2 (the library `imgui-java` builds on) already ships a correct
`Architecture.ARM` Linux target (`aarch64-linux-gnu-` prefix, `-fPIC`, no SSE flags);
`imgui-java`'s build script just never selects it. See that repo's
`.github/workflows/build.yml` for the exact patch.

The resulting `.so` is published as a GitHub Release asset tagged with the `imgui-java`
version, e.g.
[`v1.92.7.1`](https://github.com/greenjon/imgui-java-natives-linux-arm64/releases/tag/v1.92.7.1).

### Rebuilding after an `imgui-java` version bump

If `imguiVersion` in `build.gradle.kts` is bumped and no matching release exists yet in
`imgui-java-natives-linux-arm64`:
1. Go to that repo's **Actions** tab → **Build & Release Linux ARM64 Natives** → **Run workflow**.
2. Pass the new `imgui-java` tag (e.g. `v1.93.0.0`).
3. Confirm the new release and its `.so` asset, then re-run/re-trigger Liquid LSD's CI —
   it downloads by version tag automatically (see below), no other changes needed.

---

## 3. How Liquid LSD consumes it

CI (`.github/workflows/smoke-test.yml` and `.github/workflows/release.yml`) downloads
the release asset matching the pinned `imguiVersion` directly into
`src/main/resources/natives/linux-arm64/libimgui-java64.so` before building, so it
ships embedded in the jar like any other resource — no native compiler toolchain is
needed in Liquid LSD's own CI or on `linux-arm64` contributors' machines.

At runtime, `NativeLibraryLoader.prepareImGuiNatives()` (in
`src/main/kotlin/llm/slop/liquidlsd/utils/NativeLibraryLoader.kt`) extracts that
embedded `.so` to a temp directory and points `imgui.ImGui` at it via
`System.setProperty("imgui.library.path", ...)`. It's called early in `Main.kt`,
before any `ImGui` calls.

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
If omitted, Liquid LSD automatically falls back to Carabiner TCP sync without user
intervention.

---

## 5. Distribution packaging

Already wired up:
- **`build.gradle.kts`**: `zipLinuxArm` task bundles the Adoptium JRE 17
  `linux-aarch64` build and `run-linux-arm.sh` launcher; included in `packageZips`.
- **CI**: `linux-arm64` is a matrix entry (`runs-on: ubuntu-24.04-arm`) in the
  smoke-test matrices of both `smoke-test.yml` and `release.yml`.
- **PipeWire**: `SpaData` and `PipeWireBufferStruct` in `PipeWireLibrary.kt` are JNA
  structures whose layout follows declared field order rather than architecture-specific
  padding assumed here — re-verify on aarch64 if those struct definitions ever change.
