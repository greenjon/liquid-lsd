# ImGui Upgrade & Modernization Guide

This document details the multi-architecture ARM64 investigation for `imgui-java` (`io.github.spair`), the rationale for our two-phase upgrade strategy, and a comprehensive migration guide for future upgrades to Dear ImGui 1.92.x.

---

## Architecture Support Matrix & Findings

During our investigation of all published `io.github.spair:imgui-java` artifacts on Maven Central from `1.86.11` through `1.92.7.1`, we identified the exact state of ARM64 support across target platforms:

### 1. Apple Silicon (macOS ARM64 / `aarch64`)
- **Version 1.86.11**: `imgui-java-natives-macos-1.86.11.jar` packaged `libimgui-java64.dylib` compiled **exclusively for x86_64**. On Apple Silicon JVMs, running the application crashed on startup with:
  ```text
  java.lang.UnsatisfiedLinkError: ... libimgui-java64.dylib: mach-o file, but is an incompatible architecture (have 'x86_64', need 'arm64')
  ```
- **Version 1.86.12 (First ARM64 Release)**: Starting with release `1.86.12`, the upstream maintainer repackaged the macOS dylib as a **Mach-O Universal Binary** containing both `x86_64` and `arm64` architectures merged via `lipo`.
- **Subsequent Releases (1.87.0 through 1.92.7.1)**: All later versions retain universal binary packaging for macOS.

### 2. Linux ARM64 (`linux-arm64` / `aarch64`)
- **Upstream Status across ALL Releases**: In every release of `io.github.spair:imgui-java-natives-linux` up to and including the latest `1.92.7.1`, the bundled `libimgui-java64.so` is **strictly an `ELF 64-bit x86-64` shared object**.
- Upstream issue [#105](https://github.com/SpaiR/imgui-java/issues/105) (*"Make it work in arm64 linux"*) remains open. Upstream does not publish an official `imgui-java-natives-linux-arm64` artifact.
- **Impact**: Upgrading `imgui-java` resolves macOS ARM64 out-of-the-box, but does not natively resolve Linux ARM64 without custom native build steps or an architecture-aware dynamic loader.

---

## Two-Phase Upgrade Strategy

| Metric | Phase 1: Target `1.86.12` (Current) | Phase 2: Target `1.92.x` (Roadmap) |
| :--- | :--- | :--- |
| **macOS ARM64 Support** | **Full** (Universal `x86_64` + `arm64`) | **Full** (Universal `x86_64` + `arm64`) |
| **Linux ARM64 Support** | No (Upstream limitation) | No (Upstream limitation) |
| **Breaking API Changes** | **0** (Drop-in compatible) | **7 distinct categories** across 9 files |
| **Regression Risk** | **Zero** | Medium (Requires visual regression audit) |
| **Scope of Work** | Version bump in `build.gradle.kts` | Multi-panel refactoring & style verification |

### Phase 1: Stable Adoption (`1.86.12`)
We adopted `1.86.12` to immediately provide native Apple Silicon execution and unblock macOS ARM64 binary smoke testing without introducing source code churn, behavioural drift, or UI regression.

### Phase 2: Modernization Roadmap (`1.92.7.1`)
When scheduling the Phase 2 upgrade to the latest release, developers must implement the breaking changes documented below.

---

## Phase 2 Migration Guide: Breaking Changes & Fixes

Upgrading from `1.86.x` to `1.92.x` introduces breaking changes across GLFW/GL3 backends, font management, keyboard input handling, 64-bit texture handles, and draw list APIs.

### 1. 64-bit Texture Handles (`ImTextureID` changed from `Int` to `Long`)
- **Symptom**:
  ```text
  DeckControlPanel.kt:82:21 Argument type mismatch: actual type is 'Int', but 'Long' was expected.
  MixerMonitorPanel.kt:45:21 Argument type mismatch: actual type is 'Int', but 'Long' was expected.
  VideoExportModal.kt:124:19 None of the following candidates is applicable: static fun image(p0: Long, ...): actual type is 'Int', but 'Long' was expected.
  ```
- **Cause**: Upstream Dear ImGui switched `ImTextureID` from a 32-bit integer to a 64-bit pointer/scalar (`Long` in Java bindings) to support 64-bit handles and modern graphics APIs.
- **Fix**: Convert OpenGL texture handle ints with `.toLong()`:
  ```kotlin
  // DeckControlPanel.kt:82
  ImGui.image(deck.getOutputTexture().toLong(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)

  // MixerMonitorPanel.kt:45
  ImGui.image(mixer.getOutputTexture().toLong(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)

  // VideoExportModal.kt:124
  ImGui.image(previewTexId.toLong(), previewW, previewH, 0f, 1f, 1f, 0f)
  ```

---

### 2. Backend Lifecycle Methods (`dispose()` $\to$ `shutdown()`)
- **Symptom**:
  ```text
  UIManager.kt:614:18 Unresolved reference 'dispose'.
  UIManager.kt:615:19 Unresolved reference 'dispose'.
  ```
- **Cause**: `imgui-java-lwjgl3` backend classes (`ImGuiImplGlfw` and `ImGuiImplGl3`) replaced the custom `.dispose()` method with standard Dear ImGui `.shutdown()`.
- **Fix**:
  ```kotlin
  // UIManager.kt:614-615
  imGuiGlfw.shutdown()
  imGuiGl3.shutdown()
  ```

---

### 3. Font Atlas Dynamic Rebuild (`updateFontsTexture()`)
- **Symptom**:
  ```text
  UIManager.kt:329:22 Unresolved reference 'updateFontsTexture'.
  ```
- **Cause**: `ImGuiImplGl3.updateFontsTexture()` was removed. The backend now exposes discrete `destroyFontsTexture()` and `createFontsTexture()` operations.
- **Fix**: Replace `updateFontsTexture()` with destroy and recreate calls:
  ```kotlin
  // UIManager.kt:329
  imguiGl3.destroyFontsTexture()
  imguiGl3.createFontsTexture()
  ```

---

### 4. Input System Overhaul (`getKeyIndex()` Removed)
- **Symptom**:
  ```text
  Unresolved reference 'getKeyIndex'.
  ```
- **Cause**: Dear ImGui overhauled its keyboard input pipeline (the "New Key API"). `ImGuiKey` constants are now direct key identifiers rather than indices into the legacy `io.KeysDown` array. `ImGui.getKeyIndex(...)` was removed.
- **Impacted Files**:
  - `src/main/kotlin/llm/slop/liquidlsd/ui/PresetGridKeyboard.kt`
  - `src/main/kotlin/llm/slop/liquidlsd/ui/UIManager.kt`
  - `src/main/kotlin/llm/slop/liquidlsd/ui/browser/BgQueueActionsPanel.kt`
  - `src/main/kotlin/llm/slop/liquidlsd/ui/browser/PlaylistEditorPanel.kt`
  - `src/main/kotlin/llm/slop/liquidlsd/ui/browser/PresetListPanel.kt`
  - `src/main/kotlin/llm/slop/liquidlsd/ui/browser/QueueActionsPanel.kt`
- **Fix**: Pass the `ImGuiKey` enum constants directly to `ImGui.isKeyPressed` and `ImGui.isKeyDown`:
  ```kotlin
  // Legacy (1.86.x):
  ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.UpArrow))
  ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Enter))

  // Modern (1.92.x):
  ImGui.isKeyPressed(ImGuiKey.UpArrow)
  ImGui.isKeyPressed(ImGuiKey.Enter)
  ```

---

### 5. Multi-Color DrawList Colors (`Long` $\to$ `Int`)
- **Symptom**:
  ```text
  UIThemeStyler.kt:235:78 Argument type mismatch: actual type is 'Long', but 'Int' was expected.
  ```
- **Cause**: In `1.86.x`, `ImDrawList.addRectFilledMultiColor` expected `Long` values for packed 32-bit colors. In `1.89+`, parameter types were standardized back to standard 32-bit integer color values (`Int`).
- **Fix**: Remove `.toLong()` conversions:
  ```kotlin
  // UIThemeStyler.kt:232-235
  val leftCol = getNeonBgColor((posX / displayWidth).coerceIn(0f, 1f))
  val rightCol = getNeonBgColor(((posX + panelW) / displayWidth).coerceIn(0f, 1f))

  dl.addRectFilledMultiColor(posX, posY, posX + panelW, posY + panelH, leftCol, rightCol, rightCol, leftCol)
  ```

---

### 6. Dynamic Font Scaling Parameter in `pushFont`
- **Symptom**:
  ```text
  UITheme.kt:685:36 No value passed for parameter 'p1'.
  ```
- **Cause**: Dear ImGui 1.92 added dynamic runtime font scaling support directly to `pushFont`. In the Java bindings, the signature is `ImGui.pushFont(ImFont font, float fontSize)`.
- **Fix**: Pass `0f` (which instructs ImGui to use the font's native baked size) or `font.fontSize`:
  ```kotlin
  // UITheme.kt:685
  if (pushed) ImGui.pushFont(font, 0f)
  ```

---

### 7. Deprecated Style Attribute Removal (`TabMinWidthForCloseButton`)
- **Symptom**:
  ```text
  UIThemeStyler.kt:267:12 Unresolved reference 'setTabMinWidthForCloseButton'.
  UIThemeStyler.kt:267:46 Unresolved reference 'getTabMinWidthForCloseButton'.
  ```
- **Cause**: `tabMinWidthForCloseButton` was deprecated and removed from `ImGuiStyle` in Dear ImGui upstream.
- **Fix**: Remove line 267 from `UIThemeStyler.copyStyleSizes`:
  ```kotlin
  // Remove:
  // to.setTabMinWidthForCloseButton(from.getTabMinWidthForCloseButton())
  ```
