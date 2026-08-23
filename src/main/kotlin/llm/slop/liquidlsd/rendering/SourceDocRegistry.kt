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
                " are set by the recipe; hue, depth, and 3D mode shape the final image.",
        "dynamic_spiral" to "Particle trail system where points spiral outward under wave," +
                " shear, and damping forces. High Trail Decay creates luminous streak patterns.",
        "gyroid" to "Triply-periodic minimal surface (Schoen gyroid) rendered by ray-marching." +
                " Thickness and Wall Width control the shell density.",
        "chladni" to "Chladni standing-wave nodal patterns. Frequency N/M/L select harmonic" +
                " modes; Thickness and Wall Width control line weight.",
        "attractor_feedback" to "Two-layer strange-attractor system with cross-modulated" +
                " Jacobian and variance coefficients. High Persistence burns in trails.",
        "icosa_dodeca" to "Continuous H3 Coxeter symmetry morph between Icosahedron and Dodecahedron, with" +
                " Great Kepler-Poinsot Stellations, kaleidoscopic chamber coloring, and crystal reveal.",
        "icosahedron" to "Continuous 2D Du Val manifold covering all 32 achiral stellations of the icosahedron," +
                " driven by real-time H3 normal vector generation and 60-element icosahedral orbit evaluation.",
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
        "mandala/Zoom" to "Camera zoom. Lower values zoom in; higher values pull back.",
        "mandala/Rotate Z" to "Rotates the mandala around its central axis. Drive with a slow LFO for a spin effect.",
        "mandala/Thickness" to "Stroke thickness of the curve. Higher = bolder, more filled shapes.",
        "mandala/Hue Offset" to "Shifts the overall hue of the colour palette.",
        "mandala/Hue Sweep" to "Width of the hue gradient swept across the figure. 0 = solid colour; 1 = full rainbow.",
        "mandala/Depth" to "Controls z-depth layering for 3D mode extrusion.",
        "mandala/Lobes" to "Selects the petal-count group. Each value corresponds to a curated set of recipes.",
        "mandala/Recipe Select" to "Picks a specific recipe within the current Lobes group. Scrub to browse shapes.",
        "mandala/Bg Style" to "Background style: 0 = black, 1 = solid colour, 2 = animated gradient.",
        "mandala/Bg Feedback" to "Blends the previous frame into the background — creates motion trails.",
        "mandala/Bg Hue" to "Hue of the background colour (Bg Style 1 or 2).",
        "mandala/Bg Sat" to "Saturation of the background colour.",
        "mandala/Bg Val" to "Brightness of the background colour.",
        "mandala/Bg Sweep" to "Hue sweep width of the background gradient.",
        "mandala/Bg Speed" to "Animation speed of the background gradient cycle.",
        "mandala/Bg Zoom" to "Scale of the background pattern.",
        "mandala/3D Mode" to "3D projection mode: 0 = flat, 1–4 = various sphere/tube wrap modes.",
        "mandala/Sphere Wrap X" to "Horizontal stretch of the sphere-wrap UV mapping.",
        "mandala/Sphere Wrap Y" to "Vertical stretch of the sphere-wrap UV mapping.",
        "mandala/Mirror Group" to "Symmetry group: 0 = none, 1 = 2-fold, 2 = 4-fold.",
        "mandala/Permute XY" to "Enables XY axis permutation in 3D mode.",
        "mandala/Permute YZ" to "Enables YZ axis permutation in 3D mode.",
        "mandala/Permute ZX" to "Enables ZX axis permutation in 3D mode.",
        "mandala/Rotate Y" to "3D Y-axis rotation (yaw). Pivots the sphere-wrapped figure left/right.",
        "mandala/Rotate X" to "3D X-axis rotation (pitch). Pivots the figure up/down.",
        "mandala/3D Persp" to "Perspective strength for 3D mode. Higher = stronger depth distortion.",
        "mandala/Harmonic Lock" to "When 1.0, locks Hue Sweep to the recipe's petal count for perceptually correct colours.",
        "mandala/Freq Offset" to "Offsets all four arm frequencies by a fractional amount, morphing the figure.",

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

        // Gyroid
        "gyroid/Scale X" to "Frequency of the gyroid surface in the X direction.",
        "gyroid/Scale Y" to "Frequency in the Y direction.",
        "gyroid/Scale Z" to "Frequency in the Z direction. Mismatching X/Y/Z stretches the surface.",
        "gyroid/Thickness" to "Signed thickness of the shell: positive = outer wall, negative = inner wall.",
        "gyroid/Wall Width" to "Width of the rendered isosurface band. Thinner = finer mesh lines.",
        "gyroid/Speed" to "Auto-animation speed (gyroid surface phase shift over time).",
        "gyroid/Zoom" to "Camera zoom distance.",
        "gyroid/Color Shift" to "Hue shift applied to the surface colouring.",
        "gyroid/Rotate Y" to "Camera yaw.",
        "gyroid/Rotate X" to "Camera pitch.",
        "gyroid/Rotate Z" to "Camera roll.",
        "gyroid/Glow" to "Edge glow emission intensity.",

        // Chladni
        "chladni/Mode" to "Standing-wave mode selector: blends between different harmonic pattern families.",
        "chladni/Frequency N" to "First frequency component. Controls primary nodal line spacing.",
        "chladni/Frequency M" to "Second frequency component. Interacts with N to create the nodal pattern.",
        "chladni/Frequency L" to "Third frequency component for tertiary mode mixing.",
        "chladni/Thickness" to "Line thickness of the nodal curves.",
        "chladni/Wall Width" to "Width of the rendered nodal wall band.",
        "chladni/Scale" to "Spatial scale of the pattern on screen.",
        "chladni/Speed" to "Animation speed of the pattern evolution.",
        "chladni/Zoom" to "Camera zoom.",
        "chladni/Color Shift" to "Hue shift.",
        "chladni/Rotate Y" to "Pattern rotation Y.",
        "chladni/Rotate X" to "Pattern rotation X.",
        "chladni/Rotate Z" to "Pattern rotation Z.",
        "chladni/Glow" to "Edge glow.",

        // Attractor Feedback
        "attractor_feedback/Plane Scale" to "Global spatial scale of the attractor plane projection.",
        "attractor_feedback/Color Shift" to "Hue rotation of the colour palette.",
        "attractor_feedback/Persistence" to "How strongly previous frames are retained. High values burn in trails.",
        "attractor_feedback/Scale 0" to "Scale factor for attractor layer 0.",
        "attractor_feedback/Rotate 0" to "Rotation angle for attractor layer 0.",
        "attractor_feedback/Offset X 0" to "X translation for attractor layer 0.",
        "attractor_feedback/Offset Y 0" to "Y translation for attractor layer 0.",
        "attractor_feedback/Var Coef 0" to "Variance coefficient for attractor layer 0. Controls chaotic spread.",
        "attractor_feedback/Jacobian 0" to "Jacobian strength for layer 0. Higher = stronger contraction toward the attractor.",
        "attractor_feedback/Scale 1" to "Scale factor for attractor layer 1.",
        "attractor_feedback/Rotate 1" to "Rotation angle for attractor layer 1.",
        "attractor_feedback/Offset X 1" to "X translation for attractor layer 1.",
        "attractor_feedback/Offset Y 1" to "Y translation for attractor layer 1.",
        "attractor_feedback/Var Coef 1" to "Variance coefficient for attractor layer 1.",
        "attractor_feedback/Jacobian 1" to "Jacobian strength for layer 1.",

        // Icosa-Dodeca
        "icosa_dodeca/Morph" to "Continuous 4-stage cyclic H3 Coxeter morph: 0.0–0.25 (Icosahedron → Dodecahedron), 0.25–0.50 (Dodecahedron → Great Stellated Dodecahedron), 0.50–0.75 (Great Stellated Dodecahedron → Great Icosahedron), 0.75–1.00 (Great Icosahedron → Icosahedron).",
        "icosa_dodeca/Stellation" to "Manual boost/override for CSG stellation star spike depth.",
        "icosa_dodeca/Support H" to "Wythoff facet cutting: negative values truncate vertices (e.g. -0.15 for Buckyball), positive values bevel/cantellate edges.",
        "icosa_dodeca/Color Method" to "Coloring algorithm: 0 = H3 Chamber & Angular Sectors, 1 = Radial Depth Gradient, 2 = Facet Normal Spectrum.",
        "icosa_dodeca/Hue Offset" to "Rotates the base hue of the color palette.",
        "icosa_dodeca/Saturation" to "Color saturation of faces and edges.",
        "icosa_dodeca/Brightness" to "Overall luminance multiplier for surface coloring.",
        "icosa_dodeca/Opacity" to "Face opacity. Sweet spot is 0.6–0.8 for crystal reveal: semi-transparent faces illuminate inner self-intersecting facets without clutter.",
        "icosa_dodeca/Edge Thickness" to "Line thickness of wireframe edges.",
        "icosa_dodeca/Edge Brightness" to "Brightness and contrast of the complementary wireframe edge lines.",
        "icosa_dodeca/Zoom" to "Camera zoom distance.",
        "icosa_dodeca/Rotate X" to "Pitch rotation angle around the X axis.",
        "icosa_dodeca/Rotate Y" to "Yaw rotation angle around the Y axis.",
        "icosa_dodeca/Rotate Z" to "Roll rotation angle around the Z axis.",

        // Icosahedron (32-Stellation Du Val Manifold)
        "icosahedron/Control X" to "Continuous density layer selector (X axis): sweeps through the 32 achiral stellations along the Du Val poset branch.",
        "icosahedron/Control Y" to "Interpolation factor (Y axis) between 3-fold face pole and 5-fold face pole for real-time H3 normal generator vector.",
        "icosahedron/Color Method" to "Coloring algorithm: 0 = Chamber Sectors, 1 = Depth Gradient, 2 = Normal Spectrum.",
        "icosahedron/Hue Offset" to "Rotates the base hue of the color palette.",
        "icosahedron/Saturation" to "Color saturation of faces and edges.",
        "icosahedron/Brightness" to "Overall luminance multiplier for surface coloring.",
        "icosahedron/Opacity" to "Face opacity for semi-transparent multi-layer crystal raymarching.",
        "icosahedron/Edge Thickness" to "Line thickness of wireframe edges.",
        "icosahedron/Edge Brightness" to "Brightness and contrast of the wireframe edges.",
        "icosahedron/Zoom" to "Camera zoom distance.",
        "icosahedron/Rotate X" to "Pitch rotation angle around the X axis.",
        "icosahedron/Rotate Y" to "Yaw rotation angle around the Y axis.",
        "icosahedron/Rotate Z" to "Roll rotation angle around the Z axis.",

        // Feedback chain (shared across all decks)
        "feedback/fbDecay" to "How much of the previous frame persists each step. 0 = no trail; 1 = infinite persistence.",
        "feedback/fbGain" to "Brightness gain applied to the feedback signal before blending.",
        "feedback/fbZoom" to "Zoom applied to the feedback buffer each frame — creates an infinite-zoom tunnel effect.",
        "feedback/fbRotate" to "Rotation applied to the feedback buffer each frame. Drive with a slow LFO to spiral.",
        "feedback/fbHueShift" to "Hue rotation applied to the feedback signal each frame. Accumulates over time.",
        "feedback/fbBlur" to "Gaussian blur radius applied to the feedback signal — softens the trail.",
        "feedback/fbChroma" to "Chromatic aberration applied to the feedback signal — RGB channel offset amount.",
        "feedback/fbMode" to "Feedback blend mode: 0 = additive, 1 = screen, 2 = multiply.",

        // Mixer
        "mixer/crossfade" to "Crossfade position between Deck A and Deck B.",
        "mixer/masterAlpha" to "Master output opacity. 0 = black; 1 = full output.",
        "mixer/bloom" to "Bloom post-effect intensity. Adds a soft glow to bright regions.",
        "mixer/xfadeSpeed" to "Auto-crossfade transition speed when queue playback is active.",
        "mixer/queuePrev" to "Trigger: steps the play queue backward by one preset.",
        "mixer/queueNext" to "Trigger: steps the play queue forward by one preset.",
        "mixer/randDeckA" to "Trigger: randomises all modulation values on Deck A.",
        "mixer/randDeckB" to "Trigger: randomises all modulation values on Deck B.",
        "mixer/randDeckC" to "Trigger: randomises all modulation values on Deck C.",
        "mixer/randAll" to "Trigger: randomises all modulation values on all three decks simultaneously.",
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
            ?: ""

    /** Returns a mixer-level parameter description. */
    fun getMixerParamDescription(paramKey: String): String =
        paramDescriptions["mixer/$paramKey"] ?: ""
}
