# Plan: Patch Tags & Global Scratchpad

> This is a companion plan to the main UI Enrichment plan.
> Scope here is deliberately deferred — these ideas need more thought before implementation.

---

## Part 1 — Patch Tags (formerly "Idea C")

### Background

`DeckPatchDto` already has a `tags: List<String>` field, noted in a comment as "Phase 2 — tag
list; defaults to empty for backward compat". The field is serialised in `.lsdpatch` JSON files
but is never written or read by any UI code today.

`AssetBrowserPanel.kt` renders the flat patch file list and already has a right-click context
menu on items.

### Goals

1. **Visibility**: Show tags as small coloured pills next to patch names in the asset browser.
2. **Editability**: Let users add/remove tags via a lightweight inline editor (not a full modal).
3. **Filtering**: Optional — add a tag filter bar at the top of the asset browser so VJs can
   narrow the patch list by tag during set planning.
4. **Persistence**: Tags live entirely inside the existing `.lsdpatch` JSON — no new file or
   persistence layer needed.

### Proposed Changes

#### [MODIFY] `AssetBrowserPanel.kt`
- Render tag pills (small coloured rectangles with text) after each patch name.
- Right-click context menu on a patch item gains `Edit Tags…` which opens an inline
  single-line `ImGui.inputText` populated with comma-separated tags (e.g. `ambient, drop, buildup`).
- Parse, trim, and deduplicate on confirm.
- Optional: tag filter bar above the file list — `ImGui.inputText` search + active tag chips
  with `[×]` to remove.

#### [MODIFY] `PatchManager.kt`
- Ensure tags round-trip correctly through save/load (`DeckPatchDto.tags` already serialises;
  just need to make sure save/load plumbs the field correctly).
- The `saveDeckPreset` path must not silently drop tags when re-saving an existing patch.

#### [MODIFY] `PatchModels.kt` / `Deck.toDto()`
- Verify (or fix) that `Deck.toDto(name, tags)` is called with the correct tags list when
  saving — currently the `tags` argument defaults to `emptyList()`, which would wipe tags
  on every save.

### Open Questions

> [!IMPORTANT]
> **Tag colour scheme**: Should tag pill colour be driven by a fixed palette (cycle through N
> colours), by the tag string's hash, or by a user-assigned colour picker per tag?

> [!IMPORTANT]
> **Tag vocabulary**: Free-form strings (any text), or a restricted vocabulary (user-defined
> global tag list)? Free-form is simpler but risks typo fragmentation (e.g. "bass-drop" vs
> "bass drop").

> [!IMPORTANT]
> **Filter semantics**: AND (must match all selected tags) vs OR (any selected tag)? OR is
> usually more useful for live browsing.

> [!IMPORTANT]
> **Interaction with the preset save flow**: When saving via "Save As…" in the browser, should
> the tag editor be part of that flow (a field in the save dialog), or entirely separate?

---

## Part 2 — Global Scratchpad (formerly "Idea F")

### Background

The old "global patch" concept has been retired. There is no longer a meaningful "global note"
to attach to. However, the underlying VJ need is real: **performers want a persistent, glanceable
scratchpad** that is independent of any specific patch — essentially replacing the sticky note on
the monitor.

### Revised Framing

A standalone **session scratchpad**: free-form text that persists across app restarts, has no
relationship to any deck or patch, and is always accessible.

### Goals

1. A togglable floating `ImGui` window (or panel) called **"Notes"** or **"Scratchpad"**.
2. A multi-line text area with no structure imposed — just type.
3. Persisted to `~/.liquid-lsd/scratchpad.txt` (plain text; no JSON ceremony).
4. Toggle via a menu item (e.g. `View → Scratchpad`) and/or a keyboard shortcut.

### Open Questions

> [!IMPORTANT]
> **Window style**: Floating always-on-top vs docked into the existing 3-panel layout? Floating
> is simpler but can obscure the visuals. Docked means finding a layout slot for it.

> [!IMPORTANT]
> **Relationship to patch notes**: Should the scratchpad window also surface the current deck's
> patch note (read-only or editable) alongside the free-form area? This would make it a single
> "notes hub" rather than two separate places to look.

> [!IMPORTANT]
> **Auto-save strategy**: Save on every keystroke, on focus loss, on app exit, or on a debounce
> timer (e.g. 2 s after last keystroke)?

> [!NOTE]
> The scratchpad deliberately does NOT include "quick links to loaded preset names" from the
> original Idea F — that context is already visible in the deck control panels.
