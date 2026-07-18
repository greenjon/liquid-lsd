---
name: imgui_memory_management
description: Guidelines for managing native memory allocations in ImGui/JVM wrapper to prevent JVM SegFaults.
---

# ImGui Memory Management in Kotlin/JVM (imgui-java)

Liquid LSD uses **imgui-java** (`io.github.spair:imgui-java`), not the raw LWJGL ImGui bindings.
`MemoryStack` from LWJGL is **not used in the `ui/` package** and is not the right tool for
ImGui widget state. Using it for ImGui inputs will cause incorrect behaviour or crashes.

---

## The Core Rule: Im-types Must Be Fields, Not Locals

ImGui input widgets (`InputText`, `Checkbox`, `SliderInt`, etc.) require a native-backed wrapper
object to hold their value across frames. If you allocate these as local variables inside a draw
function, you create a fresh object every frame with a fresh value, and the widget never retains
user input.

**Wrong — allocates every frame, widget input never persists:**
```kotlin
fun draw() {
    val buf = ImString(256)         // ❌ new object every frame
    ImGui.inputText("Name", buf)    // widget reads back 0/empty next frame
}
```

**Correct — allocate once as a class/object field:**
```kotlin
object AssetBrowserPanel {
    private val renameBuffer = ImString(256)   // ✅ allocated once at object init
    private val searchBuffer = ImString(256)   //    reused every frame

    fun draw() {
        ImGui.inputText("Rename", renameBuffer)
        ImGui.inputText("Search", searchBuffer)
    }
}
```

This is the pattern used throughout the codebase (`AssetBrowserPanel`, `DeckPresetBrowser`,
`ImGuiFileBrowser`, `CustomRangeSlider`).

---

## Im-types Reference

| Type | Widget use | Field or local? |
|------|-----------|-----------------|
| `ImString(capacity)` | `inputText` | **Always a field** — value must survive across frames |
| `ImBoolean` | `checkbox`, `menuItem` | **Field** if the toggle state persists; local only for ephemeral one-shot reads |
| `ImInt` | `sliderInt`, `combo` | **Field** if index must persist; local acceptable for scratch combos in rarely-opened panels |
| `ImFloat` | `sliderFloat` | Not currently used; same rule as `ImInt` |

> ⚠️ **`SettingsPanel`** currently allocates `ImBoolean` and `ImInt` as locals on every draw call.
> This is acceptable *only* because it is shown infrequently (not every frame). Do not introduce
> this pattern in hot-path panels like `PatchGridPanel`, `CellConfigPanel`, or `MixerMonitorPanel`.

---

## Font Byte Arrays — Keep Alive as Fields

ImGui holds a native pointer into the JVM byte array you pass to `addFontFromMemoryTTF`. If the
GC collects the array, you get a segfault on the next atlas rebuild. Always store font data as
a class field, never as a local:

```kotlin
// From UITheme.kt — correct pattern
private var regularBytes: ByteArray? = null   // ✅ kept alive as field
private var iconRange: ShortArray? = null     // ✅ glyph range array also retained

fun loadFonts() {
    regularBytes = resourceStream("/fonts/Inter-Regular.ttf").readBytes()
    // ...
    fontConfig.setFontDataOwnedByAtlas(false)   // tell native not to free JVM memory
    atlas.addFontFromMemoryTTF(regularBytes, size, fontConfig, iconRange)
}
```

---

## Native Objects That Need Explicit `.destroy()`

Some imgui-java objects wrap native heap allocations and are **not garbage collected**:

| Object | Where created | Where destroyed |
|--------|-------------|----------------|
| `ImGuiStyle` | `UIManager` — `defaultStyle = ImGuiStyle()` | `UIManager.dispose()` → `defaultStyle.destroy()` |
| `ImFontConfig` | `UITheme.loadFonts()` per font merge | Immediately after `addFontFromMemoryTTF(...)` call (including inside helper methods like `addWithIcons`) |

Always call `.destroy()` on these before dropping the reference. Every other imgui-java type
(`ImString`, `ImBoolean`, `ImInt`) is a JVM object with no native backing and does not need
explicit cleanup.

---

## What About `MemoryStack`?

`MemoryStack` is an LWJGL stack allocator used for **OpenGL/GLFW interop** (e.g. querying
framebuffer size, reading back pixel data). It is correct there. It has no role in ImGui widget
code. If you find yourself reaching for `MemoryStack` inside a draw function, you are likely
solving the wrong problem — use an Im-type field instead.
