package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.toDto
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.sin

/**
 * Imports a `.flame` XML genome (user-supplied files only) into the Fractal Flame source's parameters. The attribute
 * names come from sample files and from Draves & Reckase, "The Fractal Flame Algorithm"; no code of any flame
 * renderer was read. The mapping is lossy and says what it dropped: variations the source does not have, variations
 * beyond three per xform, xforms beyond six, 3D camera, depth of field, curves and tone settings (the source's tone
 * model differs, so the tone parameters keep their defaults).
 */
object FlameImporter {
    /**
     * Conventions that cannot be derived from the files alone (see the handoff). [transposed]: `coefs="a b c d e f"` is
     * read as x' = a x + b y + e, y' = c x + d y + f instead of x' = a x + c y + e, y' = b x + d y + f. [flipRotation]:
     * the camera's `rotate` turns the other way. The image's y axis is taken to point down (the source's `FlipY`
     * camera parameter is set), a reference render of rings2-1.flame having the ring centre where only that gives it.
     */
    class Options(val transposed: Boolean = false, val flipRotation: Boolean = false)

    const val IMPORT_DECAY = 0.85f
    /** Deck height the original's smoothing filter is scaled to when picking a sprite size. */
    const val IMPORT_DECK_HEIGHT = 1080f

    class Imported(
        val name: String,
        /** Values for the genome parameters (morph-enabled xform parameters, XF when a final xform exists) and the camera (Zoom, Rotation, CenterX, CenterY). */
        val values: Map<String, Float>,
        /** 256 RGB triples (768 bytes) or null when the file has no readable palette. Embedded in the preset by toPresetDto: the source's palette is procedural. */
        val palette: ByteArray?,
        /** Human-readable notes on what was dropped, clamped or assumed. */
        val notes: List<String>
    )

    /** File name -> our variation id, where the file name differs from [FlameVariations.NAMES] (compared lower-case). */
    private val ALIASES = mapOf(
        "julian" to "juliaN", "juliascope" to "juliaScope", "radial_blur" to "radialBlur", "gaussian_blur" to "gaussian",
        "oscilloscope" to "oscope"
    )
    private val ID_BY_FILE_NAME: Map<String, Int> = buildMap {
        FlameVariations.ALL.forEachIndexed { id, v -> if (v.supported) put(v.name.lowercase(), id) }
        for ((file, ours) in ALIASES) FlameVariations.NAMES.indexOf(ours).let { if (FlameVariations.ALL[it].supported) put(file, it) }
    }
    /** Reserved ids (skipped for v1 or not built yet): a file that uses one gets it listed as unsupported. */
    private val RESERVED: Set<String> = FlameVariations.ALL.filter { !it.supported }.map { it.name.lowercase() }.toSet()

    /** The attribute-name prefixes a variation's parameters are read under: the aliases first, then its own lower-cased name. */
    private val PREFIXES_BY_ID: Array<List<String>> = Array(FlameVariations.count()) { id ->
        ALIASES.filterValues { it == FlameVariations.NAMES[id] }.keys.toList() + FlameVariations.NAMES[id].lowercase()
    }

    /** Attributes of an xform that are not variations (and the editor-metadata suffixes of the files we saw). */
    private val META = setOf(
        "weight", "color", "symmetry", "coefs", "post", "opacity", "name", "chaos", "plotmode", "animate", "color_speed",
        "color_type", "material", "material_speed", "mirror_pre_post_translations", "yzcoefs", "zxcoefs", "ypostcoefs",
        "zxpostcoefs", "yzpostcoefs", "mod_gamma", "mod_gamma_speed", "mod_contrast", "mod_contrast_speed", "mod_saturation",
        "mod_saturation_speed", "mod_hue", "mod_hue_speed", "var_color", "var_color_speed", "var_opacity", "var_opacity_speed"
    )
    private val META_SUFFIX = Regex(
        "(_fx_priority|_enabled|_view_\\w+|_interpolation|_selected_idx|_locked|_point_count|_[xy]\\d+)$|curve_|^xy(post)?coeff|curve$",
        RegexOption.IGNORE_CASE
    )

    private fun xml(text: String): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)   // untrusted file: no entities, no DTD
        f.isXIncludeAware = false
        f.isExpandEntityReferences = false
        return f.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(text))).documentElement
    }

    private fun children(e: Element, tag: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = e.childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.takeIf { it.tagName == tag }?.let { out += it }
        return out
    }

    private fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null
    private fun Element.num(name: String, def: Float): Float = attr(name)?.trim()?.toFloatOrNull() ?: def
    private fun Element.nums(name: String, n: Int): FloatArray? =
        attr(name)?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size >= n }?.toFloatArray()

    /** All flames of a file: a single `<flame>` root or a `<flames>` wrapper around several. */
    fun parse(text: String, fallbackName: String = "flame", options: Options = Options()): List<Imported> {
        val root = xml(text.removePrefix("\uFEFF").trimStart())
        val flames = if (root.tagName == "flame") listOf(root) else children(root, "flame")
        return flames.mapIndexed { i, f -> convert(f, f.attr("name")?.takeIf { it.isNotBlank() } ?: if (flames.size > 1) "$fallbackName-${i + 1}" else fallbackName, options) }
    }

    private fun convert(flame: Element, name: String, opt: Options): Imported {
        val notes = ArrayList<String>()
        val params = FlameGenome.buildParameters()
        val out = HashMap<String, Float>()
        fun put(key: String, v: Float, what: String = key) {
            val p = params[key] ?: return
            val c = if (p.isAngle) wrap(v) else v.coerceIn(p.minClamp, p.maxClamp)
            if (!p.isAngle && abs(c - v) > 1e-4f) notes += "$what $v out of range, clamped to $c"
            out[key] = c
        }

        val xforms = children(flame, "xform")
        if (xforms.size > FlameGenome.MAX_XF) notes += "${xforms.size} xforms, only the first ${FlameGenome.MAX_XF} kept"
        val kept = xforms.take(FlameGenome.MAX_XF)
        val maxWeight = kept.maxOfOrNull { it.num("weight", 0f) }?.takeIf { it > 0f } ?: 1f
        val weightScale = if (maxWeight > 2f) 2f / maxWeight else 1f
        if (weightScale < 1f) notes += "weights scaled by %.3f to fit 0..2 (ratios kept)".format(weightScale)
        val unsupported = linkedSetOf<String>()

        for (x in 0 until FlameGenome.MAX_XF) {
            val k = FlameGenome.keysFor(x)
            val xf = kept.getOrNull(x)
            if (xf == null) { put(k.weight, 0f); continue }
            put(k.weight, xf.num("weight", 1f) * weightScale)
            put(k.color, xf.num("color", 0f))
            put(k.speed, (1f - xf.num("symmetry", 0f)) / 2f)
            if (xf.num("opacity", 1f) != 1f) notes += "X${x + 1} opacity ignored"
            affine(xf.nums("coefs", 6), opt)?.let { a -> setAffine(::put, a, k.preRot, k.preSx, k.preSy, k.preShear, k.preX, k.preY, "X${x + 1} pre-affine") }
            affine(xf.nums("post", 6), opt).let { a ->
                if (a != null) setAffine(::put, a, k.postRot, k.postSx, k.postSy, k.postShear, k.postX, k.postY, "X${x + 1} post-affine")
                else setAffine(::put, IDENTITY, k.postRot, k.postSx, k.postSy, k.postShear, k.postX, k.postY, "")
            }
            variations(xf, k, "X${x + 1}", ::put, notes, unsupported)
        }

        children(flame, "finalxform").firstOrNull()?.let { xf ->
            val k = FlameGenome.keysFor(FlameGenome.FINAL_INDEX)
            affine(xf.nums("coefs", 6), opt)?.let { a -> setAffine(::put, a, k.preRot, k.preSx, k.preSy, k.preShear, k.preX, k.preY, "XF pre-affine") }
            affine(xf.nums("post", 6), opt)?.let { a -> setAffine(::put, a, k.postRot, k.postSx, k.postSy, k.postShear, k.postX, k.postY, "XF post-affine") }
            variations(xf, k, "XF", ::put, notes, unsupported)
        }
        if (unsupported.isNotEmpty()) notes += "unsupported variations skipped: ${unsupported.joinToString()}"

        // camera: scale = pixels per flame unit at `size`, ours is visible height = 2 / Zoom
        val size = flame.nums("size", 2)
        val scale = flame.num("scale", 0f)
        if (size != null && scale > 0f) put("Zoom", 2f * scale / size[1], "camera zoom")
        val center = flame.nums("center", 2)
        if (center != null) { put("CenterX", center[0]); put("CenterY", center[1]) }
        put("FlipY", 1f)
        put("Rotation", Math.toRadians((flame.num("rotate", 0f) * (if (opt.flipRotation) -1 else 1)).toDouble()).toFloat())
        notes += "tone settings (brightness, gamma, vibrancy, filter, background) and the 3D camera are not imported"
        // Flames assume millions of samples; trails (decay) fill in the gaps from the far fewer points drawn here.
        put("Decay", IMPORT_DECAY)
        // the toe drops pixels below an absolute density; the originals have none, and sparse flames sit under it
        put("Toe", 0f)
        // the original smooths with a filter of `filter` pixels at its own size; at deck height that is a sprite about twice the radius wide
        val filterRadius = flame.num("filter", 0.75f)
        if (size != null) put("Softness", (2f * filterRadius * IMPORT_DECK_HEIGHT / size[1]).coerceIn(3f, 10f), "sprite softness")
        notes += "Decay set to $IMPORT_DECAY and Toe to 0 to fill in with fewer points; lower Decay for crisper, trail-free frames, raise Toe to drop speckle"

        return Imported(name, out, palette(children(flame, "palette").firstOrNull(), notes), notes)
    }

    private val IDENTITY = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)

    /** Reads `a b c d e f` into a row-major 2x2 plus translation: [m00, m01, m10, m11, tx, ty] with x' = m00 x + m01 y + tx. */
    private fun affine(c: FloatArray?, opt: Options): FloatArray? {
        c ?: return null
        val m = if (opt.transposed) floatArrayOf(c[0], c[1], c[2], c[3]) else floatArrayOf(c[0], c[2], c[1], c[3])
        return floatArrayOf(m[0], m[1], m[2], m[3], c[4], c[5])
    }

    /** QR-style split of M into R(rot) * [[sx, shear], [0, sy]] (the layout of [FlameGenome.pack]). */
    private fun setAffine(
        put: (String, Float, String) -> Unit, a: FloatArray,
        rot: String, sx: String, sy: String, shear: String, tx: String, ty: String, what: String
    ) {
        val angle = atan2(a[2], a[0])
        val c = cos(angle); val s = sin(angle)
        put(rot, angle, "$what rotation")
        put(sx, c * a[0] + s * a[2], "$what scale x")
        put(shear, c * a[1] + s * a[3], "$what shear")
        put(sy, -s * a[1] + c * a[3], "$what scale y")
        put(tx, a[4], "$what x"); put(ty, a[5], "$what y")
    }

    private fun variations(
        xf: Element, k: FlameGenome.XfKeys, who: String,
        put: (String, Float, String) -> Unit, notes: MutableList<String>, unsupported: MutableSet<String>
    ) {
        val found = ArrayList<Pair<Int, Float>>()
        val attrs = xf.attributes
        for (i in 0 until attrs.length) {
            val n = attrs.item(i).nodeName
            val id = ID_BY_FILE_NAME[n.lowercase()] ?: continue
            val w = xf.num(n, 0f)
            if (w != 0f) found += id to w
        }
        // anything numeric that is neither ours, a parameter of ours, nor editor metadata is a variation we do not have
        for (i in 0 until attrs.length) {
            val n = attrs.item(i).nodeName
            val lower = n.lowercase()
            val v = attrs.item(i).nodeValue.trim().toFloatOrNull() ?: continue
            if (lower in RESERVED) { if (v != 0f) unsupported += n; continue }
            if (lower in META || ID_BY_FILE_NAME.containsKey(lower) || META_SUFFIX.containsMatchIn(n)) continue
            if (ID_BY_FILE_NAME.keys.any { lower.startsWith("${it}_") } || RESERVED.any { lower.startsWith("${it}_") }) continue
            if (v != 0f && !n.contains('_')) unsupported += n
        }
        val top = found.sortedByDescending { abs(it.second) }.take(FlameGenome.SLOTS)
        if (found.size > top.size) notes += "$who: ${found.size} variations, kept the strongest ${top.size}"
        for (j in 0 until FlameGenome.SLOTS) {
            val v = top.getOrNull(j)
            put(k.slotId[j], (v?.first ?: 0).toFloat(), "$who variation ${j + 1}")
            put(k.slotWeight[j], v?.second ?: 0f, "$who variation ${j + 1} weight")
            for (q in 0 until FlameVariations.PARAMS) {
                var norm = 0.5f
                if (v != null) {
                    val prm = FlameVariations.ALL[v.first].params.getOrNull(q)
                    val raw = prm?.let { PREFIXES_BY_ID[v.first].firstNotNullOfOrNull { pre -> xf.attr("${pre}_${it.label.lowercase()}")?.trim()?.toFloatOrNull() } }
                    if (prm != null && raw != null) {
                        if (raw < prm.min || raw > prm.max) notes += "$who ${FlameVariations.NAMES[v.first]} ${prm.label} $raw outside ${prm.min}..${prm.max}, clamped"
                        norm = FlameVariations.normalised(v.first, q, raw)
                    }
                }
                put(k.slotParam[j][q], norm, "$who variation ${j + 1} parameter")
            }
        }
    }

    private fun palette(e: Element?, notes: MutableList<String>): ByteArray? {
        e ?: return null
        val hex = e.textContent.filterNot { it.isWhitespace() }
        if (hex.length < 768 * 2 || (e.attr("format")?.uppercase() ?: "RGB") != "RGB") { notes += "palette not readable (needs 256 RGB entries)"; return null }
        return ByteArray(768) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun wrap(a: Float): Float {
        val twoPi = (2 * PI).toFloat()
        var x = a
        while (x < -PI) x += twoPi
        while (x > PI) x -= twoPi
        return x
    }

    /** A saved-preset body for the imported flame (the palette is embedded as base64). Missing deck sections load as defaults. */
    fun toPresetDto(imported: Imported, presetName: String = imported.name): DeckPresetDto {
        val params = FlameGenome.buildParameters()
        for ((k, v) in imported.values) params[k]?.set(v)
        return DeckPresetDto(
            name = presetName,
            tags = listOf("imported", "flame"),
            visualSourceType = FlameSource.SOURCE_ID,
            parameters = params.mapValues { it.value.toDto() },
            feedbackParameters = emptyMap(),
            presetNotes = imported.notes.joinToString("\n"),
            palette = imported.palette?.let { java.util.Base64.getEncoder().encodeToString(it) }
        )
    }
}
