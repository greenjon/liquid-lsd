# RFC: Relative Knob Dragging & Cursor Locking / Restoration

- **Status**: Proposed / Under Review
- **Author**: Antigravity Pair Programming
- **Target Subsystem**: `ui/MacroKnobWidget.kt`, `ui/UIManager.kt`, `Main.kt`, `ui/PreferencesPanel.kt`
- **Related Docs**: [UI Architecture](ui.md), [ARCHITECTURE.md](../../ARCHITECTURE.md), [DECISIONS.md](../../DECISIONS.md)

---

## 1. Problem Statement & Motivation

In Liquid LSD, real-time performance often involves serial adjustments across banks of rotary controls—such as tweaking an FX Chain's Super Knob, adjusting individual slot Dry/Wet and parameters in Focus Mode, or sweeping Macro Bank controls.

### Current Behavior
Currently, [`MacroKnobWidget`](../../src/main/kotlin/llm/slop/liquidlsd/ui/MacroKnobWidget.kt) uses standard Dear ImGui coordinate tracking (`io.mousePos.x - dragStartX`, `io.mousePos.y - dragStartY`). As the performer drags:
1. The mouse cursor visually traverses across the screen (often 100–200 pixels vertically or diagonally).
2. When the performer releases the mouse button, the cursor remains at the displaced screen position—often inches away from the knob that was just manipulated.
3. To adjust the next knob in the row or rack, the performer must physically locate and reposition the cursor back across the screen.

### Proposed Improvement
When click-dragging a knob:
1. Hide the cursor on drag initiation.
2. Keep the cursor confined or anchored at its initial press position while accumulating relative motion deltas.
3. On mouse release, show the cursor at the exact coordinates where the click originated.

This behavior mimics industry-standard DAW and pro-audio tools (Ableton Live, Bitwig Studio, Native Instruments, Serum, FabFilter, Blender) and drastically streamlines live manipulation by preserving hand-eye alignment and spatial muscle memory.

---

## 2. Architectural Approaches

Three technical patterns exist for implementing this behavior in a GLFW + Dear ImGui application:

| Approach | Description | Advantages | Drawbacks & Platform Risks |
| :--- | :--- | :--- | :--- |
| **1. Naive Hide + Warp on Release** | Hide cursor via `ImGui.setMouseCursor(None)` or `glfwSetInputMode(GLFW_CURSOR_HIDDEN)`. Let the OS cursor wander invisibly. On mouse up, call `glfwSetCursorPos(window, startX, startY)`. | Minimal code changes; retains existing absolute `io.mousePos` math. | **Fails completely on Linux Wayland**; cursor freezes at screen edges; can wander onto other monitors. |
| **2. Pointer Lock / Relative Mode (`GLFW_CURSOR_DISABLED`)** | On drag start, switch GLFW window cursor mode to `GLFW_CURSOR_DISABLED`. The OS locks the cursor, hides it, and emits raw relative motion deltas. On mouse release, revert to `GLFW_CURSOR_NORMAL`. | **Fully supported on Wayland** via `zwp_pointer_constraints_v1`; infinite sweep (never hits monitor edge); standard across games and 3D tools. | Requires reading raw deltas (`io.mouseDelta` or GLFW cursor callback) rather than absolute positions; requires decoupling ImGui cursor state during grab. |
| **3. Continuous Per-Frame Warping** | Hide cursor and warp pointer back to `(startX, startY)` every frame during drag, accumulating frame-by-frame deltas. | Infinite sweep on X11 and Windows. | **Fails on Wayland**; causes mouse acceleration jitter and high event overhead on macOS. |

---

## 3. Comprehensive Pitfalls & Edge Cases

### A. Linux Wayland Security Restrictions (`GLFW_PLATFORM_WAYLAND`)
* **The Constraint**: Liquid LSD runs natively on Linux under Wayland (see Wayland guards in `Main.kt` and `WindowFrameController.kt`). Under the Wayland security model, **compositors strictly prohibit client applications from warping the global pointer** in normal mode. Calling `glfwSetCursorPos()` in `GLFW_CURSOR_NORMAL` or `GLFW_CURSOR_HIDDEN` generates `GLFW_FEATURE_UNAVAILABLE` or is silently dropped.
* **Impact**: If Approach 1 or 3 is used on Wayland, the cursor will disappear during drag, but upon mouse release it will **fail to jump back**, reappearing inches away or on another display.
* **Resolution**: Wayland compositors (GNOME Mutter, KDE KWin, Sway, Hyprland) only support pointer confinement/locking via the `zwp_pointer_constraints_v1` and `wp_relative_pointer_manager_v1` protocols. GLFW binds these protocols **strictly through `GLFW_CURSOR_DISABLED`**. Therefore, relative mode (Approach 2) is the only robust cross-platform path that functions correctly on Wayland.

### B. The Screen Edge Deadzone (Monitor Boundary Clipping)
* **The Constraint**: If the cursor is merely hidden but allowed to travel across the desktop (Approach 1):
  * Upper racks, FX rows, and menu-adjacent knobs are positioned near the top of the window.
  * Dragging upward to increase a knob value causes the invisible OS pointer to hit the top of the physical monitor (`Y = 0`) after 20–40 pixels.
  * Once the OS cursor hits the display edge, the operating system stops generating delta events.
  * **Failure mode**: The performer continues moving their physical mouse, but the knob freezes partway through its sweep.
* **Multi-Monitor Bleed**: An unconfined invisible cursor can travel onto an external display (e.g. Liquid LSD's secondary projection window `secondaryWindow`) or click into an OS dock or taskbar.

### C. Click vs. Drag Disambiguation (Micro-Movement Jitter)
* **The Constraint**: `MacroKnobWidget` multiplexes multiple mouse interactions:
  * **Left Click**: Select knob / inspect bindings (`onSelect()`).
  * **Right Click**: Toggle MIDI / modulation learn (`onToggleLearn()`).
  * **Middle Click**: Reset knob to default value (`0.5`).
  * **Scroll Wheel**: Fine increment adjustment.
* **Edge Case**: Physical human fingers rarely execute a pure 0-pixel click. On mouse-down, 1–2 pixels of micro-motion almost always register. If the cursor is hidden immediately on `isActivated`, an ordinary click to select or inspect will cause the cursor to vanish for 40–80 ms and reappear—producing an annoying visual blink.
* **Resolution**: Implement a **drag deadzone** (e.g. 3–4 pixels of Euclidean travel, matching `ImGui.isMouseDragging(0, 3.0f)`). The cursor remains visible as an arrow during mouse-down, and only switches to hidden/relative mode once intentional drag displacement is established.

### D. ImGui Event Pipeline & 1-Frame Teleport / Hover Bleed
* **The Constraint**: Dear ImGui processes window and cursor inputs during `glfwPollEvents()` at the start of each frame.
* **Hover Bleed**: If the cursor travels invisibly across the UI, any UI elements underneath its path must not trigger hover styling, cursor changes, or tooltips. (ImGui's active widget system suppresses most click routing, but complex custom hitboxes or child windows may still test mouse coordinates).
* **Release Timing**: When the cursor is restored to origin on mouse release, the position change is applied to GLFW. ImGui updates `io.mousePos` on the subsequent event poll. Any logic must ensure the mouse release is registered at the origin or after the position has stabilized, preventing a 1-frame "teleport" artifact where an unrelated widget briefly evaluates hover.

### E. Absolute Pointing Hardware (Touchscreens, Stylus / Digitizers)
* **The Constraint**: Liquid LSD supports touch hardware (see `TouchConsoleController.kt`). Performance rigs frequently use Microsoft Surface devices, touch monitors, or Wacom pens.
* **Failure Mode**: Absolute digitizers enforce hardware-level coordinates on every touch contact. Attempting to lock or warp cursor coordinates on a touchscreen causes severe stuttering, driver fighting, or wild coordinate jumps.
* **Resolution**: Relative cursor locking must be bypassable when touch events are detected, and must have a clear preference toggle in user settings.

### F. Window Focus Loss & Emergency Recovery Invariants
* **The Constraint**: If the performer is dragging a knob and:
  * An OS notification steals focus,
  * The user presses `Alt+Tab` or the `Super` key,
  * A modal prompt opens (e.g. unsaved preset dialog),
  * The application is minimized or secondary window interacts...
* **Critical Safety Invariant**: The application must **never leave the user with a trapped or invisible cursor** on their desktop.
  * In `Main.kt` (`glfwSetWindowFocusCallback`), if the window loses focus while a knob drag is active, `GLFW_CURSOR_NORMAL` and `ImGuiMouseCursor.Arrow` must be forcefully restored immediately.
  * `MacroKnobWidget` must cleanly abort any relative drag state if `!io.mouseDown[0]` or if focus is lost.
  * **Widget-side state is global, not per-instance**: `MacroKnobWidget` is a singleton `object`, and its drag-tracking fields (`activeKnobId`, `dragStartX`, `dragStartY`, `dragStartValue`) are module-level, not per-widget. This is safe today because only one knob can be dragged at a time, but it means the focus-loss recovery hook must explicitly reset `activeKnobId = null` (and the lock-related fields added below) in addition to restoring GLFW cursor mode — resetting only the OS cursor state and leaving a stale `activeKnobId` behind would let the next frame's `isActive && activeKnobId == id` check misfire.

### G. High-DPI and Content Scaling
* **The Constraint**: GLFW cursor coordinates (`glfwGetCursorPos`, `glfwSetCursorPos`) operate in virtual screen coordinates (window points), whereas framebuffer dimensions and physical display pixels may be scaled (e.g. 2x on macOS Retina, 1.25x/1.5x on Wayland fractional scaling).
* Any stored coordinates must strictly remain in window points matching `ImGui.getIO().mousePos`.

---

## 4. User Preference & Ergonomics

* **Visual Feedback**: When the cursor is hidden, the knob must clearly convey that it is being manipulated. `MacroKnobWidget` already rotates the indicator needle and illuminates an active border ring (Electric Cyan / accent tint).
* **Configurable Setting**: In `PreferencesPanel` (under `Category.GENERAL`):
  ```
  [x] Lock cursor to knob while dragging (default: true)
  ```
  This provides an immediate escape hatch for users with touch devices, specialized trackballs, or personal preferences for standard cursor movement.

---

## 5. Recommended Implementation Plan

This plan follows the codebase's existing patterns rather than introducing new ones: preferences persist through `UITheme.settings` + `AppPreferencesStore`, and actual GLFW calls are issued only from the code that already owns the window handle (`UIManager`/`Main.kt`), not from the stateless `MacroKnobWidget` object.

1. **Preference Configuration** (follows the existing `autoVjDirtyBehavior`/`theme` precedent, not a new config class):
   * Add `lockCursorOnKnobDrag: Boolean = true` as a field on the `UITheme.settings` data class (`UITheme.kt`), with a `var` get/set wrapper on `UITheme` that goes through `settings.copy(...)`, mirroring `UITheme.autoVjDirtyBehavior` (`UITheme.kt:134-136`).
   * Load/save it as a `Properties` entry in `AppPreferencesStore.kt`, mirroring the existing `autoVjDirtyBehavior`/`theme` read (`AppPreferencesStore.kt:215-235`) and write (`AppPreferencesStore.kt:333`) blocks.
   * Add a checkbox in `PreferencesPanel.kt`'s `drawGeneralPreferences` (`PreferencesPanel.kt:194`).

2. **Lock-request state in `MacroKnobWidget`** (widget signals intent; it does not touch GLFW itself):
   * Add module-level fields alongside the existing `dragStartX`/`dragStartY`/`dragStartValue` (`MacroKnobWidget.kt:76-78`): `isDragLocked: Boolean`, `lockOriginX: Float`, `lockOriginY: Float`.
   * On `isActivated` (mouse press): record `dragStartX`/`dragStartY`/`dragStartValue` as today (`MacroKnobWidget.kt:131-135`); do **not** engage the lock yet.
   * Each frame while `isActive`: compute Euclidean travel from `(dragStartX, dragStartY)`. Once it exceeds a ~3px deadzone (matching `ImGui.isMouseDragging(0, 3.0f)`) and `UITheme.lockCursorOnKnobDrag` is enabled, set `isDragLocked = true` and latch `lockOriginX/Y` to the current `io.mousePos`. This satisfies the click-vs-drag disambiguation in Section 3.C without any GLFW call happening inside the widget.
   * Expose the request as a tiny public surface `MacroKnobWidget` already needs: e.g. `val wantsCursorLock: Boolean` (true only on the frame lock should engage) and `val wantsCursorRelease: Boolean` (true on the frame the drag ends while `isDragLocked`), plus `lockOriginX`/`lockOriginY` getters. Continue driving `applyDragDelta` off `io.mousePos - dragStartX/Y` exactly as today — under `GLFW_CURSOR_DISABLED` these deltas keep accumulating correctly since GLFW/ImGui still updates a virtual `mousePos`.

3. **Pointer grab execution in `UIManager`** (this is the piece the original plan omitted — only `UIManager`/`Main.kt` hold `windowHandle`):
   * After the frame's widget drawing (where `UIManager.render` already calls into `PerformanceMatrixPanel` → `MacroKnobWidget.draw`), check `MacroKnobWidget.wantsCursorLock` / `wantsCursorRelease` and act on `windowHandle` (`UIManager.kt:32`):
     * On `wantsCursorLock`: `glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED)`.
     * On `wantsCursorRelease`, **in this exact order** (see Section 3.A — reversing this order breaks the Wayland restore):
       1. While still `GLFW_CURSOR_DISABLED`, call `glfwSetCursorPos(windowHandle, lockOriginX, lockOriginY)`. This sets GLFW's virtual position / the Wayland pointer-constraint position hint while the constraint is still active.
       2. Only then call `glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL)` to release the lock. Calling `glfwSetCursorPos` *after* switching to `GLFW_CURSOR_NORMAL` is silently dropped on Wayland (Section 3.A) and must not be relied on.
   * Bypass the whole lock path when a touch/stylus contact is active (Section 3.E) — gate on the same touch-detection signal `TouchConsoleController` uses, if applicable to the pointer driving this drag.

4. **Global Focus & Interruption Safety**:
   * Hook into the existing `glfwSetWindowFocusCallback` in `Main.kt` (`Main.kt:308`) to force `GLFW_CURSOR_NORMAL` immediately if focus leaves the application while locked, and additionally call a new `MacroKnobWidget.abortDrag()` to clear `activeKnobId`/`isDragLocked` (see Section 3.F note above) — resetting cursor mode alone is not sufficient.

5. **Docs**:
   * Per this project's release convention, land `docs/developer/ui.md`, `docs/release_notes.md`/`RELEASE_NOTES.md`, and the relevant `PreferencesPanel` tooltip alongside the code change, plus a `DECISIONS.md` entry — not deferred to a follow-up.
