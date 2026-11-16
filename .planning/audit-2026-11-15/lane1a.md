# Lane 1a: correctness of changes since audit-2026-10-29 (non-UI Kotlin)

Scope: `git diff audit-2026-10-29 HEAD -- src/main/kotlin` minus `ui/` (57 files). Read in full: audio/BeatPhaseLock.kt,
BeatHopper.kt, TransientDetector.kt, rendering/TextureReceiver.kt, TextureStreamer.kt, SyphonBridge.kt, SpoutApi.kt,
ExternalVideoDiscovery.kt, pipewire/PipeWireBridge.kt, pipewire/SpaPod.kt, utils/AtomicFile.kt, utils/UserNotice.kt,
cv/AudioSignals.kt, isf/ISFAudioInputs.kt. Read as diffs plus surrounding control flow: AudioEngine, BeatTrackerEngine,
AudioAgc, JackClient, CVRegistry, Evaluators, RealtimeRecorder, FFmpegProcessPipe, OfflineRenderStudio, AudioDecoder,
Main, presets/*, FxChain, Renderer, Shader, AudioTexture, ISFFilter, ISFVisualSource, OscCodec, the *Manager save/load
changes. SourceDocRegistry (strings only), SiteGenerator (deleted), Mixer (file names only) checked and clean.

## Checklist coverage

| Checklist item | Result |
|---|---|
| Thread 0: no file I/O, network, sleep, blocking joins, MIDI open/close | findings L1a-1 (join at exit deadlocks), L1a-7 (stat + JSON decode at 4 Hz from dirty check), L1a-10 (startup tree walk), L1a-11 (offline export decodes on Thread 0, pre-existing), L1a-12 (Thread.sleep/JACK open from Preferences, pre-existing). New audio device scan moved off Thread 0: clean (AudioEngine.kt:439-466, fields `@Volatile`). FFmpeg encoder probe warm-up: clean |
| Audio callback: no allocation, locks, logging, boxing | clean. AudioEngine.processAudio, BeatHopper.process, TransientDetector.process, HitScale.signal, AudioAgc.energy, BeatPhaseLock.process/steer, BeatTrackerEngine.stepTempoEstimate/estimateTempo/settleMetre/foldedStrike all use preallocated arrays and primitives; `updatePushedValue` with string literals is a ConcurrentHashMap get. JackClient.onShutdown only sets a flag. Comb analysis runs on its own `beat-phase-lock` thread. Estimate cost is spread over blocks (BeatTrackerEngine.kt:392-427) |
| Per-frame work: no allocation in frame loop / per-knob code | findings L1a-8 (Spout/Syphon/YUY2 per-frame JNA and array allocation), L1a-7 (dirty check allocation). ISFAudioInputs.apply, AudioTexture.bindAll, ISFVisualSource.applyOverBuiltins: clean |
| Cross-thread fields `@Volatile`/atomic/queue | finding L1a-2 (PipeWire output frame buffer shared without hand-off), L1a-6 (BeatPhaseLock worker restart). BeatPhaseLock ring/hopsWritten/latest/tempo, PipeWire receiver `agreed`, AudioEngine device cache, RealtimeRecorder `isFinishing`/`lastStartError`/`startNanos`/`Frame.capturedNanos`, JackClient `serverGone`: clean |
| Ops paths (DeckOps/FxOps/TransitionOps/QueueOps) | clean. First-run presets go through `DeckOps.request` (SessionSerializer.kt:413-418); DeckOps load failure now toasts; `PresetManager.forgetActive` only touches bookkeeping |
| Precedence (macro/Metaknob/Super Knob/MIDI) | clean within this lane. Only change: transitions no longer apply a Metaknob link (ISFFilter.kt:403-404); TransitionMacroSync change is a label only. FxChain dirty check now treats a missing Super Knob/Metaknob as "at rest", consistent with load |
| Persistence: atomic writes, unknown fields survive, versions bump | finding L1a-9 (AtomicFile pre-creates an empty target). All `writeText` sites in this lane moved to `AtomicFile.write` (presets, session, MIDI/OSC profiles, notes, ISF folder list, ISF overrides, generator defaults, macro export, FX shortlist, flame import). Unreadable files kept as `.unreadable` copies. No schema change in session/preset DTOs, so no version bump needed |
| Lifecycle: native resources released on every exit path | findings L1a-1, L1a-2, L1a-5 (receiver swizzle state), L1a-13 (Spout lister never released). PipeWire receiver/bridge start failure paths unlock and stop: clean. Syphon images released in `finally`: clean. JackClient drops a dead client without native calls (by design) |
| Error paths reach the user, no swallowed catch | findings L1a-4 (failed mux/encoder only logged; audio left in a hidden file), L1a-11 (ffmpeg exit code ignored), L1a-14 (silent catches). Preset save failures now toast with the reason (PresetRepository.kt:15-27): clean. Record start failure toasts (Main.kt:460-467): clean |

## Findings

### L1a-1 [bug] Quitting while recording deadlocks the app on exit
- Where: src/main/kotlin/llm/slop/liquidlsd/export/RealtimeRecorder.kt:436 (`@Synchronized fun stopRecording`), :479 (`if (waitForFinish) awaitFinish()`), :484-485 (`finishThread?.join(timeoutMs)`, default 0 = forever), :495 (`resetAudioPool()` on the finish thread), :136-137 (`@Synchronized private fun resetAudioPool`); caller Main.kt:767 `stopRecording(waitForFinish = true)`
- Evidence: `stopRecording` holds the `RealtimeRecorder` monitor while it joins the finish thread with no timeout. The finish thread, after joining the workers, calls `resetAudioPool()`, which is `@Synchronized` on the same object, so it blocks on the monitor Thread 0 holds. Neither can proceed. The same applies on the early-return branch (:438-439) if the user stopped a recording and quits before the finish thread reaches `resetAudioPool`.
- Impact: anyone who quits (window close, Ctrl-Q) while recording: the app hangs at "Shutting down..." and never saves the session (SessionSerializer.saveSession runs after this line), never leaves controller native mode, and the recording's temp files are never muxed. The process has to be killed.
- Fix: do not join while holding the monitor: make `stopRecording` collect state under the lock and do `awaitFinish()` after leaving it (split into a synchronized part and an unsynchronized wait), or drop `@Synchronized` from `resetAudioPool` (it runs only when no recording is live; `isFinishing` already blocks a new start). Add a test that calls `stopRecording(waitForFinish = true)` on an active recording with a timeout.
- Verified:

### L1a-2 [bug] PipeWire video output reads a buffer that the render thread overwrites and may free
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/pipewire/PipeWireBridge.kt:174-177 (`pendingFrame.set(buffer)`), :183-216 (drain copies from it on `Dispatchers.IO`), :221-224 (`stopServer` cancels the drain without waiting); src/main/kotlin/llm/slop/liquidlsd/rendering/TextureStreamer.kt:143-155 and :169-174 (stopServer then `pboPipeline.dispose()`); export/PboReadbackPipeline.kt:29, :83, :125 (`flippedBuffer` reused every frame, `memFree` on dispose)
- Evidence: `LinuxTextureBridge.update` passes `PboReadbackPipeline`'s single reusable `flippedBuffer` to `publishFrameBuffer`, which stores the reference. The drain coroutine `memCopy`s from it on an IO thread while Thread 0 writes the next frame into the same memory (`readFrameAsync`, PboReadbackPipeline.kt:85). On a size change or disable, `stopServer()` only calls `drainJob.cancel()` (cooperative, does not wait), then `pboPipeline.dispose()` frees the buffer while a drain already inside `drainPendingFrame` may still be copying from it. The same race lets a drain that captured `st`/`loop` before `stopServer` nulled them call `pw_stream_dequeue_buffer` on a destroyed stream once `stopServer` releases the loop lock (:190 vs :229-268).
- Impact: Linux users with a PipeWire output enabled: torn frames in the shared video every so often (two half-frames), and a possible native crash (use after free) when the output size changes (window or output-size change with "follow mixer size") or the output is switched off. The drain runs about 60 times a second, each copy several ms at 1080p, so the window is not tiny.
- Fix: hand off ownership instead of a reference: give the bridge its own two or three direct buffers and copy into one on Thread 0 (or swap buffers with the drain), and make `stopServer` wait for the drain job (`runBlocking { drainJob?.cancelAndJoin() }` or a lock around drain vs stop) before destroying the stream and before the caller disposes the pipeline.
- Verified:

### L1a-3 [bug] Tempo metre check can compare against a stale candidate from the previous estimate
- Where: src/main/kotlin/llm/slop/liquidlsd/audio/BeatTrackerEngine.kt:571-579 (`settleMetre`)
- Evidence: `var slot = if (count < METRE_CANDIDATES) count++ else METRE_CANDIDATES - 1` then the "list full" guard `slot == METRE_CANDIDATES - 1 && count == METRE_CANDIDATES && candidateDelays[slot] != 0 && score <= scoreTable[candidateDelays[slot]]`. On the sixth peak, `count++` has just made `count == 6`, so the guard fires although the list was not full, and `candidateDelays[5]` still holds the delay from the previous estimate. If the new peak scores no higher than that stale lag, it is skipped and the stale lag (not a peak this time, possibly past this pass's `availableMaxDelay`) is evaluated as candidate 6 by `metreValue` and can be chosen.
- Impact: occasional wrong metre pick on music with six or more comb peaks; low, but the tempo tracker is a headline v1.0 change, and the web port (web/beattrack.js) should be checked for the same code.
- Fix: test fullness before incrementing: `val full = count == METRE_CANDIDATES; if (full && score <= scoreTable[candidateDelays[last]]) continue; var slot = if (full) last else count++`. Add a vector test with more than six peaks.
- Verified:

### L1a-4 [polish] A failed recording mux is only logged; the audio is left in a hidden temp file
- Where: src/main/kotlin/llm/slop/liquidlsd/export/RealtimeRecorder.kt:187-188 (temp names `.tmp_vid_...`, `.tmp_aud_...wav` in the output folder), :536-541 (mux failure keeps `tempAud`), :470-475 (finish errors logged only), :546 (`encoder ok` logged only)
- Evidence: the finish thread runs after the UI has moved on. On a failed or timed-out mux it renames the video into place and keeps the audio as a dot-file the user's file manager hides; the only report is `logger.warn`. A failed encoder flush (`videoSuccess == false`) is also log-only.
- Impact: a performer who recorded a long set gets a silent MP4 and no hint that the sound exists beside it.
- Fix: `ToastOverlay.show(...)` (thread-safe) from `finish` on mux failure naming the kept WAV, and keep it under a visible name (e.g. `<name>.wav`).
- Verified:

### L1a-5 [bug] PipeWire receiver: a stream that switches from GRAY8 to an RGB format keeps showing grey
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/TextureReceiver.kt:336-344
- Evidence: for GRAY8 the texture's `GL_TEXTURE_SWIZZLE_G` and `_B` are set to `GL_RED`. When the agreed format later changes to RGBA/BGRA/RGBx/BGRx on the same stream, the same `localTextureId` is reallocated but only `SWIZZLE_A` is reset, so G and B still read red.
- Impact: rare (a source renegotiating from GRAY8 to colour mid-stream, e.g. a camera switching IR off); the picture stays monochrome until the source is reselected.
- Fix: in the non-gray branch set `SWIZZLE_G = GL_GREEN`, `SWIZZLE_B = GL_BLUE` (or set the full swizzle every realloc).
- Verified:

### L1a-6 [polish] BeatPhaseLock worker restart neither joins the old worker nor fences its last reading
- Where: src/main/kotlin/llm/slop/liquidlsd/audio/BeatPhaseLock.kt:265-268 (`stopWorker` interrupts, no join), :271-288 (`reset`); callers AudioEngine.kt:522-524, :879
- Evidence: `startClient` calls `stopWorker(); reset(); startWorker()`. An old worker that is inside `analyse()` when interrupted finishes it, publishing a `Reading` from pre-reset data into `latest` after `reset()` cleared it, and for a moment two threads can use the shared comb scratch (`raw`, `x`, `combBest`). `reset()` also writes the audio-thread-only fields from the caller's thread (Thread 0 or the watchdog), which is only safe because no callback is running then.
- Impact: low; a stale reading is ignored by `steer` until 20 consistent readings accumulate. Worth fixing for clean restarts.
- Fix: `worker?.let { it.interrupt(); it.join(500) }` in `stopWorker`, or tag readings with a generation counter bumped in `reset`.
- Verified:

### L1a-7 [polish] FX chain dirty check does a file stat and JSON decode on Thread 0 four times a second
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/FxChain.kt:508 and :531-532 (`defaultLinksFor` → `ISFAutoBindEngine.resolveDefault`), :520 and :535-538 (`comparable` copies); rendering/isf/ISFAutoBindEngine.kt:74-77, :175-178 (`loadOverride`: `File.exists()` with no negative cache), :86-98 (`shippedDefault` decodes JSON and overlays parameters every call)
- Evidence: `isDirty()` reruns `computeIsDirty()` every 250 ms per chain from the UI. For every slot whose baseline names no Metaknob link (all stock chains, per the comment at :507), it calls `resolveDefault`, which stats `library/.../<hash>.json` (absent for stock effects, so never cached) and rebuilds an `FxDefaultDto` from shipped JSON; `comparable()` copies each parameter and its modulator list.
- Impact: small but constant Thread 0 I/O and garbage (5 chains x up to 3 slots x 4 Hz), against the rule "no file I/O on Thread 0" and the per-frame allocation rule.
- Fix: compute the baseline's default links once when the baseline is set (`baselineDto` assignment) and store them; cache negative `loadOverride` lookups.
- Verified:

### L1a-8 [polish] Per-frame allocation in video sharing paths
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/SpoutApi.kt:31-33 (`Function.getFunction` + vararg array + boxed ints on every `sendTexture`/`receiveTexture`); SyphonBridge.kt:60-67, :130-134 (`pooled` and `msgSend` argument arrays per publish), :184-187 plus TextureReceiver.kt:137 (`IntArray` and `Pair` per received frame); TextureReceiver.kt:369 (`IntArray(4)` per YUY2 frame) and :355-357 (one `glTexSubImage2D` per row, about 1080 GL calls per frame)
- Evidence: these run every frame per enabled output/input on Thread 0.
- Impact: steady garbage on Windows/macOS outputs and on webcam input; GC pauses under ZGC are short, so low impact. The per-row upload is a CPU cost (could use `GL_UNPACK_ROW_LENGTH` and flip in the YUY2/blit pass).
- Fix: cache the `Function` objects per slot in `SpoutApi`, reuse argument arrays, keep an `IntArray(4)` field for the viewport, return size via fields instead of a `Pair`.
- Verified:

### L1a-9 [polish] AtomicFile creates an empty target before the swap, so a crash can leave a 0-byte file
- Where: src/main/kotlin/llm/slop/liquidlsd/utils/AtomicFile.kt:68 (`if (!target.exists()) Files.createFile(target.toPath())`), doc at :12-16 ("a crash ... never leaves a truncated file")
- Evidence: for a new file the target is created empty (to read the umask) before `moveIntoPlace`. A crash or exception in between leaves an empty file; a library scan on another thread can also see the empty file during the window. Also, the KDoc for `write` (:12-16) sits above `keepUnreadable`, which has its own KDoc right after it, so `write` is undocumented and `keepUnreadable` shows two comments.
- Impact: rare: an empty preset/profile after a crash during first save; `keepUnreadable` skips empty files so the loader just falls back to defaults.
- Fix: create a probe file with another temp name to read the umask permissions (or apply `PosixFilePermissions` from the directory's default ACL), delete the target in `finally` if the move failed and it was created here; move the KDoc onto `write`.
- Verified:

### L1a-10 [polish] Session restore walks the whole presets tree on Thread 0
- Where: src/main/kotlin/llm/slop/liquidlsd/presets/SessionSerializer.kt:274 and :428-432 (`presetFileNamed` → `root.walkTopDown()` per deck)
- Evidence: called four times from `loadSession` (Main.kt:379, Thread 0 at startup). Each call walks every file under `library/presets`. It also only matches `<name>.lsd`, so a deck loaded from a legacy `.json` preset is never re-linked to its file.
- Impact: slower start with a large library (thousands of presets); legacy `.json` presets still ask for a name on Save after a restart.
- Fix: walk once and index by name for all four decks; accept the same extensions as `PlaylistParser.presetExtensions`. Better still, save the file path in the session.
- Verified:

### L1a-11 [polish] Offline export: ffmpeg decode failure is silent, and the decode runs on Thread 0
- Where: src/main/kotlin/llm/slop/liquidlsd/export/AudioDecoder.kt:81 (`pb.start().waitFor()`, exit code ignored); export/OfflineRenderStudio.kt:80-81 (`AudioDecoder.decodeAudioFile` inside `startExport`, called from ui/VideoExportModal.kt:282/291)
- Evidence: if ffmpeg fails (unsupported file, disk full in temp), the temp file is short or empty and a 0-sample (or truncated) `DecodedAudio` is returned; the render proceeds with silence or stops early, with no message. The decode of a two-hour mix (the case ce148e1 targets) runs synchronously on Thread 0, freezing the UI for its duration (pre-existing, but the change was made for exactly these long files).
- Impact: users rendering long or unusual audio: a frozen window for a while, and a wrong-length or silent-analysis render without explanation.
- Fix: check the exit code and throw with the last stderr lines (redirect stderr to a small buffer instead of DISCARD); move the decode to a worker and start the render when it lands.
- Verified:

### L1a-12 [polish] Thread.sleep and JACK/Java Sound open on Thread 0 when changing audio device (pre-existing in a changed file)
- Where: src/main/kotlin/llm/slop/liquidlsd/audio/AudioEngine.kt:489-500 (`selectDevice`: `stop()`, `Thread.sleep(150)`, `startClient()`), :556-559 (another 150 ms sleep in `startClient`); callers ui/AudioEnginePanel.kt:184, :209, :138
- Evidence: Preferences calls `selectDevice`/`start` on Thread 0; that path sleeps up to 300 ms and opens a JACK client or Java Sound line (native, can stall). The watchdog thread calls the same `stop()`/`startClient()` via `tryReconnect` (:577-586) with no lock, so a device change during a reconnect can run two `startClient`s at once. The new `phaseLock.stopWorker/startWorker` (:522-524) and JackClient `serverGone` reconnect make this path run more often.
- Impact: a visible output freeze on each device or backend change; a rare double client after a JACK restart coinciding with a device change.
- Fix: run device changes on a single audio-control executor shared with the watchdog.
- Verified:

### L1a-13 [polish] Spout sender-list object is never released
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/ExternalVideoDiscovery.kt:98-102, :43-47
- Evidence: `spoutLister` is created once by the polling coroutine and `stopPolling()` neither releases nor clears it. A SpoutLibrary object holds shared-memory handles.
- Impact: none in practice (process lifetime); listed for the lifecycle checklist.
- Fix: `spoutLister?.release(); spoutLister = null` in `stopPolling`, called at shutdown.
- Verified:

### L1a-14 [polish] Swallowed exceptions in new code
- Where: src/main/kotlin/llm/slop/liquidlsd/utils/AtomicFile.kt:33-35 (`keepUnreadable`: `catch (e: Exception) { null }`); rendering/ExternalVideoDiscovery.kt:120-126 and SyphonBridge.kt:163 (`catch (e: Throwable) { emptyList() }`, no log); rendering/SyphonBridge.kt:114 (`key()` returns null silently)
- Evidence: failures vanish without a log line. In `keepUnreadable` this means the user is not told the unreadable file was not kept, and the original is then overwritten by defaults on the next save.
- Impact: harder diagnosis; for `keepUnreadable`, possible silent loss of the very file it exists to protect (e.g. a read-only folder).
- Fix: `logger.warn(e) { ... }` in each; in `keepUnreadable` post a `UserNotice` that the copy failed.
- Verified:

### L1a-15 [polish] PipeWire source labels are never forgotten, and numbered labels can point at a different node later
- Where: src/main/kotlin/llm/slop/liquidlsd/rendering/ExternalVideoDiscovery.kt:60-63, :85-89
- Evidence: `pipeWireTargets` only grows; duplicate descriptions are numbered in `pw-dump` order (`"HP HD Camera (2)"`), which can change between scans or reboots, so a saved label may resolve to the other node. `pw-dump` also has no timeout (:72-74), so a hung `pw-dump` stops discovery for good.
- Impact: a deck saved with the second of two same-named cameras may open the first one after a replug; low.
- Fix: rebuild the map each scan; number duplicates by a stable key (sorted `node.name` or `object.serial`); `process.waitFor(5, SECONDS)` and destroy on timeout.
- Verified:
