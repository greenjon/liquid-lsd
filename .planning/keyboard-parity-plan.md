# Keyboard as a virtual controller (plan)

Started 2026-10-08. Status: PLAN, nothing built. Target: v1.1 (v1.0 feature freeze), except the "v1.0-safe" items at the end.

## Goal and decisions (user)

- Goal: a user **without a Twister** can drive the app without the mouse for everything: perform, browse, load.
- Existing keyboard shortcuts may be **removed or moved**; no need to preserve them.
- The 4x4 letter grid idea is wanted: `1234 / QWER / ASDF / ZXCV` = the Twister's 16 knobs.

## Core idea

The `CommandRegistry` (`control/Command.kt`) was already built for "an input device, keyboard shortcut or OSC message".
Every Twister action is a command: `knob.<n>` (RELATIVE), `knob.<n>.press` / `.press_alt`, `nav.button.<n>` / `.alt`,
plus `NavSurface` (`button`, `browseStep`, `browseAccept`, `browseSend`) and `KnobSurface` (`turn`, `primary`, `secondary`,
`showPage`, `stepPair`). So the keyboard becomes **another input device that dispatches those same commands**, not a new
set of features. Whatever the Twister can do (and whatever it learns to do later) the keyboard inherits, with the same
context-dependent meanings (Perform / pair view / Library / picker / dirty-deck modal).

Not verified yet: whether to feed the registry through a keyboard adapter with its own binding table, or to extend
`ControllerProfile` (MIDI-event based, see `ControllerRuntime.handle(event: MidiEvent)`) to accept key events. The second would
give rebinding via the planned phase 5 profile UI for free. Decide in step 1.

## Proposed layout (tentative, tune by use)

Grid, physically matching the Twister's 4x4 (knob 1 top-left, knob 16 bottom-right):

```
1 2 3 4        knobs  1- 4
Q W E R        knobs  5- 8
A S D F        knobs  9-12
Z X C V        knobs 13-16   (V = browse cursor knob 16, Z = the clock/send knob 13)
```

Select-then-nudge, because keys have no rotation:
- **Press a grid key** = select that knob (focus ring on it; also counts as "last touched" for the picker button).
- **Left / Right** (or `-` `=`, key repeat gives continuous travel) = turn the selected knob. **Shift** = fine (the
  Twister's hold-switch fine, x0.1). **Ctrl** = coarse, optional.
- **Hold a grid key + Left/Right** nudges that knob without changing the selection (quick tweak). Mouse wheel over the
  selected knob optional.
- **Enter** = knob switch primary tap (`knob.n.press`), **Shift+Enter** = secondary (`press_alt`).
- **Up / Down** = previous/next row of 4 (selection), so the grid is reachable one-handed too.

Pages and side buttons (the Twister's bank button and 3 side buttons, +shift):
- **F1-F4** = perform pages `ab / bgpv / mixer / master` (direct). **Page Up / Page Down** = step pages (Twister wrapPages).
- **Esc** = `nav.button.1` (back, already the Esc stack via `BackNavigation`).
- **Tab / Shift+Tab** = `nav.button.2` / `.2.alt`: in Perform opens Library, in Library next/previous tab. This is the
  "smart Tab": same key moves you between the two big contexts, then within them.
- **Backquote (`) / Shift+`** = `nav.button.3` / `.3.alt`: picker for the last-touched row, then pane next/prev in Library.

Browse contexts (Library, picker): mirror the Twister, grid is inert except the browse knob and send knobs:
- **Up / Down** = `browseStep(+-1)` (knob 16 turn), **Enter** = `browseAccept`, **Shift+Enter** = shifted accept.
- Send knobs (row three + knob 13) as taps: `browseSend(A/B/BG/PV/MASTER)`; keys per target to settle (e.g. the keys of the
  physical knobs they map to).
- Because the grid is inert here, **plain letters are free for typeahead**, and `/` or `Ctrl+F` focus search as now.

Everything else (needs a home, since the plain letters are now the grid):
- Tap tempo: the clock knob (Z) switch tap, same as Twister knob 13 push. Retires `T`.
- Output view (was `F`), background video (`B`), OSC map (`O`), queue-add (`Q`): move to `Ctrl+`/`Alt+` or F-keys. F5+
  are free. Fullscreen stays F11, Preferences stays Ctrl+P, record stays Ctrl+R, undo/save/copy/paste stay Ctrl+.
- Queue transport (prev/next): Page Up/Down is taken by pages; leave on `Ctrl+Left/Right` or keep the `queueKeyTrigger`
  pref values but fold them into the registry.
- Crossfader: Transitions K1 already mirrors it; add `,` `.` nudge and Home/End snap later (check `CrossfadeControl`).

## Discoverability (needed, not optional)

- Visible focus ring on the selected knob, and Edit/Library zone clearly marked.
- **Key-cap badges**: a toggle (hold `?` or F-key) overlays each perform knob with its grid key letter, and shows the
  current side button/Tab/Esc meanings in a small legend (same content as the Twister context table).
- Tooltips list the key on every control that has one. Docs: user guide section "Keyboard", replacing the stale table in
  `your_workspace.md`.

## Prerequisites and risks

1. Fold the shortcuts that bypass `ShortcutManager` (Space, queue transport, CapsLock `touchConsoleController`) into
   one registry; drop the single-letter ones listed above.
2. `wantTextInput` guard applied uniformly; grid keys must never fire while typing in a text field. Esc must reliably
   leave a text field first.
3. Conflicts: ImGui keyboard nav (`NavEnableKeyboard`) also uses Tab and arrows. Either turn nav off for the main views and
   keep it only in modals/Preferences, or make our handler run first and consume the key. Needs a spike.
4. Keyboard layouts: letters differ on AZERTY/QWERTZ. Use physical key positions (GLFW scancodes) for the grid.
5. Held-key state and key repeat: use the registry's `clearHeldState` on window focus loss.
6. The `QUEUE`/BG transport keys and `Space` cycle-mode need a deliberate new home (Library FULL/HALF toggle, etc.).

## Phases

1. **Spike + decision**: ImGui nav vs our handler on Tab/arrows; adapter vs ControllerProfile extension; scancode grid.
2. **Registry consolidation** (also v1.0-safe): move bypasses into `ShortcutManager`/registry, remove retired letters.
3. **Grid + select-nudge + pages + side buttons** on Perform, selection ring, wired to `KnobSurface`/`NavSurface`.
4. **Browse contexts**: arrows/Enter = browse knob, send taps, typeahead.
5. **Discoverability**: key-cap overlay, legend, tooltips, docs + both release-notes files.
6. **Rebinding UI** (with MIDI phase 5 if the profile route is chosen).
Tests: command-level (like existing `NavCommands`/`KnobCommands` tests) driven by key events -> assert the same
`KnobSurface`/`NavSurface` calls as the Twister path; plus a no-fire-while-typing test.

## v1.0-safe subset

Phase 2 (registry consolidation) and refreshing the stale shortcut docs. Everything else waits for v1.1.

## Decisions (2026-10-08, user)

- Select-then-nudge is the primary gesture.
- Tab = `nav.button.2` (Library toggle in Perform, tab-next in Library). Gut call; the user will judge by playing with it.
- Keyboard-mode toggle: **both** a menu bar button and a key (CapsLock today, `touchConsoleController`). When off, the grid
  and arrows do not capture keys, so typing is safe.
