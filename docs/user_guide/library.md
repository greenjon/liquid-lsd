# Library & Playlist Management

Liquid LSD includes a dedicated **Library Management System** for organizing visual presets, building live performance playlists, and managing the play queue.

---

## Toggling Modes & Resizing

Liquid LSD features flexible workspace dock layouts:

1. **Performance Mode (Default)**: Preset Grid, Cell Config, and Mixer / Monitor panels with the Library docked at the bottom.
2. **Library View Modes**: Switch between **Half Height**, **Full Height**, and **Hide** via:
   - **Mode Buttons**: Click `[FULL]`, `[HALF]`, or `[HIDE]` in the Library's top menu bar.
   - **Spacebar Quick-Cycle**: Press <kbd>Space</kbd> (when not typing in a search bar or text input) to seamlessly ping-pong cycle through:
     $$\text{HIDE} \longrightarrow \text{HALF} \longrightarrow \text{FULL} \longrightarrow \text{HALF} \longrightarrow \text{HIDE}$$
   - **Title Bar Drag-to-Resize**: Click-drag anywhere in the empty area of the Library title/menu bar to smoothly adjust the Library's height. Dragging all the way down collapses it into **Hide** mode. Double-clicking empty space in the title bar snaps the Library back to 50% Half height.

---

## Panel Layout in Library Mode

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│ [FULL] [HALF] [HIDE]  │  [🔒] [A] [B] [BG] [PV]  │  [Q] [BGQ]  │  [ + ▾]          │
├───────────────────┬───────────────────┬───────────────────┬───────────────────────┤
│ Presets       [+] │ Playlists [+] [••]│ Queue     [Clear] │ BG Queue      [Clear] │
├───────────────────┼───────────────────┼───────────────────┼───────────────────────┤
│ [🔍 Search...]    │ [Select Playlist▾]│ [AUTO-VJ] [🔁][🔀]│ [AUTO-BG] [🔁][🔀]   │
│ Preset Alpha      │ 1. Preset Alpha   │ ▶ 1. Preset 1     │ ▶ 1. Nebula BG        │
│ Preset Beta       │ 2. Preset Beta    │   2. Preset 2     │   2. Dark Grid BG     │
└───────────────────┴───────────────────┴───────────────────┴───────────────────────┘
```

---

## Unified Menu Bar Action Toolbar & Quick Audition Latch

Located in the top Library Menu Bar, the action toolbar provides a unified control strip operating on whichever preset is currently selected across all four columns:

- **`[ 🔒 ]` Quick Audition Latch**: Toggles sticky audition mode.
  - When turned **ON**, it automatically latches to **Deck PV** (Preview) by default. Clicking any preset or navigating with **`↑` / `↓` arrow keys** in any column instantly loads the preset into the latched deck for rapid auditioning.
  - Clicking any deck button (`A`, `B`, `BG`, `PV`) while locked switches the latch target.
  - Clicking the *currently latched* deck button unlatches it.
  - Turning the padlock **OFF** clears all latches and restores standard selection mode.
- **`[ A ]`**: Loads the currently selected preset into Deck A (or latches Deck A when audition mode is armed).
- **`[ B ]`**: Loads the currently selected preset into Deck B (or latches Deck B when audition mode is armed).
- **`[ BG ]`**: Loads the currently selected preset into Deck BG / Background (or latches Deck BG when audition mode is armed).
- **`[ PV ]`**: Previews the currently selected preset in Deck PV / Preview (or latches Deck PV when audition mode is armed).
- **`[ Q ]`**: Appends the currently selected preset to the A/B Play Queue (automatically dimmed if the selection is already in the A/B Play Queue).
- **`[ BGQ ]`**: Appends the currently selected preset to the Background Queue (automatically dimmed if the selection is already in the Background Queue).
- **`[ + ▾ ]`**: Opens a dropdown to create a new blank preset on any deck (`Deck A`, `Deck B`, `Deck BG`, `Deck PV`).

> [!TIP]
> Selecting any preset in **any of the 4 columns** (Preset Library, Playlist Editor, A/B Queue, BG Queue) focuses that patch globally across the Library for unambiguous one-click routing.

---

## 1. Preset Library (Column 1)

The Left column displays the complete pool of all available presets discovered across `library/presets/`.

### Features & Navigation
- **Search & Tag Filter**: Type into the top search bar to filter presets in real-time by preset name or assigned tags.
- **Clean List View**: Preset rows display clean typography without cluttered inline buttons.
- **Double-Click**: Automatically loads the preset into the inactive deck based on crossfader position.
- **Keyboard Shortcuts**:
  - `↑` / `↓` Arrows: Navigate through presets across columns (auto-loading to the latched deck if Audition Lock `[🔒]` is armed) without focus interruption.
  - `1`: Load selected preset into **Deck A**.
  - `2`: Load selected preset into **Deck B**.
  - `3`: Load selected preset into **Deck BG** (Background).
  - `4`: Preview selected preset on **Deck PV** (Preview).
  - `Q`: Append selected preset to **A/B Play Queue**.
  - `Shift + Q`: Append selected preset to **Background Queue (BG)**.
  - `Delete` / `Backspace`: Delete selected preset from your library (with permanent deletion confirmation).
- **Context Menu Actions (Right-Click)**:
  - **Load to Deck A / B / BG / PV**: Instant deck routing.
  - **Add to A/B Queue / Background Queue**: Fast queue assignment.
  - **Add to '{Active Playlist}'**: Appends the preset directly into the currently selected playlist.
  - **Rename / Edit Tags… (`F2`)**: Opens the metadata modal to edit filename and tags.
  - **Duplicate Preset…**: Opens the metadata modal pre-populated with `<name>_copy`.
  - **Delete**: Permanently deletes the preset from disk with a confirmation modal.

---

## 2. Playlist Editor (Column 2)

The Middle column allows inspecting and arranging setlists side-by-side with your preset library.

### Header & Playlist Switcher
- **Playlist Dropdown Combo**: Click to instantly switch the active playlist from all discovered `.lsdset` files.
- **`[ + ]` Create New Playlist**: Prompts for a playlist name and creates a new empty setlist file.
- **`[ ••• ]` Playlist Actions Menu**:
  - **A/B Play Queue Actions**:
    - **Play now in A/B Queue (and replace queue)**: Loads and starts Auto-VJ playback of the entire playlist in the A/B queue.
    - **Insert into A/B Queue after current**: Inserts all playlist presets into the live A/B queue after the current track.
    - **Add to the bottom of A/B Queue**: Appends the playlist to the end of the A/B queue.
  - **Background Queue Actions**:
    - **Play now in BG Queue (and replace queue)**: Loads and starts Auto-BG playback of the entire playlist in the Background queue.
    - **Insert into BG Queue after current**: Inserts all playlist presets into the live Background queue after the current track.
    - **Add to the bottom of BG Queue**: Appends the playlist to the end of the Background queue.
  - **File Operations**:
    - **Rename...**: Renames the active playlist file on disk.
    - **Clone**: Duplicates the active playlist as `<name>_copy.lsdset`.
    - **Delete**: Permanently removes the playlist file.

### Playlist Preset Rows & Auto-Save
- **Auto-Save on Edit**: Any modification (adding presets, dragging to reorder, or removing items) automatically saves to disk.
- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the playlist and press `Delete` or `Backspace` to remove it from the playlist.
- **Drag Reordering**: Drag items up and down with mint-green insertion line feedback.
- **Item Context Menu (Right-Click)**:
  - **Load to Deck A / B / BG / PV**: Routes the preset to the specified deck.
  - **Add to A/B Queue / Background Queue**: Appends the preset to either queue.
  - **Remove from playlist**: Removes the preset from the playlist.
  - **Delete preset from library...**: Permanently deletes the preset file from your library.

---

## 3. A/B Play Queue (Column 3)

The 3rd column displays the live sequence of presets for main A/B deck Auto-VJ and playback.

- **Auto-VJ (`BOT` / `BOT_OFF` Robot Icon Button)**: Enables automated cycling through queue presets at configured crossfade intervals.
- **Repeat & Shuffle**: Controls queue cycle loop and randomization.
- **Export Button**: Exports the current live A/B queue as a new `.lsdset` playlist file.
- **Item Context Menu (Right-Click)**:
  - **Load to Deck A / B / BG / PV**: Instantly loads the queued preset to any deck.
  - **Add to Background Queue**: Routes the queued preset over to the Background queue.
  - **Remove from queue**: Removes the preset from the active queue.
  - **Delete preset from library...**: Permanently deletes the preset file.
- **Keyboard Shortcut (`Delete` / `Backspace`)**: Select an item in the play queue and press `Delete` or `Backspace` to remove it from the queue.

---

## 4. Background Queue (Column 4)

The 4th column manages automated cycling and sequential playback for the dedicated background layer (`Deck BG`).

- **Auto-BG (`BOT` / `BOT_OFF` Robot Icon Button)**: Enables automatic cycling through background presets.
- **Dip-to-Black Transitions**: Smoothly fades out the current background, loads the new preset, and fades back in beneath the live foreground.
- **Repeat & Shuffle**: Continuous loop and shuffle for background visuals.
- **Export Button**: Exports the current background queue as a new `.lsdset` playlist file.
- **Double-Click & Right-Click Play**: Trigger instant cuts or dip-to-black transitions on demand.
- **Item Context Menu (Right-Click)**:
  - **Play (Dip to Black) / Play (Instant Cut)**: Triggers playback with or without dip-to-black fade.
  - **Load to Deck A / B / PV**: Routes the background preset to other decks.
  - **Add to A/B Queue**: Routes the background preset over to the live A/B play queue.
  - **Remove from BG queue**: Removes the preset from the background queue.
  - **Delete preset from library...**: Permanently deletes the preset file.

---

## 3. Drag-and-Drop Matrix

| Dragged Item | Target Destination | Resulting Action |
|---|---|---|
| **Preset from Left Column** | Between presets in Playlist Editor | Inserts preset at hovered slot with mint-green guideline |
| **Preset from Left Column** | Empty playlist area / bottom | Appends preset to the end of the playlist |
| **Preset within playlist** | Reorder within active playlist | Reorders preset sequence |
| **Preset from Left Column** | Queue panel | Adds preset to live queue |

---

## 4. Handling Missing Items

If a playlist references a preset file that was moved or deleted from disk:
- The preset row appears in **red** with `[!] (missing)`.
- Right-click the missing row to remove the reference.
