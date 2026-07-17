# UI Layer

The `ui/` package contains 34 files. This document maps the panel hierarchy, ownership of state,
key patterns, and what to read before touching any UI code.

---

## Top-Level Layout

```
┌─ MenuBar (top bar, full width) ───────────────────────────────────┐

┌─ PatchGrid (40%) ──┬─ CellConfig (30%) ──┬─ Mixer/Monitor (30%) ─┐
│                    │                      │                        │
│  Modulation matrix │  Selected cell       │  Master preview        │
│  param rows ×      │  LFO config          │  Crossfader            │
│  CV columns        │  Oscilloscope        │  Deck A/B/C monitors   │
│                    │                      │  Deck controls         │
└────────────────────┴──────────────────────┴────────────────────────┘

┌─ AssetBrowserPanel (bottom, 70% width) ───────────────────────────┐
│  Patch library / playlists / play queue browser                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## Component Dependency Graph

```
UIManager  (orchestrator — owns backends, runs per-frame render loop)
├── UITheme         singleton — fonts, settings, feature flags
├── PopupManager    all modal dialogs (exit, MIDI warn, deck confirm)
├── MenuBar         top menu bar; no state of its own
├── PatchGridState  shared transient state token (passed by reference)
├── PatchGridPanel  modulation matrix grid (object singleton)
├── CellConfigPanel modulator editor + oscilloscope (object singleton)
├── MixerMonitorPanel
│   └── DeckControlPanel  (via injected lambda)
│       └── DeckPresetBrowser A / B
├── AssetBrowserPanel   (object singleton)
├── SettingsPanel       (object singleton)
├── AudioEnginePanel    (object singleton)
└── ImGuiFileBrowser deckA, deckB
```

All panels receive the live `Mixer` reference **at draw time** — they do not store it as a field.
`UIManager.currentMixer` is the only frame-to-frame reference.

---

## UIManager

**File**: `UIManager.kt`  
**Role**: Owns the ImGui/GLFW/GL3 backends. Calls `render(mixer, w, h)` once per frame.

Key responsibilities:
- Initialises `ImGuiImplGlfw` + `ImGuiImplGl3` at startup, disposes at shutdown
- Drains the MIDI CC event queue each frame; dispatches queue-advance signals (MIDI CC, keyboard, CV threshold)
- Deferred font rebuild: sets `pendingFontSize` in one frame; rebuilds atlas + GL texture at the **top of the next frame** (after `newFrame()` but before `ImGui.newFrame()`) — mid-frame rebuilds corrupt the font atlas
- Deferred popup open: sets `pendingOpenSettings` / `pendingOpenAudioEngineMonitor` flags; `ImGui.openPopup()` is called one frame later at root ID-stack level so it lands outside child windows
- `companion object` exposes `triggerDeckDragDrop` as a static entry point callable safely from the rendering thread

Notable owned state:
- `patchState: PatchGridState` — the shared selection/undo token
- `popupManager: PopupManager`
- `defaultStyle: ImGuiStyle` — clean style snapshot; **freed in `dispose()` via `.destroy()`** (native object)

---

## PatchGridState

**File**: `PatchGridState.kt`  
**Role**: Plain data class (not a singleton) carrying all transient patch-grid state.  
Passed by reference from `UIManager` into `MenuBar`, `PatchGridPanel`, `CellConfigPanel`,
`MixerMonitorPanel`, and `DeckControlPanel`.

Key fields:
| Field | Purpose |
|-------|---------|
| `selectedCell: PatchCellId?` | Currently selected grid cell (paramKey + cvSourceId) |
| `selectedParam: ModulatableParameter?` | Backing parameter for the selection |
| `undoStack` | Max-30 snapshots of modulator state |
| `isMidiLearnMode` | Whether MIDI learn is active |
| `midiLearnTarget: MidiLearnTarget?` | Sealed class: `GridCell` or `BaseValueSlider` |
| `activeTopTab` | "Deck A" / "Deck B" / "Deck C" |
| `activeDeck*SubTab` | Per-deck sub-tab selection |

---

## PopupManager

**File**: `PopupManager.kt`  
**Role**: Owns all modal popups — exit confirm, MIDI warning, per-deck unsaved-changes confirm.

Pattern: `UIManager` sets a `pendingOpen*` flag and immediately calls `ImGui.openPopup(id)` in
the *same* frame at the root ID-stack level (outside child windows). `PopupManager.draw*()` then
calls `ImGui.beginPopupModal(id)` to actually render it.

`PendingDeckAction` enum has 8 states (NONE, NEW, LOAD_FILE, LOAD_PRESET, DRAG_DROP, MOVE, COPY,
SWAP) — one set per deck (A, B, C). `clearAction()` resets all three after dismiss.

---

## UITheme

**File**: `UITheme.kt`  
**Role**: Global singleton. Fonts, settings persistence, feature flags.

Fonts are loaded from classpath resources (Inter + JetBrains Mono + Lucide icon font merged via
`setMergeMode(true)`). Six semantic levels: H1–H3, BODY, CAPTION, CODE.

**Critical**: Font `ByteArray` fields (`regularBytes`, `boldBytes`, etc.) and the `iconRange:
ShortArray` are stored as class fields **intentionally** — to prevent GC collection while native
ImGui holds a pointer into them. `setFontDataOwnedByAtlas(false)` tells native code not to
attempt to free these JVM-owned arrays. Removing these fields = crash on next font rebuild.

`ImFontConfig` objects are created per font merge and `.destroy()`'d immediately after
`addFontFromMemoryTTF` — they are not reused.

`withFont(level) { ... }` is an `inline` function — zero-allocation on the render path.

Settings are read from / written to `lsd-settings.properties` on disk.
Flags consumed across the UI: `audioEngineEnabled`, `backgroundVideoEnabled`, `cleanModeEnabled`,
`assetBrowserMode`, `tooltipsEnabled`, `maxFps`, etc.

---

## CvTheme

**File**: `CvTheme.kt`  
**Role**: Lookup-only singleton. Maps CV source ID strings to RGBA color values.  
Used by `PatchGridPanel`, `PatchGridRenderer`, `CellConfigPanel`, and `OscilloscopeDrawer` to
give each CV source a consistent color across all panel contexts.

No state; no native allocations.

---

## AssetBrowserPanel

**File**: `AssetBrowserPanel.kt` (~1115 lines)  
**Role**: File/playlist browser in the bottom section. Handles navigation, search, rename,
delete, folder creation, drag-to-deck, playlist export.

Owns six `ImString` fields (`renameBuffer`, `folderNameBuffer`, `searchBuffer`,
`newPlaylistNameBuffer`, `renamePlaylistBuffer`, `exportQueueNameBuffer`) — all `ImString(256)`,
allocated at object construction and reused every frame. This is the correct pattern.

Navigation state is a `LibraryView` sealed class with three variants:
`PlaylistsRoot`, `SpecificPlaylist(playlist)`, `Patches(dir)`.

---

## Im-type Allocation Rules

See the `imgui_memory_management` skill for full details. Short version:

- **`ImString`** — always a **class/object-level field**, never a local. Widgets read back the
  previous value through the same pointer; a new instance is always empty.
- **`ImBoolean` / `ImInt`** — field if the value must persist between frames; local is acceptable
  only in panels shown infrequently (e.g. `SettingsPanel`). Never use as a local in panels
  rendered every frame.
- **`MemoryStack`** — not used in `ui/` at all. It belongs in OpenGL/GLFW interop code only.
- **`ImGuiStyle`** — native object; call `.destroy()` in `dispose()`.
- **`ImFontConfig`** — native object; call `.destroy()` immediately after use.

---

## Adding a New Panel

1. Allocate any `ImString`/`ImBoolean` fields at object/class level — not inside the draw function.
2. Accept `Mixer` as a draw-time parameter — do not store it as a field.
3. Accept `PatchGridState` by reference if you need selection/undo state.
4. Register your draw call in `UIManager.render()`.
5. If your panel opens a popup, follow the deferred-open pattern: set a `pendingOpen*` flag in one
   frame; call `ImGui.openPopup(id)` at root level; call `beginPopupModal(id)` in your draw.
