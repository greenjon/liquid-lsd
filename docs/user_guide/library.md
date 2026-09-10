# Library & Playlists

The Library is your setlist staging ground. Use it to organize presets, build `.lsdset` playlists, audition looks without flashing the live stage, and queue automated transitions.

---

## Docking, Resizing & Shortcuts

The Library docks along the bottom of the workspace:

* **Cycle View Modes (<kbd>Space</kbd>)**: When not typing in a text field, tap <kbd>Space</kbd> to step through dock sizes:
  `Hide` $\rightarrow$ `Half Height` $\rightarrow$ `Full Screen` $\rightarrow$ `Half Height` $\rightarrow$ `Hide`
* **Double-Click Title Bar**: Instantly snaps the Library to 50% split (Half Height).
* **Window Controls**: Click `[-]` to minimize to the bottom dock, or `[□]` to maximize/restore.

---

## Panel Layout

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│              │  [🔒] [A] [B] [BG] [PV]  │  [Q] [BGQ]  │  [ + ▾]  │         [-] [□]│
├───────────────────┬───────────────────┬───────────────────┬───────────────────────┤
│ Presets       [+] │ Playlists [+] [••]│ BG Queue [<][▶][>]│ Queue        [<][▶][>]│
├───────────────────┼───────────────────┼───────────────────┼───────────────────────┤
│ [🔍 Search...]    │ [Select Playlist▾]│ [🔁][🔀][Export][x│ [🔁][🔀][Export][Clear│
│ Preset Alpha      │ 1. Preset Alpha   │ ▶ 1. Nebula BG    │ ▶ 1. Preset 1         │
│ Preset Beta       │ 2. Preset Beta    │   2. Dark Grid BG │   2. Preset 2         │
└───────────────────┴───────────────────┴───────────────────┴───────────────────────┘
```

---

---

## Action Toolbar & Quick Audition

The top toolbar acts on whatever preset is selected in **any** column:

* **`[ 🔒 ]` Audition Latch**: Locks a deck for instant previewing.
  * Latches to **Deck PV** (Preview) by default. When active, clicking presets or scrolling with **`↑` / `↓`** arrows immediately loads them into the audition deck
  * Click `A`, `B`, or `BG` while locked to change the target deck. Click the active deck again or uncheck `[ 🔒 ]` to return to standard selection mode
* **`[ A ]` / `[ B ]` / `[ BG ]` / `[ PV ]`**: Send the selected preset to that specific deck.
* **`[ Q ]` / `[ BGQ ]`**: Add the selected preset to the A/B Queue or Background Queue.
* **`[ + ▾ ]`**: Create a new blank preset on any deck.
  
  ### Global Selection Hotkeys
  
  Select any preset in any column and use these quick keys:
* **`1` / `2` / `3` / `4`**: Route to **Deck A**, **Deck B**, **Deck BG**, or **Deck PV**.
* **`Q` / `Shift + Q`**: Add to **A/B Queue** or **Background Queue**.
* **`Ctrl + F` or `/`**: Jump focus directly to preset search (opens Library automatically if hidden).
* **`Esc`**: Clear search filter and return navigation focus to the table.
* **`Delete` / `Backspace`**: Remove the preset from the active playlist/queue, or confirm permanent deletion from disk.

---

## 1. Preset Library (All Presets)

The master browser for all presets saved in `library/presets/`.

* **Smart Search**: Filter instantly by name or tag.
* **Double-Click**: Automatically loads the preset into whichever deck is currently inactive on the crossfader.
* **Dependency Warning (`[!]`)**: Presets appear with a red `[!]` badge if they depend on an offline subsystem (like disabled MIDI or audio engines). Hover the badge to inspect missing components; presets can still be loaded normally
* **Context Menu (Right-Click or `⋮`)**: Rename, add tags (`F2`), duplicate (`<name>_copy`), append to queues, or permanently delete.

---

## 2. Playlist Editor (Setlists)

Create and manage custom `.lsdset` setlists side-by-side with your preset pool.

* **Switch Setlists**: Select any playlist from the top dropdown.
* **Menu Actions (`•••`)**:
  * **Play Now**: Replace the current live queue with this playlist and trigger playback immediately.
  * **Insert After Current**: Slide the playlist into the live queue right after the active preset.
  * **Add to Bottom**: Append the playlist to the end of the queue.
* **Reorder & Auto-Save**: Drag and drop presets up and down with mint-green insertion feedback. Every change auto-saves to disk immediately

---

## 3. Background Queue (Deck BG)

Dedicated sequencing for the background layer (`Deck BG`).

* **Transport (`<`, `▶`/`⏸`, `>`)**: Step through presets or trigger continuous Auto-BG playback with dip-to-black fades.
* **Repeat (`🔁`) & Shuffle (`🔀`)**: Loop the queue indefinitely or randomize playback order.
* **Export**: Save the current background sequence as an `.lsdset` playlist.
* **Instant Cuts vs. Fades**: Double-click or right-click to choose between an immediate cut or a smooth dip to black.

---

## 4. A/B Play Queue (Live Auto-VJ)

The live playback sequence driving the main crossfader and stage output[cite: 3, 5].

* **Auto-VJ Transport (`<`, `▶`/`⏸`, `>`)**: Plays through the queue sequentially, triggering automated transitions between Deck A and Deck B at your configured timing intervals[cite: 3, 5].
* **Loop & Shuffle**: Set continuous loop playback (`🔁`) or non-repeating shuffle (`🔀`).
* **Export**: Save an improvised live queue as a permanent playlist file.

---

## Drag-and-Drop Staging

| From                          | To                             | Result                                        |
|:----------------------------- |:------------------------------ |:--------------------------------------------- |
| **Preset Library**            | Between items in Playlist      | Inserts at hovered position (mint-green line) |
| **Preset Library**            | Empty space at playlist bottom | Appends to the end of the playlist            |
| **Playlist Item**             | Up / Down within Playlist      | Reorders the setlist                          |
| **Preset Library / Playlist** | A/B or BG Queue                | Adds preset to live playback queue            |

---

## Missing Items

If an `.lsdset` references a preset that was renamed, moved, or deleted from disk, the row displays in red with **`[!] (missing)`**. Right-click the row to prune the dead link from the playlist
