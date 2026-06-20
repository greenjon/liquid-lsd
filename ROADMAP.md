Project Roadmap: Spirals Desktop VJ Software
Overall Goal:
Develop a Linux desktop application for real-time audio-reactive parametric visuals, featuring a flexible CV modulation matrix and a dual-deck mixer, optimized for live performance.

Core Principles:
Performance: Low-latency audio and high-framerate OpenGL rendering.
Modularity: Clean separation of concerns (audio, CV, rendering, UI).
Extensibility: Easy to add new CV sources, visual modules, or effects.
User Experience (UI): Intuitive grid-based patcher for modulation, clear feedback, single-screen preference.
Reliability: Robust error handling, especially for real-time systems.

Important Architectural Note:
While Mandalas are the initial first-class visual source, the system architecture must support multiple types of visual sources in the future (e.g., live video feeds, 3D objects, shader toys, particle systems, etc.). The Deck abstraction should be designed to work with any visual source type, not just Mandalas.
Phases & Milestones:
Phase 1: Core Graphics & UI Foundation (Current)
Goal: Establish stable OpenGL rendering pipeline with FBOs and a functional ImGui UI.

1.1. Project Setup & Basic Window

Initialize GLFW window, OpenGL context.
Integrate ImGui for basic UI.
Logging setup.
Status: COMPLETE (Main.kt shows ImGui, basic menu, status panel).
GitHub Sync: COMPLETE
1.2. FBO System

FBO.kt: Generic Framebuffer Object wrapper for off-screen rendering.
Geometry.kt: Utility for fullscreen quad (for blitting textures).
Main.kt update: Render test content (e.g., solid color or simple shape) to an FBO, then blit FBO to screen.
Status: COMPLETE
Milestone: FBO rendering confirmed, ImGui showing FBO status.

1.3. Shader Management

Shader.kt: Class for loading, compiling, linking GLSL shaders (.vert, .frag).
Error logging for shader compilation/linking.
Main.kt update: Use Shader class for blitting.
Status: COMPLETE
Milestone: Shaders loaded via Shader.kt, screen still renders FBO content.
1.4. Basic Mandala Rendering
Status: COMPLETE

Mandala.kt: Class encapsulating core mandala parameters and rendering logic (port initial GLSL shaders and parameter structure from Android).
Renderer.kt: Main OpenGL renderer class.
Main.kt update: Render a single Mandala to FBO_Master, then blit FBO_Master to screen.
Milestone: A static mandala visual appears on screen, controllable by hardcoded values.
Phase 2: Dual Deck & Mixer
Goal: Implement two independent visual rendering chains with blending and master output.

2.1. Deck System
Status: COMPLETE

Deck.kt: Represents a single visual source (e.g., a Mandala). Contains its own FBOs (for ping-pong feedback).
IMPORTANT: Design Deck to use a VisualSource interface/abstract class, allowing different source types (Mandala, VideoFeed, 3DScene, etc.) to be swapped in.
Initial Deck will render a Mandala, but architecture must support future source types.
Milestone: Two Deck instances exist, each rendering a distinct Mandala to its own FBO.
2.2. Ping-Pong Feedback Integration
Status: COMPLETE

Extend Deck.kt to integrate ping-pong FBOs for feedback effects (zoom, rotate, blur, hue shift).
Port existing feedback.frag shader from Android.
Milestone: Each Deck can render a mandala with independent feedback effects.
2.3. Mixer & Crossfade
Status: COMPLETE

Mixer.kt: Combines outputs from Deck A and Deck B into FBO_Master.
Port mixer.frag shader (crossfade, blend modes).
Renderer.kt update: Orchestrate Deck rendering and Mixer compositing.
Main.kt update: Implement ImGui slider for crossfade.
Milestone: Can smoothly crossfade between two distinct, audio-reactive mandalas.

Phase 3: Core Modulation System (Complete)
Goal: Implement audio analysis, CV sources, and the parameter modulation mechanism.

3.1. Audio Engine (JACK)
Status: COMPLETE

JackClient.kt: Wrapper for JNAJack, handling audio input via JACK.
DSP.kt: Core signal processing (FFT, Biquad filters, RMS amplitude, onset detection).
AudioEngine.kt: Orchestrates JACK input and DSP, updates CVRegistry with audio-derived signals (amp, bass, mid, high, onset).
Milestone: Audio input processed, console logs real-time CV values (amplitude, bands, onset).

3.2. CV Registry & Basic Sources
Status: COMPLETE

CVSource.kt: Interface for all CV signals.
CVRegistry.kt: Central repository for all active CV signals, their current values, and history.
Implement BeatClock, LFO (time-based), and SampleAndHold as core CV sources.
Milestone: All core CV sources generate values and are accessible in CVRegistry.

3.3. Modulatable Parameters
Status: COMPLETE

Parameter.kt: Represents a single visual parameter (ModulatableParameter from Android). Holds base value, list of CvModulators, and evaluated value.
Modulation.kt: Defines CvModulator (CV source, weight, operator, bypass).
Integrate Parameter.kt into Mandala.kt and Mixer.kt parameters.
Milestone: Parameters in Mandala and Mixer are now ModulatableParameters.
Phase 4: UI & Interactivity
Goal: Develop the grid-based patcher UI and integrate MIDI input.

4.1. Hierarchical Parameter System
Status: COMPLETE

PatchGridState.kt: Manages selection state and group collapse state.
Parameter paths implemented as strings (e.g. "Deck A/Geometry/L1").
Milestone: Parameters are organized hierarchically in collapsible tree groups.

4.2. CV Grid Patcher UI
Status: COMPLETE

PatchGridPanel.kt: Renders the full grid — parameter rows grouped under Mixer/Deck A/Deck B
with Geometry, Color, and Feedback sub-groups; CV source columns (AMP, BASS, MID, HIGH,
FLUX, ONSET, ACCENT, BEAT, LFO, RAND). Groups are collapsible.
Active cells show a faint circle + animated dot tracking live CV value
(6 o'clock = 0/1, noon = 0.5). Bypassed cells shown in grey.
Milestone: Interactive grid displayed, all parameters and CV sources visible.

4.3. Modulation Editor (Cell Config Panel)
Status: COMPLETE

CellConfigPanel.kt: Opens when a grid cell is clicked. Shows the (parameter → CV) pair,
BYPASS/ACTIVE toggle, DELETE button, Operator (ADD/MUL), Weight slider, and
CV-specific controls ported from Android ModulatorRow:
  - BEAT: waveform, subdivision, phase offset, slope/duty
  - LFO: waveform, speed range, period, phase offset, slope
  - RAND (S&H): subdivision, phase offset, glide
  - Audio CVs (amp/bass/etc.): weight and operator only
Live value bar shown at bottom of panel.
Milestone: Can click a cell, configure modulation, see live value feedback.
4.4. MIDI Input

MIDIManager.kt: Wrapper for javax.sound.midi, detects MIDI devices.
Maps MIDI CC messages to new CV sources in CVRegistry.
UIController.kt update: Display active MIDI CCs as CV columns.
Milestone: MIDI controller can modulate visual parameters.
Phase 5: Patch Management & Refinements
Goal: Implement saving/loading of presets and general polishing.

5.1. Patch Serialization

Patch.kt: Data class representing a complete patch (all parameters, CV sources, modulations).
PatchManager.kt: Uses kotlinx.serialization (JSON) to save and load Patch objects.
Integrate into File menu in Main.kt.
Milestone: Can save/load entire visual configurations.
5.2. Randomization (Optional but High Value)

RandomSetGenerator.kt: (Port logic from Android) Generate new mandala/set parameters based on constraints.
Integrate into Deck.kt or a "Generate" button in the UI.
Milestone: Can generate new mandala variations with a single click.
5.3. Performance & Debugging

OpenGL profiling (e.g., glGetDebugMessageLog).
Optimize shader performance.
Refine audio processing for efficiency.
Milestone: Smooth performance at target framerate, minimal audio latency.
5.4. Documentation & Release

Update README.md with usage instructions.
Add inline code documentation.
Prepare for Linux distribution.
Milestone: First stable release candidate.
