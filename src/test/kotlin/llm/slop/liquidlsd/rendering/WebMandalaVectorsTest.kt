package llm.slop.liquidlsd.rendering

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden vectors for web/mandala.js: which recipe and hue-cycle count the desktop picks for a
 * given Lobes / Recipe Select / Hue Sweep. The web side replays them in
 * `node --test web/tools/mandala.test.mjs`.
 *
 * Fails when the committed web/tools/mandala_vectors.json is stale (e.g. the recipe library
 * changed). Regenerate with `UPDATE_WEB_VECTORS=1 ./gradlew test --tests '*WebMandalaVectorsTest*'`.
 * Selects and sweeps are dyadic fractions so float and double arithmetic round identically.
 */
class WebMandalaVectorsTest {

    private val projectRoot: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }

    @Test
    fun vectorsMatchDesktopSelection() {
        val selects = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        val sweeps = listOf(0.0f, 0.5f, 1.0f)
        val rows = StringBuilder()
        for (lobes in 0..32) {
            for (select in selects) {
                val recipe = Mandala.pickRecipe(lobes, select) ?: continue
                for (sweep in sweeps) {
                    if (rows.isNotEmpty()) rows.append(",\n")
                    rows.append(
                        "[$lobes,$select,$sweep,${recipe.a},${recipe.b},${recipe.c},${recipe.d},${recipe.petals}," +
                            "${Mandala.hueSweepCycles(recipe.petals, sweep).toInt()}]"
                    )
                }
            }
        }
        val text = "{\"fields\":[\"lobes\",\"select\",\"sweep\",\"a\",\"b\",\"c\",\"d\",\"petals\",\"hueCycles\"],\n\"rows\":[\n$rows\n]}\n"
        val file = File(projectRoot, "web/tools/mandala_vectors.json")
        if (System.getenv("UPDATE_WEB_VECTORS") == "1" || !file.exists()) {
            file.writeText(text)
        } else {
            assertEquals(file.readText(), text, "web/tools/mandala_vectors.json is stale; regenerate with UPDATE_WEB_VECTORS=1")
        }
    }

    @Test
    fun webRecipeTableMatchesLibrary() {
        val file = File(projectRoot, "web/mandala_recipes.json")
        val expected = MandalaLibrary.MandalaRatios.joinToString(",") { "[${it.a},${it.b},${it.c},${it.d},${it.petals}]" }
        assertEquals(
            "{\"fields\":[\"a\",\"b\",\"c\",\"d\",\"petals\"],\"recipes\":[$expected]}\n",
            file.readText(),
            "web/mandala_recipes.json is stale; run ./scripts/sync_web.py --apply"
        )
    }
}
