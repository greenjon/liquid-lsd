# Proposal: Unified Title Bar & Custom Window Frame (CSD)

## Status: Proposed / Backlog

## Overview
This document describes the design and implementation roadmap for replacing the traditional OS window title bar with a modern, integrated **Unified Header & Title Bar** (Client-Side Decorations / CSD) in Liquid LSD Desktop, similar to Discord, Spotify, and modern DAWs.

The unified title bar merges the OS title bar, window control buttons (minimize, maximize/restore, close), application menus, status widgets, and live performance monitors into a single sleek, vertical-space-efficient top bar (~32–36px).

---

## Architectural Design: Option A + B

The architecture combines a cross-platform custom window implementation with a persistent settings fallback.

```
+---------------------------------------------------------------------------------------------------------------+
| [Logo] Liquid LSD | File  Randomize  MIDI  Settings  Audio  Color  Out  Help  Tools |     | REC • LIVE • DSP • FPS | _ ◻ ✕ |
+---------------------------------------------------------------------------------------------------------------+
  [Left: Brand + Menus]                                                                 [Center Drag]   [Right: Status & Controls]
```

### 1. Window Mode & Decoration Control
- **Setting**: `AppSettings.framelessWindow: Boolean = true` (persisted in `lsd-settings.properties`).
- **Default Mode (Option A - Frameless CSD)**:
  - Created with `glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)`.
  - Top bar renders custom minimize, maximize/restore, and close buttons.
  - Middle space serves as the primary window dragging zone and double-click maximize toggle.
  - Invisible perimeter border handles cursor changes and edge/corner resizing.
- **Fallback Mode (Option B - Native OS Decorations)**:
  - When `framelessWindow = false`, window is created with `glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)`.
  - Standard OS title bar is managed by the desktop environment / window manager.
  - The custom window action buttons (`_ ◻ ✕`) are hidden in the menu bar.
  - Ideal fallback for strict Linux tiling window managers (e.g. i3, sway, bspwm).

---

## Component Architecture & Proposed Changes

### 1. Configuration & Persistence (`UITheme.kt`)
- Add `framelessWindow: Boolean = true` to `AppSettings`.
- Implement serialization/deserialization in `loadSettings()` and `saveSettings()`.
- Add `SettingsPanel` toggle under General / Display: *"Frameless Window (Custom Title Bar) [Requires restart]"*.

### 2. Window Lifecycle (`Main.kt`)
- Query `UITheme.settings.framelessWindow` before calling `glfwCreateWindow()`.
- Pass `GLFW_DECORATED = GLFW_FALSE` (frameless) or `GLFW_TRUE` (native).
- Attach window frame controller to window handle.

### 3. Window Frame Controller (`WindowFrameController.kt` - New)
- **Window Dragging**: Tracks mouse delta in non-interactive top-bar space (`!ImGui.isAnyItemHovered()` and `!ImGui.isAnyItemActive()`) and updates `glfwSetWindowPos`.
- **Double-Click Maximize**: Toggles between `glfwMaximizeWindow` and `glfwRestoreWindow`.
- **Perimeter Edge Resizing**:
  - ~5px perimeter zone around the window border when unmaximized.
  - Updates cursor (`GLFW_HRESIZE_CURSOR`, `GLFW_VRESIZE_CURSOR`, etc.).
  - Resizes window via `glfwSetWindowSize` / `glfwSetWindowPos`, respecting `800x600` limits.

### 4. Unified Header Bar (`MenuBar.kt`)
- Render app branding / logo on the far left.
- Render compact menus (`File`, `Randomize`, `MIDI Map`, `Settings`, `Audio Engine`, `Color`, `Output Window`, `Help`, `Tooltips`).
- Keep center area clean for window dragging.
- Render telemetry HUD (recording indicator, live broadcast, beat phase dots, BPM, DSP latency, FPS, CPU%).
- Render window control buttons on the far right (when `framelessWindow == true`):
  - **Minimize** (`_`): `glfwIconifyWindow(windowHandle)`
  - **Maximize / Restore** (`◻` / `❐`): `glfwMaximizeWindow` / `glfwRestoreWindow` based on `glfwGetWindowAttrib(windowHandle, GLFW_MAXIMIZED)`
  - **Close** (`✕`): `uiManager.triggerExitFlow()` (triggers unsaved changes prompt if dirty).

---

## Verification & Test Plan

1. **Window Dragging**: Click and drag empty space in the top bar; verify smooth repositioning without lag.
2. **Maximize / Restore**: Double-click top bar or click `◻` button; verify maximize and restore bounds transition cleanly.
3. **Window Controls**: Verify minimize iconifies to taskbar; verify close triggers the unsaved changes modal.
4. **Edge Resizing**: Hover over window borders; verify resize cursors appear and dragging resizes the window while adhering to minimum 800x600 limits.
5. **Fallback Toggle**: Disable frameless mode in Settings, restart app, and verify native OS title bar renders correctly with window buttons hidden in the menu bar.
