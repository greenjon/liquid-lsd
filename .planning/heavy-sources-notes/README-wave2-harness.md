# Wave 2 scratch harness (not in the repo)

`render-harness.kt.txt` renders any `library/sources/<id>` ISF (single or multipass/persistent, emulating
`ISFVisualSource` ping-pong semantics) for N frames and writes PNGs to /tmp/heavy. Copy it to
`src/test/kotlin/llm/slop/liquidlsd/rendering/isf/HeavySourcesScratchRenderTest.kt` to use it.
Env: HEAVY_ID, HEAVY_W/H, HEAVY_DUMP=frame,frame, HEAVY_OVER="Input=val,...", HEAVY_TAG.
Run on the NVIDIA GPU (this laptop defaults to the Intel iGPU):
`__NV_PRIME_RENDER_OFFLOAD=1 __GLX_VENDOR_LIBRARY_NAME=nvidia HEAVY_ID=x ./gradlew cleanTest test --no-build-cache --tests '*HeavySourcesScratchRenderTest' -i`
(`--no-build-cache` + `cleanTest` are needed: env vars are not Gradle task inputs, so a cached result is replayed.)
`compile-harness.kt.txt` compiles the ids in HEAVY_IDS only; agent H's `StockIsfCompileTest` supersedes it.
Timings are the CPU-side `glFinish` span of one frame (all passes).

## Engine facts found while building the sims
- A persistent TARGET binds the most recently written buffer (after the swap), so a pass that reads another pass's
  persistent target sees this frame's output.
- Two passes sharing one persistent TARGET share one slot, and `resizePassFBOs` would allocate that slot twice
  (leaks the first pair). Sub-stepping therefore uses distinct targets chained A<-D, B<-A, C<-B, D<-C.
- `patch` is a reserved word in GLSL; do not use it as an identifier.
