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
        "kifs" to "Kaleidoscopic Iterated Function System (KIFS) fractal. Fold angles and" +
                " scale drive the self-similar structure; Shape Morph blends between presets.",
        "dynamic_spiral" to "Particle trail system where points spiral outward under wave," +
                " shear, and damping forces. High Trail Decay creates luminous streak patterns.",
        "gyroid" to "Triply-periodic minimal surface (Schoen gyroid) rendered by ray-marching." +
                " Thickness and Wall Width control the shell density.",
        "mandelbulb" to "3D analogue of the Mandelbrot set rendered by ray-marching. Power" +
                " and Iterations control the fractal depth; Bailout sets the escape threshold.",
        "mandelbox" to "Box-fold IFS fractal rendered by ray-marching. Negative Scale" +
                " values invert the folding, producing very different morphologies.",
        "chladni" to "Chladni standing-wave nodal patterns. Frequency N/M/L select harmonic" +
                " modes; Thickness and Wall Width control line weight.",
        "clifford_torus" to "A 4-dimensional torus projected into 3D and rotated in" +
                " 4-space via six independent rotation planes (XY, XZ, YZ, XW, YW, ZW).",
        "pseudo_kleinian" to "Pseudo-Kleinian limit-set fractal. CX/CY/CZ and Scale set the" +
                " Julia parameter; a separate camera rotation tracks the viewer.",
        "attractor_feedback" to "Two-layer strange-attractor system with cross-modulated" +
                " Jacobian and variance coefficients. High Persistence burns in trails.",
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

        // KIFS
        "kifs/Iterations" to "Number of IFS fold iterations. Higher = more self-similar detail; also costs more GPU.",
        "kifs/Scale" to "Global scale of the fractal attractor. Values near 2.0 produce the most structured forms.",
        "kifs/Fold X" to "Enables folding along the X axis.",
        "kifs/Fold Y" to "Enables folding along the Y axis.",
        "kifs/Fold Z" to "Enables folding along the Z axis.",
        "kifs/Fold Angle X" to "Manual X fold angle (overridden by Shape Morph when it is non-zero).",
        "kifs/Fold Angle Y" to "Manual Y fold angle.",
        "kifs/Fold Angle Z" to "Manual Z fold angle.",
        "kifs/Shape Morph" to "Blends between four preset fold-angle configurations. Drive with a slow LFO for organic morphing.",
        "kifs/Zoom" to "Camera zoom into the fractal. Higher values drill deeper into the structure.",
        "kifs/Color Shift" to "Shifts the hue of the distance-based colouring.",
        "kifs/Rotate Y" to "Camera yaw rotation.",
        "kifs/Rotate X" to "Camera pitch rotation.",
        "kifs/Rotate Z" to "Camera roll rotation.",
        "kifs/Glow" to "Emission glow intensity around the fractal edges.",
        "kifs/Repeat Spacing" to "Spatial tiling period. Lower values create a tighter repeating lattice.",
        "kifs/Repeat 3D" to "Enables 3D space tiling.",
        "kifs/Fly Speed" to "Auto-fly speed through the fractal along the camera axis.",
        "kifs/Trap Mode" to "Orbit trap colouring mode.",
        "kifs/Trap Glow" to "Glow intensity of the orbit-trap highlight region.",
        "kifs/Smoothness" to "Smoothing applied to the distance estimator — higher softens hard edges.",
        "kifs/Normal Coloring" to "Blends surface-normal-based shading into the colour. 1.0 = fully normal-shaded.",
        "kifs/Normal Frequency" to "Frequency of the normal-based colour banding.",
        "kifs/Rot Mode" to "Automatic rotation mode: 0 = manual, 1 = slow auto-tumble, 2 = beat-locked.",

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

        // Mandelbulb
        "mandelbulb/Power" to "Exponent of the Mandelbulb formula. 8 = classic bulb; lower = smoother, higher = spikier.",
        "mandelbulb/Iterations" to "Ray-march iteration count. Higher = more detail and GPU cost.",
        "mandelbulb/Glow" to "Surface glow emission.",
        "mandelbulb/Zoom" to "Camera zoom.",
        "mandelbulb/Color Shift" to "Hue of the iteration-count colouring.",
        "mandelbulb/Bailout" to "Escape radius. Higher values let rays travel further before escaping.",
        "mandelbulb/Rotate Y" to "Camera yaw.",
        "mandelbulb/Rotate X" to "Camera pitch.",
        "mandelbulb/Rotate Z" to "Camera roll.",

        // Mandelbox
        "mandelbox/Scale" to "Box-fold scale. Negative values invert the fold, producing a distinct morphology.",
        "mandelbox/Min Radius" to "Minimum sphere-fold radius. Smaller values increase interior complexity.",
        "mandelbox/Fixed Radius" to "Fixed sphere-fold radius reference.",
        "mandelbox/Iterations" to "Number of fold iterations.",
        "mandelbox/Fold Limit" to "Clamp value for the box fold step.",
        "mandelbox/Zoom" to "Camera zoom.",
        "mandelbox/Color Shift" to "Hue shift for distance-based colouring.",
        "mandelbox/Rotate Y" to "Camera yaw.",
        "mandelbox/Rotate X" to "Camera pitch.",
        "mandelbox/Rotate Z" to "Camera roll.",
        "mandelbox/Glow" to "Surface glow emission.",

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

        // Clifford Torus 4D
        "clifford_torus/Zoom" to "Camera zoom / projection scale.",
        "clifford_torus/Rotate X" to "3D X-axis rotation.",
        "clifford_torus/Rotate Y" to "3D Y-axis rotation.",
        "clifford_torus/Rotate Z" to "3D Z-axis rotation.",
        "clifford_torus/Rotate XW" to "4D XW plane rotation — creates a characteristic turning-inside-out motion.",
        "clifford_torus/Rotate YW" to "4D YW plane rotation.",
        "clifford_torus/Rotate ZW" to "4D ZW plane rotation.",
        "clifford_torus/Mesh Density Theta" to "Number of segments along the first toroidal direction.",
        "clifford_torus/Mesh Density Phi" to "Number of segments along the second toroidal direction.",
        "clifford_torus/Thickness" to "Tube radius of the rendered torus wire.",
        "clifford_torus/Wireframe Mode" to "0 = solid surface, 1 = wireframe grid.",
        "clifford_torus/Color Shift" to "Hue of the surface colouring.",
        "clifford_torus/Glow" to "Surface emission glow.",

        // Pseudo-Kleinian
        "pseudo_kleinian/Scale" to "IFS scale factor. Values close to 2 produce the densest limit sets.",
        "pseudo_kleinian/Radius" to "Sphere inversion radius in the Kleinian group iteration.",
        "pseudo_kleinian/CX" to "Julia parameter X component — shifts the attractor geometry.",
        "pseudo_kleinian/CY" to "Julia parameter Y component.",
        "pseudo_kleinian/CZ" to "Julia parameter Z component.",
        "pseudo_kleinian/Rotate X" to "Fold-space X rotation.",
        "pseudo_kleinian/Rotate Y" to "Fold-space Y rotation.",
        "pseudo_kleinian/Rotate Z" to "Fold-space Z rotation.",
        "pseudo_kleinian/Iterations" to "Iteration depth.",
        "pseudo_kleinian/Zoom" to "Camera zoom.",
        "pseudo_kleinian/Color Shift" to "Hue of orbit-trap colouring.",
        "pseudo_kleinian/Cam Rotate Y" to "Camera yaw — independent of the fold-space rotation.",
        "pseudo_kleinian/Cam Rotate X" to "Camera pitch.",
        "pseudo_kleinian/Cam Rotate Z" to "Camera roll.",
        "pseudo_kleinian/Glow" to "Surface emission glow.",

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
