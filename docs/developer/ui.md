# UI Architecture & ImGui Systems

The `ui/` package manages the immediate-mode desktop interface using ImGui (`imgui-java`), LWJGL 3, and GLFW. This document maps component ownership, memory safety patterns, popup scheduling, and the `NoteEditorModal` architecture.

---

## Component Dependency Graph

```mermaid
graph TD
    UIManager[UIManager.kt - Top-level Orchestrator & GLFW/ImGui Loop]
    
    SessionContext[SessionContext.kt - DI Container for Subsystems]
    UITheme[UITheme.kt - Fonts, Styling, Settings]
    PresetGridState[PresetGridState.kt - Selection & 30-level Undo Stack]
    PopupManager[PopupManager.kt - Modal Dialog Management]
    DeckPresetController[DeckPresetController.kt - Deck Presets & File Dialogs]
    NoteEditorModal[NoteEditorModal.kt - Note Editor Modal]
    
    UIManager --> SessionContext
    UIManager --> UITheme
    UIManager --> PresetGridState
    UIManager --> PopupManager
    UIManager --> DeckPresetController
    UIManager --> NoteEditorModal
    
    UIManager --> MenuBar[MenuBar.kt]
    UIManager --> PresetGridPanel[PresetGridPanel.kt]
    UIManager --> CellConfigPanel[CellConfigPanel.kt]
    UIManager --> MixerMonitorPanel[MixerMonitorPanel.kt]
    UIManager --> LibraryPanel[LibraryPanel.kt & PlaylistEditorPanel.kt]
    
    MixerMonitorPanel --> DeckControlPanel[DeckControlPanel.kt]
    DeckControlPanel --> drawDeckMonitorToolbar[drawDeckMonitorToolbar Helper]
    UIManager --> SavePresetModal[SavePresetModal.kt]
```

All panel `draw(...)` methods receive `session: SessionContext`, the current `Mixer` reference, and `presetState: PresetGridState` at frame render time. Panels access subsystems (`AudioEngine`, `CVRegistry`, `PresetManager`, `PlayQueueManager`, `NotesManager`) via `session` rather than direct global singletons.

Deck preview monitors (`Deck A`, `Deck B`, `Deck C`) in `MixerMonitorPanel` and `DeckControlPanel` use a unified interactive preset bar (`drawDeckMonitorToolbar`) positioned directly **above** each monitor image. The preset bar orders elements left-to-right as `[Save Button] [Eject Button] [Preset Bar]`. Buttons and the Preset Bar are aligned along their bottom baselines, and the row height dynamically expands as text font scaling increases.

Left-clicking any deck preview monitor (`Deck A`, `Deck B`, or `Deck C`) immediately focuses the Preset Grid to that deck by setting `PresetGridState.activeTopTab`. Dragging from a monitor initiates deck copy, move, or swap routing, and dropping preset files directly onto a monitor loads the preset into the corresponding deck.

`MixerMonitorLayoutCalculator` calculates exact 16:9 aspect preview sizes against available pane height and comprehensive vertical chrome (master controls, preset bars, separator bands, and safety margins). It utilizes the full pane width without reserving unconditional scrollbars, automatically scaling monitor previews to fit vertically without scrolling on standard screens, and displaying scrollbars only on extremely small display heights.

---

## Key Core UI Orchestrators

### 1. `UIManager.kt`
- **Main Loop Integration**: Invoked once per frame (`render(mixer, width, height)`). Initialises and disposes `ImGuiImplGlfw` and `ImGuiImplGl3`.
- **Workspace Layout Orchestration**: Coordinates the three-column desktop workspace by delegating splitting and styling to dedicated components. Left Panel (`PresetGridPanel`) width auto-fits active CV columns, label widths, and font zoom (`CTRL-`/`CTRL=`).
- **Deferred Font Atlas Rebuilding**: Changing font size sets `pendingFontSize`. Rebuilding font atlas and OpenGL textures occurs at the **top of the next frame** (before `ImGui.newFrame()`) to prevent mid-frame atlas corruption.
- **Deferred Popup Triggering**: Modal popups set a `pendingOpen*` flag and execute `ImGui.openPopup(id)` at the root ID stack level outside child windows.
- **Modal Rendering Pipeline**: Invokes `NoteEditorModal.draw()` and `PopupManager.draw()` at root scope.

### 2. `UIThemeStyler.kt`, `ColorTunerPanel.kt` & `SplitterManager.kt`
- **`UIThemeStyler.kt`**: Applies ImGui color palettes across all themes (`BORING`, `DARK_SOLARIZED`, `LIGHT_SOLARIZED`, `DARK_LUNARIZED`, `LIGHT_LUNARIZED`, `NEON`), manages window transparency/alpha blending when background video is enabled, renders multi-color Neon gradient backgrounds, and handles proportional `ImGuiStyle` size scaling.
- **`ColorTunerPanel.kt`**: Interactive developer tool window accessible via the "Color" top menu item. Provides real-time swatch assignment to all 17 themed ImGui elements without background dimming, enabling live dial-in across all palettes with instant clipboard Kotlin code generation.
- **`SplitterManager.kt`**: Manages mouse hit-testing, resize cursors (`ResizeEW` / `ResizeNS`), double-click reset positions, and draw-list divider rendering for vertical and horizontal layout splitters.

### 3. `DeckPresetController.kt`
- **Role**: Dedicated orchestrator for deck preset lifecycle, modal save/load/eject workflows, and file dialogs.
- **Deck Actions**: Coordinates move/copy/swap deck utilities with dirty-state checks, quick save vs "Save As" flow (`SavePresetModal`), duplicate copy naming (`_copy`), and ejecting with Auto-VJ dirty behavior resolution.
- **ImGui File Browsers**: Manages independent `ImGuiFileBrowser` dialogs for Deck A and Deck B and executes asynchronous disk I/O via `PresetManager`.

### 4. `NoteEditorModal.kt`
- **Role**: Stateful singleton modal editor for the 3-tier Note System.
- **`NoteContext` Sealed Class**:
  - `Param(deckLabel, paramKey, displayLabel)`: Edits parameter-level notes.
  - `Source(sourceId, displayName)`: Edits global visual source notes.
  - `Preset(deckLabel, presetName)`: Edits preset-level notes.
- **Zero-Allocation Buffer Safety**: Allocates a single `ImString(2048)` buffer at object instantiation (`textBuffer`). Calling `NoteEditorModal.request(context)` populates `textBuffer` with the current note text. Drawing `ImGui.inputTextMultiline` reuses this pre-allocated buffer every frame without heap allocation.

### 5. `UITheme.kt`
- Manages font rendering (Inter, JetBrains Mono, Lucide icons merged via `setMergeMode(true)`).
- **Proportional Icon Glyph Offset**: Lucide icons are configured with a scaled `setGlyphOffset(0f, round(size * 0.18f))` to ensure optical vertical centering and prevent icon bounding boxes from touching the top edge of buttons across all font sizes.
- **Critical Font Array Ownership**: Font `ByteArray` fields (`regularBytes`, `boldBytes`) and `iconRange: ShortArray` are stored as class fields. Calling `setFontDataOwnedByAtlas(false)` prevents native ImGui from attempting to free JVM-managed byte arrays.

### 6. Custom Sliders (`CustomRangeSlider.kt` & `BeatDivisionSlider.kt`)
- Compute row height (`h`), label positions (`labelY`), widget rows (`row2Y`), and center line (`centerY`) dynamically using font metrics (`captionHeight`, `getFrameHeight()`, and `fontScale`) to ensure the "Current:" label and slider tracks never overlap adjacent rows or widgets across all font scales.

---

## ImGui Native Memory & Allocation Rules

Because ImGui uses JNI wrappers around native C++ pointers, strict memory rules must be followed across all `ui/` files to prevent JVM SegFaults and Garbage Collection pauses:

| Wrapper Type | Instantiation Rule | Cleanup Rule |
|--------------|-------------------|--------------|
| **`ImString`** | Allocate as **class/field-level variable**. Never allocate locally inside `draw()`. | Reused frame-to-frame. |
| **`ImBoolean` / `ImInt`** | Class field if state persists; local variables allowed only in rare modal popups. | Reused frame-to-frame. |
| **`ImGuiStyle`** | Class/singleton instance. | Must call `.destroy()` in `dispose()`. |
| **`ImFontConfig`** | Instantiated per font build. | Must call `.destroy()` immediately after loading. |

---

## Pattern for Adding a New UI Panel

1. **Pre-allocate String Buffers**: Define all `ImString` fields at the class/object level (e.g. `val searchBuffer = ImString(256)`).
2. **Inject Context at Draw Time**: Accept `session: SessionContext`, `mixer: Mixer`, and `presetState: PresetGridState` in the `draw(...)` signature.
3. **Use Deferred Root Popups**: To open a popup, set a `pendingOpen` flag and call `ImGui.openPopup(id)` at the root ID level.
4. **Register in `UIManager`**: Hook panel rendering into `UIManager.render()`.
