---
status: resolved
trigger: "when opening the window opens windowed and does this weird think where it freaks out and flashes vertical scrollbar and resizes right pane content until i resize the window. see if you can reproduce it via testing and fix it"
created: 2026-07-06T18:20:19-07:00
updated: 2026-07-06T18:23:53-07:00
---

# Debug Session: window-startup-layout-jitter

## Symptoms

- expected_behavior: Opening the app should show a stable window and stable right-pane layout without scrollbar flicker or repeated content resizing.
- actual_behavior: On startup, the app opens windowed and the right pane repeatedly resizes while a vertical scrollbar flashes until the window is manually resized.
- error_messages: No error message reported; visual/layout instability only.
- timeline: Reported while running current desktop app.
- reproduction: Start the app and observe the initial windowed UI before manually resizing the window.

## Current Focus

- hypothesis: The first few ImGui layout frames are using unstable framebuffer/window dimensions or auto-fit window sizing, causing the right-pane child content to cross the vertical-scroll threshold repeatedly until GLFW receives a resize event.
- test: Inspect startup window sizing, framebuffer-size handling, and right-pane/child sizing logic; add a focused regression test for the sizing calculation if it can be isolated.
- expecting: A code path that uses dynamic available height/width without a stable initial viewport or uses auto-sized content in a way that oscillates around scrollbar visibility.
- next_action: gather initial evidence
- reasoning_checkpoint:
- tdd_checkpoint:

## Evidence

- timestamp: 2026-07-06T18:21:10-07:00
  observation: Main loop used glfwGetFramebufferSize for both OpenGL viewport/rendering and UIManager.render dimensions.
  implication: ImGui layout was receiving framebuffer-pixel dimensions even though the ImGui GLFW backend lays out windows in logical GLFW window coordinates; high-DPI/startup framebuffer changes can make setNextWindowSize pane math disagree with ImGui's actual display size.
- timestamp: 2026-07-06T18:21:55-07:00
  observation: Mixer / Monitor content originally derived all preview heights from available width and could exceed the right pane's startup height, causing vertical scrollbar appearance to change the content width.
  implication: The right-pane monitor layout was on a scrollbar threshold; scrollbar width changed the next frame's available width and preview heights, producing visible resize/flicker.
- timestamp: 2026-07-06T18:23:20-07:00
  observation: Focused MixerMonitorLayoutTest and full Gradle test suite pass after separating framebuffer/window sizes and using scaled monitor preview heights.
  implication: The extracted layout calculation covers the startup pane sizing regression, and the broader test suite found no regression.

## Eliminated

## Resolution

- root_cause: Startup UI layout mixed framebuffer-pixel dimensions with ImGui logical window coordinates, while the right-pane monitor content sat on a vertical-scrollbar threshold and resized as scrollbar visibility changed.
- fix: Main.kt now passes GLFW window size to UIManager.render while keeping framebuffer size for OpenGL; the Mixer / Monitor pane uses a pure layout calculator that reserves scrollbar width and scales preview heights to fit the available pane height.
- verification: Ran `.\gradlew.bat test --tests "llm.slop.spirals.ui.MixerMonitorLayoutTest"` and `.\gradlew.bat test`; both passed.
- files_changed: src/main/kotlin/llm/slop/spirals/Main.kt; src/main/kotlin/llm/slop/spirals/ui/MixerMonitorLayout.kt; src/main/kotlin/llm/slop/spirals/ui/MixerMonitorPanel.kt; src/main/kotlin/llm/slop/spirals/ui/DeckControlPanel.kt; src/test/kotlin/llm/slop/spirals/ui/MixerMonitorLayoutTest.kt
