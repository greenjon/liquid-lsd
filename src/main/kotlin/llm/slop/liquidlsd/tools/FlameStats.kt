package llm.slop.liquidlsd.tools

import llm.slop.liquidlsd.rendering.FlameImporter
import llm.slop.liquidlsd.rendering.FlameVariations
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Which variations do good flames use? Tallies the variations of every flame in one or more folders of `.flame` /
 * `.flam3` files and ranks them per source and across sources, marking each as ours, reserved (named but not built)
 * or missing. Only counts leave this tool: no genome is copied, stored or shipped. Parsing is plain XML with DTDs
 * refused (the files are untrusted). Needs no GL.
 *
 *   ./gradlew flameStats -Pargs="--source es=/data/sheep --source loot=/data/loot --out build/flame-stats"
 *   add "--survival build/curated-flames/variation-survival.csv" for the curator's survival rates
 */
object FlameStats {
    private class Counts {
        var flames = 0; var xforms = 0
        val flamesUsing = HashMap<String, Int>()
        val xformsUsing = HashMap<String, Int>()
        val dominant = HashMap<String, Int>()
        val solo = HashMap<String, Int>()
        val weightSum = HashMap<String, Double>()
        val pairs = HashMap<String, Int>()
        val xformsPerFlame = HashMap<Int, Int>()
        val varsPerXform = HashMap<Int, Int>()
        var withFinal = 0
        var duplicates = 0
        var unreadable = 0
    }

    @JvmStatic fun main(args: Array<String>) {
        val sources = ArrayList<Pair<String, File>>()
        var out = File("build/flame-stats")
        var survival: File? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--source" -> args[++i].split("=", limit = 2).let { sources += it[0] to File(it[1]) }
                "--out" -> out = File(args[++i])
                "--survival" -> survival = File(args[++i])
                else -> error("unknown argument ${args[i]}")
            }
            i++
        }
        require(sources.isNotEmpty()) { "give at least one --source label=folder" }
        out.mkdirs()
        val counts = LinkedHashMap<String, Counts>()
        for ((label, dir) in sources) counts[label] = tally(dir)
        write(out, counts, survival?.let(::readSurvival).orEmpty())
    }

    private fun parse(file: File): List<org.w3c.dom.Element>? = try {
        val f = DocumentBuilderFactory.newInstance()
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        f.isXIncludeAware = false; f.isExpandEntityReferences = false
        // some files hold several top-level elements: wrap them so they are one document (a leading XML declaration goes)
        val text = file.readText().trimStart('\uFEFF', ' ', '\n', '\r', '\t').replace(Regex("^<\\?xml[^>]*\\?>"), "")
        val root = f.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader("<wrapper>$text</wrapper>"))).documentElement
        fun flamesIn(e: org.w3c.dom.Element): List<org.w3c.dom.Element> =
            (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? org.w3c.dom.Element }
                .flatMap { if (it.tagName == "flame") listOf(it) else if (it.tagName == "flames") flamesIn(it) else emptyList() }
        flamesIn(root)
    } catch (e: Exception) { null }

    /** The variations an xform uses (name -> weight): numeric, non-zero attributes that are neither metadata nor parameters of another attribute. */
    private fun variations(xf: org.w3c.dom.Element): Map<String, Float> {
        val attrs = (0 until xf.attributes.length).map { xf.attributes.item(it).nodeName to xf.attributes.item(it).nodeValue.trim() }
        val names = attrs.map { it.first.lowercase() }.toSet()
        val out = LinkedHashMap<String, Float>()
        for ((n, v) in attrs) {
            val lower = n.lowercase()
            val w = v.toFloatOrNull() ?: continue
            if (w == 0f || FlameImporter.isMeta(n)) continue
            // "julian_power" is a parameter when "julian" is also an attribute, or when "julian" is a variation we know
            val isParam = lower.indices.any { lower[it] == '_' && (lower.substring(0, it) in names && lower.substring(0, it) != lower || FlameImporter.variationIdForFileName(lower.substring(0, it)) != null || FlameImporter.isReserved(lower.substring(0, it))) }
            if (isParam) continue
            out[lower] = w
        }
        return out
    }

    private fun tally(dir: File): Counts {
        val c = Counts()
        val seen = HashSet<Int>()
        dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("flame", "flam3", "xml") && it.length() < 8L * 1024 * 1024 }.forEach { file ->
            val flames = parse(file)
            if (flames == null) { c.unreadable++; return@forEach }
            for (fl in flames) {
                val xfs = (0 until fl.childNodes.length).mapNotNull { fl.childNodes.item(it) as? org.w3c.dom.Element }
                val normal = xfs.filter { it.tagName == "xform" }
                if (normal.isEmpty()) continue
                val perXform = normal.map { variations(it) }
                // identical genomes (a lineage keeps copies) count once
                if (!seen.add(perXform.map { it.toSortedMap().toString() }.hashCode() * 31 + normal.size)) { c.duplicates++; continue }
                c.flames++
                c.xforms += normal.size
                c.xformsPerFlame.merge(normal.size, 1, Int::plus)
                if (xfs.any { it.tagName == "finalxform" }) c.withFinal++
                val inFlame = HashSet<String>()
                for (v in perXform) {
                    c.varsPerXform.merge(v.size, 1, Int::plus)
                    if (v.isEmpty()) continue
                    v.maxByOrNull { abs(it.value) }!!.key.let { c.dominant.merge(it, 1, Int::plus) }
                    if (v.size == 1) c.solo.merge(v.keys.first(), 1, Int::plus)
                    for ((k, w) in v) { c.xformsUsing.merge(k, 1, Int::plus); c.weightSum.merge(k, abs(w.toDouble()), Double::plus); inFlame += k }
                    val ks = v.keys.sorted()
                    for (a in ks.indices) for (b in a + 1 until ks.size) c.pairs.merge("${ks[a]}+${ks[b]}", 1, Int::plus)
                }
                for (k in inFlame) c.flamesUsing.merge(k, 1, Int::plus)
            }
        }
        return c
    }

    private class Survival(val tried: Int, val accepted: Int)

    private fun readSurvival(f: File): Map<String, Survival> =
        f.readLines().drop(1).map { it.split(",") }.filter { it.size >= 3 }.associate { it[0].lowercase() to Survival(it[1].toInt(), it[2].toInt()) }

    private fun status(name: String) = when {
        FlameImporter.variationIdForFileName(name) != null -> "ours"
        FlameImporter.isReserved(name) -> "reserved"
        else -> "MISSING"
    }

    private fun write(out: File, counts: Map<String, Counts>, survival: Map<String, Survival>) {
        val labels = counts.keys.toList()
        val all = counts.values.flatMap { it.flamesUsing.keys }.toSet()
        fun share(c: Counts, v: String) = (c.flamesUsing[v] ?: 0).toDouble() / c.flames.coerceAtLeast(1)
        // consensus: the lowest share across sources, so a variation must be common everywhere to rank high
        // sources under 100 flames show up in the table but do not set the consensus (one showpiece each says nothing)
        val voting = counts.values.filter { it.flames >= 100 }.ifEmpty { counts.values.toList() }
        val ranked = all.sortedByDescending { v -> voting.minOf { share(it, v) } * 1000 + voting.sumOf { share(it, v) } }

        val md = StringBuilder("# Variation usage\n\n")
        for ((l, c) in counts) md.append("- **$l**: ${c.flames} flames (${c.duplicates} exact duplicates skipped, ${c.unreadable} files unreadable), ${c.xforms} xforms, ${"%.0f".format(100.0 * c.withFinal / c.flames.coerceAtLeast(1))}% with a final xform\n")
        md.append("\nRanked by the lowest share of flames across sources with 100+ flames (a variation must be common everywhere), then the sum. `share` = % of flames using it; `dom` = % of its xforms where it is the strongest variation; `solo` = % where it is the only one.\n\n")
        md.append("| # | variation | status | ${labels.joinToString(" | ") { "$it share" }} | dom | solo | mean weight | survival |\n|---|---|---|${labels.joinToString("|") { "---" }}|---|---|---|---|\n")
        val csv = StringBuilder("variation,status,${labels.joinToString(",") { "${it}_share" }},dominant_pct,solo_pct,mean_weight,tried,accepted\n")
        ranked.forEachIndexed { rank, v ->
            val xf = counts.values.sumOf { it.xformsUsing[v] ?: 0 }.coerceAtLeast(1)
            val dom = 100.0 * counts.values.sumOf { it.dominant[v] ?: 0 } / xf
            val solo = 100.0 * counts.values.sumOf { it.solo[v] ?: 0 } / xf
            val mw = counts.values.sumOf { it.weightSum[v] ?: 0.0 } / xf
            val s = survival[v]
            val shares = labels.map { 100 * share(counts.getValue(it), v) }
            if (rank < 120) md.append("| ${rank + 1} | $v | ${status(v)} | ${shares.joinToString(" | ") { "%.1f".format(it) }} | %.0f | %.0f | %.2f | %s |\n"
                .format(dom, solo, mw, s?.let { "%.0f%% of %d".format(100.0 * it.accepted / it.tried.coerceAtLeast(1), it.tried) } ?: ""))
            csv.append("$v,${status(v)},${shares.joinToString(",") { "%.2f".format(it) }},%.1f,%.1f,%.3f,%d,%d\n".format(dom, solo, mw, s?.tried ?: 0, s?.accepted ?: 0))
        }
        md.append("\n## Missing from our set, by consensus rank\n\n")
        ranked.withIndex().filter { status(it.value) == "MISSING" }.take(60).forEach { (r, v) -> md.append("${r + 1}. $v\n") }
        md.append("\n## Structure\n\n")
        for ((l, c) in counts) {
            md.append("**$l** xforms per flame: ${c.xformsPerFlame.toSortedMap().entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n\n")
            md.append("**$l** variations per xform: ${c.varsPerXform.toSortedMap().entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n\n")
            md.append("**$l** most common pairs (xforms): ${c.pairs.entries.sortedByDescending { it.value }.take(25).joinToString(", ") { "${it.key} ${it.value}" }}\n\n")
        }
        File(out, "stats.md").writeText(md.toString())
        File(out, "variations.csv").writeText(csv.toString())
        println("Wrote ${File(out, "stats.md")} and variations.csv")
        println(md.lineSequence().take(8 + labels.size).joinToString("\n"))
    }
}
