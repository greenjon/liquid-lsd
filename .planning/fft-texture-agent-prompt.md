# Agent prompt: make the audio FFT texture actually live

Repo: liquid-lsd (Kotlin, LWJGL, imgui-java). Work on a new branch off main; commit in small steps; no Claude/Co-Authored-By attribution.

## Problem
The FFT texture given to shaders (`audioFFT`, `iChannel0` row 0) is never populated in the live app. A measurement during the tour work read 0.000.
Code reading: `AudioEngine.copyMagSpectrum` reads `BeatTrackerEngine.magSpectrum`, which is only written by
`computeComplexSpectralDifference` in `audio/BeatTrackerEngine.kt`, and no live path calls that (the beat path uses
`processMultiBand`). The waveform row (row 1) does work. No shipped source uses the FFT row today, so nobody noticed.

## Do
1. Confirm the diagnosis by running (`./gradlew run --args="--no-audio"` is not enough: use the tour feeder,
   `scripts/tour_determinism_check.sh` + `scripts/tour_spike_script.json`, which feeds a test beat through `TourAudioFeeder`,
   or a unit test calling `AudioEngine.processAudio` with a sine) and printing max(magSpectrum) / the uploaded texture row.
2. Find where the texture is uploaded (grep `copyMagSpectrum`, `audioFFT`, `iChannel0` in `audio/`, `rendering/`) and define the
   intended contract from the docs (`docs/user_guide/visual_sources.md`, ISF docs, DECISIONS.md): bin count, windowing, scaling
   (linear/dB, 0..1), sample-rate dependence, smoothing/peak behavior.
3. Implement a proper per-block magnitude spectrum in the audio path (windowed FFT of the mono mix, normalized to 0..1, light
   smoothing), computed on the audio thread, published via a volatile/double-buffered array (no allocation in the audio callback;
   the codebase is zero-alloc on hot paths: match the surrounding style). Do not change beat detection results: add a separate
   path or reuse the same FFT without altering `processMultiBand` outputs; add a test that beat/BPM outputs on a fixed signal are unchanged.
4. Tests: sine at a known frequency peaks in the expected bin; silence is zero; texture row is non-zero after processAudio;
   deterministic for a fixed input (the tour renderer relies on bit-identical results, so no wall-clock or random inputs).
5. Visual check: write a throwaway ISF (not committed unless useful) that draws bars from the FFT row, render 3 s with the tour
   renderer and the test beat, look at a frame (PNG), confirm bars move.
6. Docs per repo convention ("done" = code + both release-notes files + user_guide/dev docs): RELEASE_NOTES.md,
   docs/release_notes.md, the audio/ISF docs describing `audioFFT`.

## Constraints
- Feature freeze for v1.0 is polish/stability: keep the change small and low-risk; call out CPU cost per block.
- Tour determinism must stay intact: re-run `scripts/tour_determinism_check.sh` on a scene that uses the FFT and report identical hashes.
- Report honestly: ran vs only read, test results, CPU cost, files changed. Under 500 words.
