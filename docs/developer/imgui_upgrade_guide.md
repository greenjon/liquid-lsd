# ImGui Version & ARM64 Notes

Liquid LSD uses `io.github.spair:imgui-java` **1.92.7.1** (Dear ImGui 1.92). It got there in two steps: 1.86.11 → 1.86.12 (drop-in, for Apple Silicon) and then 1.86.12 → 1.92.7.1 (API changes, listed below for anyone reading old code or upgrading again).

---

## ARM64 Support

- **macOS ARM64**: `imgui-java-natives-macos` 1.86.11 shipped an x86_64-only `libimgui-java64.dylib` (`UnsatisfiedLinkError ... have 'x86_64', need 'arm64'` on Apple Silicon). From 1.86.12 on, every release ships a universal (`x86_64` + `arm64`) dylib.
- **Linux ARM64**: no upstream release, up to and including 1.92.7.1, ships an `aarch64` `libimgui-java64.so`; the bundled one is x86-64 only (upstream issue [#105](https://github.com/SpaiR/imgui-java/issues/105)). Liquid LSD ships its own build of it; see [Build for ARM64 Linux](build_arm64_linux.md).

---

## 1.86 → 1.92 API Changes

What changed in the bindings when the app moved to 1.92, and the replacement used throughout the code:

| 1.86.x | 1.92.x |
| :--- | :--- |
| `ImTextureID` is `Int` (`ImGui.image(texId, ...)`) | `Long`: pass `texId.toLong()` |
| `ImGuiImplGlfw.dispose()` / `ImGuiImplGl3.dispose()` | `shutdown()` |
| `ImGuiImplGl3.updateFontsTexture()` | `destroyFontsTexture()` then `createFontsTexture()` |
| `ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.X))` | `ImGui.isKeyPressed(ImGuiKey.X)` (New Key API; `getKeyIndex` removed) |
| `ImDrawList.addRectFilledMultiColor` takes `Long` colours | `Int` colours (drop the `.toLong()`) |
| `ImGui.pushFont(font)` | `ImGui.pushFont(font, size)`; `0f` keeps the font's baked size |
| `ImGuiStyle.tabMinWidthForCloseButton` | removed upstream; don't copy it |

1.92 also brought dynamic font scaling and native multi-selection primitives (the Library's multi-select is its own code, not these).
