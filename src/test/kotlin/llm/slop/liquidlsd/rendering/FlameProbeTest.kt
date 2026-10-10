package llm.slop.liquidlsd.rendering

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the quality gate on the real iterate shader on a hidden GL 3.3 context; skipped where no context can be made. */
class FlameProbeTest {
    /**
     * One hidden context for the whole class, never destroyed: [Geometry] caches its fullscreen-quad VAO, and VAOs are
     * not shared between contexts, so a second context in the same JVM would draw nothing.
     */
    private fun withGl(body: () -> Unit) {
        if (window == 0L) {
            val hasGlfw = try { glfwInit() } catch (e: Throwable) { false }
            assumeTrue(hasGlfw, "GLFW unavailable; skipping")
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            window = glfwCreateWindow(64, 64, "Flame probe test", 0L, 0L)
            assumeTrue(window != 0L, "No offscreen GL context; skipping")
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
        }
        body()
    }

    private companion object { var window = 0L }

    private val params = FlameGenome.buildParameters()

    @Test fun starterPassesAndIsFramed() = withGl {
        val probe = FlameProbe()
        val r = probe.evaluate { null }
        probe.dispose()
        assertTrue(r.ok, r.toString())
        assertTrue(r.zoom in 0.05f..4f)
    }

    @Test fun degenerateGenomesAreRejected() = withGl {
        val probe = FlameProbe()
        // every xform scaled to (almost) nothing: the attractor is a dot
        val dot = probe.evaluate { name -> if (" PreScale" in name) 0.0005f else if (name.endsWith(" PreX") || name.endsWith(" PreY")) 0f else null }
        // affine maps that all squash y onto the x axis: the attractor is a line
        val line = probe.evaluate { name ->
            when {
                name.endsWith(" PreScaleY") -> 0.05f
                name.endsWith(" PreRot") || name.endsWith(" PreShear") || name.endsWith(" PreY") -> 0f
                name.endsWith(" V1 Id") -> 0f
                name.endsWith(" V1 Weight") -> 1f
                name.endsWith(" V2 Weight") || name.endsWith(" V3 Weight") -> 0f
                else -> null
            }
        }
        // wildly expanding maps: points escape and are reseeded
        val boom = probe.evaluate { name -> if (name.endsWith(" PreScaleX") || name.endsWith(" PreScaleY")) 2f else null }
        probe.dispose()
        assertTrue(!dot.ok, "dot: $dot")
        assertTrue(!line.ok, "line: $line")
        assertTrue(!boom.ok, "boom: $boom")
    }

    /** Calibration aid: how many fresh random genomes pass, and why the others fail (printed, loosely asserted). */
    @Test fun randomGenomesMostlyPassAndSomeFail() = withGl {
        val probe = FlameProbe()
        val verdicts = HashMap<FlameQuality.Verdict, Int>()
        val occupancies = ArrayList<Float>()
        val total = 300
        val rng = Random(42)
        repeat(total) {
            val cand = FlameSampler.random(params, rng)
            val r = probe.evaluate { cand[it] }
            verdicts.merge(r.verdict, 1, Int::plus)
            if (r.ok) occupancies += r.occupancy
        }
        println("lowest occupancies: " + occupancies.sorted().take(12).joinToString { "%.3f".format(it) } +
            " | highest: " + occupancies.sorted().takeLast(5).joinToString { "%.3f".format(it) })
        probe.dispose()
        println("random genomes: $verdicts")
        val ok = verdicts[FlameQuality.Verdict.OK] ?: 0
        assertTrue(ok > total * 0.2, "too few pass: $verdicts")
        assertTrue(ok < total, "gate rejects nothing: $verdicts")
    }

    @Test fun mutationsOfTheStarterMostlyPassAndHighStrengthFailsMore() = withGl {
        val probe = FlameProbe()
        val from: (String) -> Float = { params[it]?.baseValue ?: 0f }
        val rejected = HashMap<Float, Int>()
        val rng = Random(7)
        for (strength in listOf(0.25f, 1f)) {
            repeat(200) {
                val cand = FlameSampler.mutate(params, from, strength, rng)
                if (!probe.evaluate { cand[it] ?: from(it) }.ok) rejected.merge(strength, 1, Int::plus)
            }
        }
        probe.dispose()
        println("mutations rejected of 200: $rejected")
        assertTrue((rejected[0.25f] ?: 0) < 60, "$rejected")
    }

    @Test fun forceRandomizeBuildsAFramedGenomeThatPassesTheGate() = withGl {
        val source = FlameSource(
            "fractal_flame", "Fractal Flame",
            Shader(FlameEngine.FULLSCREEN_VERT, "#version 330 core\nout vec4 o;\nvoid main(){o=vec4(1.0);}"),
            FlameGenome.buildParameters(), ownsShader = true
        )
        val controller = DeckMorphController({ source }) { source.parameters.values.toList() }
        val probe = FlameProbe()
        val zooms = HashSet<Float>()
        repeat(20) { seed ->
            controller.forceRandomize(Random(seed))
            val p = source.parameters
            val active = (0 until FlameGenome.MAX_XF).count { p[FlameGenome.weightKey(it)]!!.baseValue > 0.05f }
            assertTrue(active in 2..5, "active=$active")
            zooms += p["Zoom"]!!.baseValue
            // the live genome is what the gate accepted (or the starter), so it passes again
            val r = probe.evaluate { p[it]?.baseValue }
            assertTrue(r.ok, "seed $seed: $r")
            assertEquals(r.zoom, p["Zoom"]!!.baseValue, 0.02f * r.zoom + 0.05f)
        }
        assertTrue(zooms.size > 5, "camera follows the genome: $zooms")
        probe.dispose(); source.dispose()
    }

    private fun flameSource() = FlameSource(
        "fractal_flame", "Fractal Flame",
        Shader(FlameEngine.FULLSCREEN_VERT, "#version 330 core\nout vec4 o;\nvoid main(){o=vec4(1.0);}"),
        FlameGenome.buildParameters(), ownsShader = true
    )

    private fun member(name: String, seed: Int, zoom: Float): PoolMember {
        val r = FlameSampler.random(params, Random(seed))
        return PoolMember(name, emptyList(), params.mapValues { r[it.key] ?: it.value.baseValue } + ("Zoom" to zoom))
    }

    @Test fun poolAndBreedStrategiesUseTheGarden() = withGl {
        val source = flameSource()
        val a = member("a", 1, 0.8f); val b = member("b", 2, 1.3f)
        source.poolProvider = { listOf(a, b) }
        val controller = DeckMorphController({ source }) { source.parameters.values.toList() }

        controller.strategy = SamplerStrategy.POOL
        controller.forceRandomize(Random(1))
        val p = source.parameters
        fun matches(m: PoolMember) = p.filter { (n, q) -> q.randomizeBase && !n.startsWith("XF") }.all { (n, q) -> q.baseValue == m.values[n] }
        assertTrue(matches(a) || matches(b), "live genome is a kept offspring")
        assertTrue(p["Zoom"]!!.baseValue == 0.8f || p["Zoom"]!!.baseValue == 1.3f, "offspring's own camera")

        controller.strategy = SamplerStrategy.BREED
        repeat(10) { seed ->
            controller.forceRandomize(Random(seed))
            val active = (0 until FlameGenome.MAX_XF).count { p[FlameGenome.weightKey(it)]!!.baseValue > 0.05f }
            assertTrue(active >= 2)
        }
        source.dispose()
    }

    @Test fun emptyPoolFallsBackToRandomAndDefaultStrategyIsMutate() = withGl {
        val source = flameSource()
        source.poolProvider = { emptyList() }
        val controller = DeckMorphController({ source }) { source.parameters.values.toList() }
        assertEquals(SamplerStrategy.MUTATE, controller.strategy)
        controller.strategy = SamplerStrategy.POOL
        controller.forceRandomize(Random(4))
        val active = (0 until FlameGenome.MAX_XF).count { source.parameters[FlameGenome.weightKey(it)]!!.baseValue > 0.05f }
        assertTrue(active in 2..5)
        source.dispose()
    }

    @Test fun probeRestoresGlState() = withGl {
        val probe = FlameProbe()
        org.lwjgl.opengl.GL33.glViewport(3, 4, 50, 60)
        org.lwjgl.opengl.GL33.glEnable(org.lwjgl.opengl.GL33.GL_BLEND)
        probe.evaluate { null }
        val vp = IntArray(4); org.lwjgl.opengl.GL33.glGetIntegerv(org.lwjgl.opengl.GL33.GL_VIEWPORT, vp)
        assertEquals(listOf(3, 4, 50, 60), vp.toList())
        assertTrue(org.lwjgl.opengl.GL33.glIsEnabled(org.lwjgl.opengl.GL33.GL_BLEND))
        assertEquals(0, org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_CURRENT_PROGRAM))
        probe.dispose()
    }

    /** Eyeball aid, off by default: `FLAME_SHEET=/tmp/sheet.png` writes a contact sheet of random genomes (red border = rejected). */
    @Test fun contactSheet() = withGl {
        val path = System.getenv("FLAME_SHEET") ?: return@withGl
        val probe = FlameProbe()
        val cell = 200; val cols = 8; val rows = 6
        val img = java.awt.image.BufferedImage(cell * cols, cell * rows, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val rng = Random(System.getenv("FLAME_SEED")?.toInt() ?: 1)
        for (k in 0 until cols * rows) {
            val cand = FlameSampler.random(params, rng)
            val r = probe.evaluate { cand[it] }
            val pts = probe.lastPoints()
            val hits = IntArray(cell * cell)
            val aspect = 1f
            for (i in 0 until pts.size / 4) {
                if (pts[i * 4 + 3] < FlameProbe.ITERATIONS - 0.5f) continue
                val x = ((pts[i * 4] - r.centerX) * r.zoom / aspect * 0.5f + 0.5f) * cell
                val y = ((pts[i * 4 + 1] - r.centerY) * r.zoom * 0.5f + 0.5f) * cell
                if (x >= 0 && x < cell && y >= 0 && y < cell) hits[y.toInt() * cell + x.toInt()]++
            }
            val ox = (k % cols) * cell; val oy = (k / cols) * cell
            for (j in hits.indices) {
                val v = (kotlin.math.ln(1f + hits[j]) / 3f).coerceIn(0f, 1f)
                val c = (v * 255).toInt()
                img.setRGB(ox + j % cell, oy + j / cell, (c shl 16) or (c shl 8) or c)
            }
            if (!r.ok) for (j in 0 until cell) for (e in 0..1) {
                img.setRGB(ox + j, oy + e, 0xFF0000); img.setRGB(ox + e, oy + j, 0xFF0000)
            }
        }
        probe.dispose()
        javax.imageio.ImageIO.write(img, "png", java.io.File(path))
    }
}
