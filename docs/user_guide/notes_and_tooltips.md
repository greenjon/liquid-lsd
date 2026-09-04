# Documentation & Note System

Liquid LSD features an integrated engine documentation registry, multi-tier user note system, and rich interactive tooltips. Performers and sound designers can reference visual parameters in real time and attach custom notes to engines, patches, and individual modulation parameters.

---

## Engine Documentation & Rich Tooltips

Every visual generator, feedback chain control, and mixer parameter in Liquid LSD includes built-in engine documentation supplied by `SourceDocRegistry`.

### Interactive Parameter Tooltips
Hovering over any parameter row in the Preset Grid or any slider control across the interface (when tooltips are enabled) displays a multi-section tooltip:

```
┌─────────────────────────────────────────────────────────────┐
│ Lobes                                                       │
│ Range: 1.0 to 16.0  (Default: 4.0)                          │
│ Live: 8.3  (base 4.0  +4.3)                                 │
├─────────────────────────────────────────────────────────────┤
│ Dictates rotational symmetry and repeating petal count      │
│ for the mandala geometry.                                   │
├─────────────────────────────────────────────────────────────┤
│ 📝 Great for high-energy drop transitions when mapped to Bass│
└─────────────────────────────────────────────────────────────┘
```

1. **Parameter Title & Range**: Displays the human-readable name, valid numeric bounds, and factory default value.
2. **Live Value Breakdown**: Shows the evaluated current value alongside a real-time breakdown of base value and net CV modulation delta (`Live: X (base Y + mod Z)`).
3. **Engine Description**: Built-in description explaining what the parameter mathematically or visually controls.
4. **User Parameter Note**: Displays your custom user note (in amber text) if a note has been attached to this parameter.

---

## The Three-Tier Note System

Liquid LSD provides three distinct scopes for notes:

| Note Tier | Scope | Storage Location | Lifetime |
|-----------|-------|------------------|----------|
| **Global Source Notes** | Visual Engine / Source (e.g. Mandala, Gyroid) | `~/.liquid-lsd/source-notes.json` | App-global; persists across preset changes and app restarts |
| **Preset Notes** | Loaded Visual Preset (.lsd) | Inside `.lsd` preset file (`presetNotes`) | Saved/loaded per preset file |
| **Parameter Notes** | Specific parameter within a preset | Inside `.lsd` preset file (`paramNotes`) | Saved/loaded per preset file |

---

## Creating & Editing Notes

### 1. Parameter Notes
- **How to edit**: Hover over any parameter row in the Preset Grid and click the **⋮** button (or right-click the parameter name) to open the row context menu, then select **Add/Edit Parameter Note…**.
- **Display**: Appears inside the hover tooltip for that parameter whenever the preset is loaded.
- **Persistence**: Saved directly inside the `.lsd` file.

### 2. Global Source Notes
- **How to edit**: In Deck Control, open the **Deck Menu** popup and select **📝 Add/Edit Source Note…**.
- **Display**: Appears inside the hover tooltip when hovering over the source selection button in Deck Control.
- **Persistence**: Stored globally in `~/.liquid-lsd/source-notes.json`. Perfect for keeping personal cheat-sheets or performance tips for specific shader engines (e.g. "Gyroid works best with low gain feedback").

### 3. Preset Notes & Deck Monitor Labels
Each active Deck Monitor panel (Deck A and Deck B) features a soft blue-white **Preset Name Label** below the preview screen:

```
┌───────────────────────────────────────────────────────────┐
│                    [ Video Preview ]                      │
└───────────────────────────────────────────────────────────┘
 🎨 Sunset_Mandala.lsd *
```

- **Hovering**: Displays a detailed tooltip containing:
  - Full preset file path and name
  - Last saved timestamp and preset DTO schema version (`Last saved: 2026-08-13 04:30  v1`)
  - The preset note body (or a hint to right-click if empty)
- **Editing**: Right-click the preset name label and select **📝 Add/Edit Preset Note…** to open the note modal.
- **Untitled Presets**: If a deck has no saved preset loaded, the label displays `Untitled` in dim grey. Notes cannot be attached to unsaved untitled decks until saved to disk.

---

## Note Editor Modal

Selecting any edit note command opens the `NoteEditorModal` dialog:

- **Multi-line Editing**: Supports multi-line text input up to 2048 characters.
- **Keyboard Shortcuts**: Press `Ctrl+Enter` to quickly save and close, or `Escape` to cancel without saving changes.
- **Clean Memory**: Runs with a pre-allocated text buffer to ensure native JVM memory safety during live performance.
