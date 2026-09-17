package llm.slop.liquidlsd.rendering

/**
 * Hardcoded documentation registry for all built-in visual sources and their parameters.
 *
 * Keys for [paramDescriptions] follow the pattern:
 *   "<sourceId>/<paramName>"   — for source-specific parameters
 *   "feedback/<paramName>"     — for feedback chain parameters shared across all sources
 *   "mixer/<paramName>"        — for mixer-level parameters
 */
object SourceDocRegistry {

    /** One-line description of each visual source engine, keyed by source ID. */
    val sourceDescriptions: Map<String, String> = mapOf(
        "mandala" to "Parametric 4-arm Lissajous mandala. Arm lengths and frequency ratios" +
                " are set by the recipe; hue, depth, and zoom shape the final image.",
        "dynamic_spiral" to "Particle trail system where points spiral outward under wave," +
                " shear, and damping forces. High Trail Decay creates luminous streak patterns.",
        "icosa_h3" to "Icosahedral H3 Coxeter raymarcher combining a continuous Icosahedron/Dodecahedron" +
                " duality morph (with Kepler-Poinsot stellations and truncation/cantellation duals) crossfaded" +
                " against an independent spike-and-blocker stellation CSG, with facet-family aware coloring.",
    )

    /**
     * Per-parameter descriptions keyed by "<sourceId>/<paramName>" or "feedback/<paramName>"
     * or "mixer/<paramName>".
     */
    val paramDescriptions: Map<String, String> = mapOf(

        // Mandala
        "mandala/L1" to "Length of arm 1 (primary arm). Larger values extend the outermost petal tips.",
        "mandala/L2" to "Length of arm 2 (second harmonic). Interacts with L1 to shape petal edges.",
        "mandala/L3" to "Length of arm 3 (third harmonic). Adds finer curvature to the figure.",
        "mandala/L4" to "Length of arm 4 (fourth harmonic). Controls innermost detail loops.",
        "mandala/Thickness" to "Stroke thickness of the curve. Higher = bolder, more filled shapes.",
        "mandala/Hue Offset" to "Shifts the overall hue of the colour palette.",
        "mandala/Hue Sweep" to "Width of the hue gradient swept across the figure. 0 = solid colour; 1 = full rainbow.",
        "mandala/Depth" to "Controls radial depth shading (outer reach vs center).",
        "mandala/Lobes" to "Selects the petal-count group. Each value corresponds to a curated set of recipes.",
        "mandala/Recipe Select" to "Picks a specific recipe within the current Lobes group. Scrub to browse shapes.",

        // Dynamic Spiral
        "dynamic_spiral/Max Points" to "Number of particles in the trail system. Higher = denser but costs more GPU.",
        "dynamic_spiral/Scale" to "Overall spatial scale of the spiral.",
        "dynamic_spiral/Damping" to "Velocity damping per step. Lower values let particles fly outward further.",
        "dynamic_spiral/Wave Freq" to "Frequency of the radial wave modulating particle paths.",
        "dynamic_spiral/Wave Amp" to "Amplitude of the wave modulation.",
        "dynamic_spiral/Shear" to "Tangential shear applied per step. Creates twisted streak patterns.",
        "dynamic_spiral/Speed" to "Global update speed multiplier.",
        "dynamic_spiral/Dot Size" to "Rendered size of each particle dot.",
        "dynamic_spiral/Glow" to "Additive glow halo around each particle.",
        "dynamic_spiral/Hue Offset" to "Base hue of the particle colour palette.",
        "dynamic_spiral/Hue Sweep" to "Range of hues swept across the particle age gradient.",
        "dynamic_spiral/Trail Decay" to "How quickly old trail positions fade. Low = long persistent trails.",

        // Icosa-H3 (combined Icosahedron/Dodecahedron duality morph + spike/blocker stellation CSG)
        "icosa_h3/Morph" to "Continuous 4-stage cyclic H3 Coxeter morph: 0.0–0.25 (Icosahedron → Dodecahedron), 0.25–0.50 (Dodecahedron → Great Stellated Dodecahedron), 0.50–0.75 (Great Stellated Dodecahedron → Great Icosahedron), 0.75–1.00 (Great Icosahedron → Icosahedron).",
        "icosa_h3/StellationBoost" to "Manual boost/override for the duality-morph stellation spike depth.",
        "icosa_h3/SpikeMode" to "Crossfades the geometry from the smooth duality-morph facets (0.0) to an independent spike-and-blocker stellation CSG (1.0).",
        "icosa_h3/SpikePhase" to "Sweeps the spike family's pole around the 3-fold/5-fold arc, independent of Morph.",
        "icosa_h3/SpikeSharpness" to "Tilts the spike facets from flat (0.0) to fully extruded pyramid spikes (1.0). Only affects the spike family (see Spike Mode).",
        "icosa_h3/BlockerSize" to "Size of the plane that chops the spike tips. Larger values leave more of the spike intact.",
        "icosa_h3/SupportH" to "Wythoff facet cutting on the duality-morph family: negative values truncate vertices (e.g. -0.15 for Buckyball), positive values bevel/cantellate edges.",
        "icosa_h3/ColorMode" to "Coloring algorithm: 0 = Facet Family (distinct hue per active half-space), 1 = H3 Chamber & Angular Sectors, 2 = Radial Depth Gradient, 3 = Facet Normal Spectrum, 4 = Iridescent Fresnel (animated view-angle shimmer).",
        "icosa_h3/HueOffset" to "Rotates the base hue of the color palette.",
        "icosa_h3/HueAnimSpeed" to "Slowly drifts the hue offset over time. 0 = static.",
        "icosa_h3/Saturation" to "Color saturation of faces and edges.",
        "icosa_h3/Brightness" to "Overall luminance multiplier for surface coloring.",
        "icosa_h3/Opacity" to "Face opacity. Sweet spot is 0.6–0.8 for crystal reveal: semi-transparent faces illuminate inner self-intersecting facets without clutter.",
        "icosa_h3/EdgeThickness" to "Line thickness of wireframe edges.",
        "icosa_h3/EdgeBrightness" to "Brightness and contrast of the complementary wireframe edge lines.",
        "icosa_h3/RimGlow" to "Fresnel rim-light intensity added around silhouette edges for extra depth.",
        "icosa_h3/Zoom" to "Camera zoom distance.",
        "icosa_h3/RotateX" to "Pitch rotation angle around the X axis.",
        "icosa_h3/RotateY" to "Yaw rotation angle around the Y axis.",
        "icosa_h3/RotateZ" to "Roll rotation angle around the Z axis.",

        // Feedback chain (shared across all decks)
        // View (3D display and transform)
        "view/3D Mode" to "3D display mode: 0 = 2D Flat, 1 = Tri-Axial (3P @ 90°), 2 = Cube Cage (6P), 3 = Hex-Planar (6P @ 60°), 4 = Tetrahedral Kaleidoscope (24-Chamber).",
        "view/3DMode" to "3D display mode: 0 = 2D Flat, 1 = Tri-Axial (3P @ 90°), 2 = Cube Cage (6P), 3 = Hex-Planar (6P @ 60°), 4 = Tetrahedral Kaleidoscope (24-Chamber).",
        "view/Rotate X" to "3D Pitch rotation (elevation angle).",
        "view/RotateX" to "3D Pitch rotation (elevation angle).",
        "view/Rotate Y" to "3D Yaw rotation (azimuth angle).",
        "view/RotateY" to "3D Yaw rotation (azimuth angle).",
        "view/Rotate Z" to "2D/3D Roll rotation.",
        "view/RotateZ" to "2D/3D Roll rotation.",
        "view/Zoom" to "Camera zoom / field of view scaling.",
        "view/3D Persp" to "Perspective strength: 0 = orthographic, 1 = deep perspective camera.",
        "view/Persp" to "Perspective strength: 0 = orthographic, 1 = deep perspective camera.",
        "view/Depth Dim" to "Headlight proximity falloff: dims receding geometric elements into atmospheric depth.",
        "view/DepthDim" to "Headlight proximity falloff: dims receding geometric elements into atmospheric depth.",
        "view/Separation" to "Axial separation offset: pushes intersecting planes outward along their normal axes.",
        "view/Blend Mode" to "3D intersection blending: 0 = alpha transparency, 1 = additive luminous glow.",
        "view/BlendMode" to "3D intersection blending: 0 = alpha transparency, 1 = additive luminous glow.",
        "view/Roundness" to "Boundary shape of intersecting planes: 0 = square quad, 1 = circular disc (smooth celestial gyroscope).",

        // Feedback
        "feedback/Decay" to "Feedback trail persistence: 0 = no feedback, 1 = maximum trail retention.",
        "feedback/fbDecay" to "Feedback trail persistence: 0 = no feedback, 1 = maximum trail retention.",
        "feedback/fbZoom" to "Zoom applied to the feedback buffer each frame — creates an infinite-zoom tunnel effect.",
        "feedback/fbRotate" to "Rotation applied to the feedback buffer each frame. Drive with a slow LFO to spiral.",
        "feedback/fbHueShift" to "Hue rotation applied to the feedback signal each frame. Accumulates over time.",
        "feedback/fbBlur" to "Gaussian blur radius applied to the feedback signal — softens the trail.",
        "feedback/fbChroma" to "Chromatic aberration applied to the feedback signal — RGB channel offset amount.",
        "feedback/fbMode" to "Feedback blend mode: 0 = additive, 1 = screen, 2 = multiply.",

        // Mixer
        "mixer/crossfade" to "Crossfade position between Deck A and Deck B.",
        "mixer/mode" to "Deck A/B blend mode: 0 = Add, 1 = Screen, 2 = Multiply, 3 = Max, 4 = Crossfade.",
        "mixer/masterAlpha" to "Master output opacity. 0 = black; 1 = full output.",
        "mixer/bloom" to "Bloom post-effect intensity. Adds a soft glow to bright regions.",
        "mixer/xfadeSpeed" to "Auto-crossfade transition speed when queue playback is active.",
        "mixer/queuePrev" to "Trigger: steps the play queue backward by one preset.",
        "mixer/queueNext" to "Trigger: steps the play queue forward by one preset.",
        "mixer/bgQueuePrev" to "Trigger: steps the background queue backward by one preset.",
        "mixer/bgQueueNext" to "Trigger: steps the background queue forward by one preset.",
        "mixer/tapTempo" to "Trigger: taps in BPM tempo and synchronizes beat phase.",
        "mixer/randDeckA" to "Trigger: randomises all modulation values on Deck A.",
        "mixer/randDeckB" to "Trigger: randomises all modulation values on Deck B.",
        "mixer/randDeckBG" to "Trigger: randomises all modulation values on Deck BG.",
        "mixer/randDeckPV" to "Trigger: randomises all modulation values on Deck PV.",
        "mixer/randAll" to "Trigger: randomises all modulation values on all decks simultaneously.",
    )

    /** Returns the source description, or an empty string if none is registered. */
    fun getSourceDescription(sourceId: String): String =
        sourceDescriptions[sourceId] ?: ""

    /**
     * Returns the parameter description for a given source and parameter name,
     * falling back to the feedback/ namespace, then empty string.
     */
    fun getParamDescription(sourceId: String, paramName: String): String =
        paramDescriptions["$sourceId/$paramName"]
            ?: paramDescriptions["feedback/$paramName"]
            ?: paramDescriptions["view/$paramName"]
            ?: ""

    /** Returns a mixer-level parameter description. */
    fun getMixerParamDescription(paramKey: String): String =
        paramDescriptions["mixer/$paramKey"]
            ?: paramDescriptions["mixer/${paramKey.lowercase()}"]
            ?: paramDescriptions["mixer/${paramKey.replace(" ", "")}"]
            ?: ""
}
