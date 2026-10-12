package llm.slop.liquidlsd.rendering

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlameImporterTest {
    private val rings2 = """<flame name="r2" size="250 250" center="-0.5 0.25" scale="281.25" rotate="0.0">
  <xform weight="0.5" color="0.0" symmetry="0.0" coefs="1.0 0.0 0.0 1.0 0.0 -0.5" rings2="1.0" rings2_val="0.14"/>
  <xform weight="1.5" color="1.0" symmetry="-1.0" coefs="0.0 1.0 -1.0 0.0 0.2 0.0" post="2.0 0.0 0.0 2.0 0.0 0.0" sinusoidal="0.5" swirl="0.25" lazysusan="1.0" lazysusan_fx_priority="0"/>
  <palette count="256" format="RGB">${"FF8000".repeat(256)}</palette>
</flame>"""

    private fun one(xml: String, o: FlameImporter.Options = FlameImporter.Options()) = FlameImporter.parse(xml, "t", o).single()
    /** The imported value in real units (the map holds the normalised, stored values). */
    private fun Map<String, Float>.f(k: String) = FlameUnits.real(k, getValue(k))

    @Test fun readsXformsVariationsAndCamera() {
        val r = one(rings2)
        assertEquals("r2", r.name)
        assertEquals(0.5f, r.values.f("X1 Weight")); assertEquals(1.5f, r.values.f("X2 Weight")); assertEquals(0f, r.values.f("X3 Weight"))
        assertEquals(0.5f, r.values.f("X1 ColorSpeed")); assertEquals(1f, r.values.f("X2 ColorSpeed"))
        assertEquals(FlameVariations.NAMES.indexOf("rings2").toFloat(), r.values.f("X1 V1 Id"))
        // rings2_val 0.14 inside 0.02..2: normalised and back
        assertEquals(0.14f, FlameVariations.real(FlameVariations.NAMES.indexOf("rings2"), 0, r.values.f("X1 V1 A")), 1e-4f)
        // strongest first: sinusoidal 0.5 then swirl 0.25; lazysusan is unknown
        assertEquals(FlameVariations.NAMES.indexOf("sinusoidal").toFloat(), r.values.f("X2 V1 Id"))
        assertEquals(FlameVariations.NAMES.indexOf("swirl").toFloat(), r.values.f("X2 V2 Id"))
        assertEquals(0f, r.values.f("X2 V3 Weight"))
        assertTrue(r.notes.any { "lazysusan" in it && "unsupported" in it })
        // camera: visible height = 250 / 281.25 units, Zoom = 2 * scale / height
        assertEquals(2f * 281.25f / 250f, r.values.f("Zoom"), 1e-4f)
        assertEquals(-0.5f, r.values.f("CenterX")); assertEquals(0.25f, r.values.f("CenterY"))
        assertEquals(768, r.palette?.size)
        assertEquals(0xFF.toByte(), r.palette!![0])
    }

    @Test fun affineRoundTripsThroughThePackedMatrix() {
        val r = one(rings2)
        val p = FlameGenome.buildParameters()
        for ((k, v) in r.values) p[k]?.set(v)
        p.values.forEach { it.evaluate() }
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        FlameGenome.pack(p, out)
        // X2 is the 90 degree rotation (0 1 -1 0): x' = a x + c y = -y, y' = b x + d y = x  -> row-major (0 -1 / 1 0)
        val o = 1 * FlameGenome.VEC4_PER_XF * 4
        assertEquals(listOf(0f, -1f, 1f, 0f), (4..7).map { out[o + it] }.map { if (abs(it) < 1e-5f) 0f else it })
        assertEquals(0.2f, out[o + 8], 1e-6f)
        // post 2 0 0 2 is a uniform scale of 2 (at the clamp limit)
        assertEquals(2f, out[o + 12], 1e-5f)
        assertEquals(2f, out[o + 15], 1e-5f)
    }

    @Test fun imageYDownIsACameraFlagNotAMirroredGenome() {
        val r = one(rings2)
        assertEquals(1f, r.values.f("FlipY"))
        assertEquals(0.25f, r.values.f("CenterY"))        // as in the file
        assertEquals(-0.5f, r.values.f("X1 PreY"))        // translations untouched
    }

    @Test fun rotationSignIsAnOption() {
        val xml = """<flame size="100 100" scale="50" rotate="30"/>"""
        assertEquals(Math.toRadians(30.0).toFloat(), one(xml).values.f("Rotation"), 1e-5f)
        assertEquals(-Math.toRadians(30.0).toFloat(), one(xml, FlameImporter.Options(flipRotation = true)).values.f("Rotation"), 1e-5f)
    }

    @Test fun transposedOptionReadsTheCoefficientsTheOtherWay() {
        val r = one(rings2, FlameImporter.Options(transposed = true))
        val p = FlameGenome.buildParameters()
        for ((k, v) in r.values) p[k]?.set(v)
        p.values.forEach { it.evaluate() }
        val out = FloatArray(FlameGenome.PACKED_FLOATS)
        FlameGenome.pack(p, out)
        val o = 1 * FlameGenome.VEC4_PER_XF * 4
        assertEquals(listOf(0f, 1f, -1f, 0f), (4..7).map { out[o + it] }.map { if (abs(it) < 1e-5f) 0f else it })
    }

    @Test fun weightsAreScaledToTheRangeKeepingRatiosAndExtraXformsAreReported() {
        val xs = (1..8).joinToString("") { """<xform weight="${it * 5}" color="0" coefs="1 0 0 1 0 0" linear="1"/>""" }
        val r = one("""<flame size="100 100" scale="50">$xs</flame>""")
        assertTrue(r.notes.any { "only the first 6" in it })
        assertEquals(2f, r.values.f("X6 Weight"), 1e-5f)            // 30 is the largest kept -> 2
        assertEquals(2f / 6f, r.values.f("X1 Weight"), 1e-5f)       // 5 / 30 * 2
        assertTrue(r.notes.any { "scaled" in it })
    }

    @Test fun finalXformAndOutOfRangeParametersAreHandled() {
        val r = one("""<flame size="100 100" scale="50"><xform weight="1" coefs="1 0 0 1 0 0" julian="1" julian_power="-40" julian_dist="0.55"/>
            <finalxform weight="1" coefs="0.5 0 0 0.5 0 0" linear="1"/></flame>""")
        assertTrue(r.notes.any { "julian".lowercase() in it.lowercase() && "clamped" in it })
        assertEquals(0.5f, r.values.f("XF PreScaleX"), 1e-5f)
        assertEquals(FlameVariations.NAMES.indexOf("juliaN").toFloat(), r.values.f("X1 V1 Id"))
    }

    @Test fun flamesWrapperBomAndMissingPaletteAreAccepted() {
        val list = FlameImporter.parse("\uFEFF<flames name=\"x\"><flame name=\"a\" size=\"10 10\" scale=\"5\"/><flame size=\"10 10\" scale=\"5\"/></flames>", "f")
        assertEquals(listOf("a", "f-2"), list.map { it.name })
        assertEquals(null, list[0].palette)
    }

    @Test fun doctypesAreRefused() {
        val evil = """<!DOCTYPE flame [<!ENTITY x SYSTEM "file:///etc/passwd">]><flame size="1 1" scale="1">&x;</flame>"""
        assertFalse(runCatching { FlameImporter.parse(evil) }.isSuccess)
    }

    @Test fun presetDtoCarriesTheGenomeWithMorphFlags() {
        val dto = FlameImporter.toPresetDto(one(rings2))
        assertEquals("fractal_flame", dto.visualSourceType)
        assertEquals(0.5f, FlameUnits.real("X1 Weight", dto.parameters.getValue("X1 Weight").baseValue))
        assertTrue(dto.parameters.getValue("X1 Weight").randomizeBase)
        assertTrue("imported" in dto.tags)
        assertEquals(FlameImporter.IMPORT_DECAY, FlameUnits.real("Decay", dto.parameters.getValue("Decay").baseValue), 1e-5f)
        assertEquals(0f, FlameUnits.real("Toe", dto.parameters.getValue("Toe").baseValue))
        // rings2 is 250 px with filter 0.75: a sprite about twice that radius wide at 1080 px deck height
        assertEquals(2f * 0.75f * 1080f / 250f, FlameUnits.real("Softness", dto.parameters.getValue("Softness").baseValue), 1e-3f)
        val pal = java.util.Base64.getDecoder().decode(dto.palette!!)
        assertEquals(768, pal.size)
        assertEquals(one(rings2).palette!!.toList(), pal.toList())
    }

    @Test fun everySampleFileInTempFlamesConvertsWhenPresent() {
        val dir = File("temp-flames")
        val files = dir.listFiles { f -> f.extension == "flame" } ?: return
        for (f in files) {
            val all = FlameImporter.parse(f.readText(), f.nameWithoutExtension)
            assertTrue(all.isNotEmpty(), f.name)
            for (r in all) {
                assertTrue(r.values.getValue("X1 Weight") > 0f, f.name)
                assertTrue(r.values.values.all { it.isFinite() }, f.name)
                println("${f.name}: ${r.notes.joinToString(" | ")}")
            }
        }
    }

    @Test fun wildFlameRangesAreWideEnoughForTheSamples() {
        val p = FlameGenome.buildParameters()
        assertTrue(FlameUnits.of("X1 PreX")!!.realMax >= 3.2f)             // gnarlomalia translates by 3.14
        assertTrue(FlameUnits.of("CenterX")!!.realMax >= 2.6f)
        val julian = FlameVariations.NAMES.indexOf("juliaN")
        assertEquals(-20f, FlameVariations.real(julian, 0, 0f))        // spirencor2 uses julian_power -20
        assertTrue(FlameVariations.ALL[FlameVariations.NAMES.indexOf("rings2")].params[0].min <= 0.012f)
    }

    private fun xform(attrs: String) = one("""<flame name="n" size="100 100" scale="50"><xform weight="1" coefs="1 0 0 1 0 0" $attrs/></flame>""")

    @Test fun readsTheNewVariationsAndTheirParametersByTheirFileNames() {
        val r = xform("""curve="1.0" curve_xamp="1.5" curve_ylength="2" wedge_julia="0.5" wedge_julia_power="3" """)
        val curve = FlameVariations.NAMES.indexOf("curve")
        assertEquals(curve.toFloat(), r.values.f("X1 V1 Id"))
        assertEquals(1.5f, FlameVariations.real(curve, 0, r.values.f("X1 V1 A")), 1e-4f)
        assertEquals(2f, FlameVariations.real(curve, 3, r.values.f("X1 V1 D")), 1e-4f)
        assertEquals(FlameVariations.NAMES.indexOf("wedge_julia").toFloat(), r.values.f("X1 V2 Id"))
        assertEquals(3f, FlameVariations.real(FlameVariations.NAMES.indexOf("wedge_julia"), 0, r.values.f("X1 V2 A")), 1e-4f)
        assertTrue(r.notes.none { "unsupported" in it })
    }

    @Test fun oscilloscopeIsReadUnderBothSpellings() {
        val osc = FlameVariations.NAMES.indexOf("oscope")
        for (attrs in listOf("""oscope="1" oscope_frequency="2" """, """oscilloscope="1" oscilloscope_frequency="2" """)) {
            val r = xform(attrs)
            assertEquals(osc.toFloat(), r.values.f("X1 V1 Id"), attrs)
            assertEquals(2f, FlameVariations.real(osc, 1, r.values.f("X1 V1 B")), 1e-4f, attrs)
        }
    }

    @Test fun aParameterOutsideTheRangeIsClampedWithANote() {
        val r = xform("""cell="1" cell_size="10" """)
        assertEquals(2.5f, FlameVariations.real(FlameVariations.NAMES.indexOf("cell"), 0, r.values.f("X1 V1 A")), 1e-4f)
        assertTrue(r.notes.any { "cell" in it && "clamped" in it })
    }

    @Test fun theThreeSkippedVariationsAreReportedAndNeverMapped() {
        val r = xform("""sinusoidal="1" super_shape="1" super_shape_m="4" lazysusan="0.5" mobius="1" mobius_re_a="1" Re_A="1" """)
        assertEquals(FlameVariations.NAMES.indexOf("sinusoidal").toFloat(), r.values.f("X1 V1 Id"))
        assertEquals(0f, r.values.f("X1 V2 Weight"))
        val note = r.notes.single { "unsupported" in it }
        for (n in listOf("super_shape", "lazysusan", "mobius")) assertTrue(n in note, note)
        assertFalse("super_shape_m" in note || "mobius_re_a" in note || "Re_A" in note, note)
        // a skipped variation with weight 0 is not a use
        assertTrue(xform("""sinusoidal="1" mobius="0" """).notes.none { "unsupported" in it })
    }

    @Test fun unknownPreAndPostVariationsAreReportedButTheirParametersAreNot() {
        val r = xform("""sinusoidal="1" pre_blur="0.1" pre_curl="0.5" pre_curl_c1="0.2" post_crop="1" post_crop_left="-1" post_ztranslate_wf="0" """)
        val note = r.notes.single { "unsupported" in it }
        assertTrue("pre_curl" in note && "post_crop" in note, note)
        assertFalse("pre_curl_c1" in note || "post_crop_left" in note || "pre_blur" in note || "post_ztranslate_wf" in note, note)
    }
}
