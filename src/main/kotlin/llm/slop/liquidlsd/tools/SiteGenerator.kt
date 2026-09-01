package llm.slop.liquidlsd.tools

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure JVM static website and documentation generator for greenjon.com.
 *
 * Compiles all markdown guides in `docs/`, injects metadata (version, GitHub URLs, release links),
 * generates HTML pages with responsive dark UI matching Liquid LSD, bundles `docs.zip`,
 * and outputs everything to `./greenjon/` ready for FTP upload.
 */
object SiteGenerator {

    private val markdownParser: Parser
    private val htmlRenderer: HtmlRenderer

    init {
        val options = MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(
                TablesExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AnchorLinkExtension.create()
            ))
            set(HtmlRenderer.GENERATE_HEADER_ID, true)
        }
        markdownParser = Parser.builder(options).build()
        htmlRenderer = HtmlRenderer.builder(options).build()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val projectDir = File(if (args.isNotEmpty()) args[0] else ".").canonicalFile
        val outputDir = File(if (args.size > 1) args[1] else "${projectDir.path}/greenjon").canonicalFile
        val version = if (args.size > 2) args[2] else "1.0-SNAPSHOT"
        val githubUrl = if (args.size > 3) args[3] else "https://github.com/greenjon/liquid-lsd"

        println("==================================================")
        println("Liquid LSD Website & Docs Generator (greenjon.com)")
        println("==================================================")
        println("Project root: ${projectDir.path}")
        println("Output dir:   ${outputDir.path}")
        println("App Version:  $version")
        println("GitHub URL:   $githubUrl")

        outputDir.mkdirs()

        // 1. Load Nav structure from mkdocs.yml
        val navItems = parseNavStructure(File(projectDir, "mkdocs.yml"))

        // 2. Load HTML Templates
        val templatesDir = File(projectDir, "website/templates")
        val indexTemplate = File(templatesDir, "index.html").readText()
        val docPageTemplate = File(templatesDir, "doc_page.html").readText()

        // 3. Render Landing Page (index.html)
        val latestReleaseUrl = "$githubUrl/releases/latest"
        val renderedIndex = indexTemplate
            .replace("{{VERSION}}", version)
            .replace("{{GITHUB_URL}}", githubUrl)
            .replace("{{LATEST_RELEASE_URL}}", latestReleaseUrl)

        File(outputDir, "index.html").writeText(renderedIndex)
        println("✓ Rendered index.html")

        // 4. Render Documentation Pages (docs/*.html)
        val docsSourceDir = File(projectDir, "docs")
        val docsOutputDir = File(outputDir, "docs")
        docsOutputDir.mkdirs()

        val allDocs = scanMarkdownFiles(docsSourceDir)
        println("Found ${allDocs.size} markdown doc files in docs/")

        for (docFile in allDocs) {
            val relPath = docFile.relativeTo(docsSourceDir).path
            val relHtmlPath = relPath.removeSuffix(".md") + ".html"
            val targetHtmlFile = File(docsOutputDir, relHtmlPath)
            targetHtmlFile.parentFile?.mkdirs()

            // Determine depth for relative assets (e.g. "../" or "../../")
            val depth = relHtmlPath.count { it == '/' || it == '\\' } + 1
            val rootPath = "../".repeat(depth)

            // Parse Markdown
            val rawMarkdown = docFile.readText()
            val (pageTitle, cleanedMarkdown) = extractTitleAndCleanMarkdown(rawMarkdown, docFile.nameWithoutExtension)
            val document = markdownParser.parse(cleanedMarkdown)
            var contentHtml = htmlRenderer.render(document)

            // Convert .md links to .html
            contentHtml = rewriteMarkdownLinks(contentHtml)
            // Enhance alerts (> [!NOTE], > [!WARNING], etc.)
            contentHtml = enhanceAlertBlockquotes(contentHtml)

            // Build sidebar navigation for this specific page
            val sidebarNavHtml = buildSidebarNavHtml(navItems, relHtmlPath, rootPath)

            val renderedDocPage = docPageTemplate
                .replace("{{PAGE_TITLE}}", pageTitle)
                .replace("{{ROOT_PATH}}", rootPath)
                .replace("{{SIDEBAR_NAV_HTML}}", sidebarNavHtml)
                .replace("{{CONTENT_HTML}}", contentHtml)
                .replace("{{SOURCE_MD_PATH}}", relPath)
                .replace("{{VERSION}}", version)
                .replace("{{GITHUB_URL}}", githubUrl)
                .replace("{{LATEST_RELEASE_URL}}", latestReleaseUrl)

            targetHtmlFile.writeText(renderedDocPage)
            println("  ✓ Rendered docs/$relHtmlPath")
        }

        // 5. Copy Static Assets
        val assetsSourceDir = File(projectDir, "website/assets")
        val assetsOutputDir = File(outputDir, "assets")
        if (assetsSourceDir.exists()) {
            assetsSourceDir.copyRecursively(assetsOutputDir, overwrite = true)
            println("✓ Copied assets to greenjon/assets/")
        }

        // 6. Generate docs.zip Archive
        val zipFile = File(outputDir, "docs.zip")
        generateDocsZip(docsSourceDir, zipFile)
        println("✓ Created offline documentation package: ${zipFile.name} (${zipFile.length() / 1024} KB)")

        println("==================================================")
        println("Website generation completed successfully!")
        println("FTP ready in: ${outputDir.absolutePath}")
        println("==================================================")
    }

    data class NavEntry(
        val title: String,
        val path: String? = null,
        val children: List<NavEntry> = emptyList()
    )

    private fun parseNavStructure(mkdocsFile: File): List<NavEntry> {
        if (!mkdocsFile.exists()) return emptyList()
        val yaml = Yaml()
        val data = yaml.load<Map<String, Any>>(mkdocsFile.readText())
        val rawNav = data["nav"] as? List<*> ?: return emptyList()

        return parseNavList(rawNav)
    }

    private fun parseNavList(list: List<*>): List<NavEntry> {
        val result = mutableListOf<NavEntry>()
        for (item in list) {
            when (item) {
                is Map<*, *> -> {
                    for ((k, v) in item) {
                        val title = k.toString()
                        when (v) {
                            is String -> {
                                val htmlPath = v.removeSuffix(".md") + ".html"
                                result.add(NavEntry(title, htmlPath))
                            }
                            is List<*> -> {
                                val children = parseNavList(v)
                                result.add(NavEntry(title, null, children))
                            }
                        }
                    }
                }
                is String -> {
                    val htmlPath = item.removeSuffix(".md") + ".html"
                    result.add(NavEntry(item.removeSuffix(".md"), htmlPath))
                }
            }
        }
        return result
    }

    private fun scanMarkdownFiles(dir: File): List<File> {
        return dir.walkTopDown().filter { it.isFile && it.extension.equals("md", ignoreCase = true) }.toList()
    }

    private fun extractTitleAndCleanMarkdown(markdown: String, fallback: String): Pair<String, String> {
        val lines = markdown.lines()
        var title = fallback.replace('_', ' ').replace('-', ' ').capitalizeWords()
        var titleFound = false
        val newLines = mutableListOf<String>()

        for (line in lines) {
            if (!titleFound && line.startsWith("# ")) {
                title = line.removePrefix("# ").trim()
                titleFound = true
                // We preserve the h1 or include it in content
                newLines.add(line)
            } else {
                newLines.add(line)
            }
        }
        return Pair(title, newLines.joinToString("\n"))
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    private fun rewriteMarkdownLinks(html: String): String {
        // Rewrite href="...*.md" to href="...*.html"
        val regex = Regex("""href="([^"#:]+)\.md(#?[^"]*)"""")
        return regex.replace(html) { match ->
            val path = match.groupValues[1]
            val fragment = match.groupValues[2]
            """href="$path.html$fragment""""
        }
    }

    private fun enhanceAlertBlockquotes(html: String): String {
        return html
            .replace("<blockquote>\n<p>[!NOTE]", "<blockquote class=\"alert-note\">\n<p><strong>Note:</strong>")
            .replace("<blockquote>\n<p>[!TIP]", "<blockquote class=\"alert-tip\">\n<p><strong>Tip:</strong>")
            .replace("<blockquote>\n<p>[!IMPORTANT]", "<blockquote class=\"alert-important\">\n<p><strong>Important:</strong>")
            .replace("<blockquote>\n<p>[!WARNING]", "<blockquote class=\"alert-warning\">\n<p><strong>Warning:</strong>")
            .replace("<blockquote>\n<p>[!CAUTION]", "<blockquote class=\"alert-caution\">\n<p><strong>Caution:</strong>")
    }

    private fun buildSidebarNavHtml(navItems: List<NavEntry>, currentRelHtmlPath: String, rootPath: String): String {
        val sb = StringBuilder()
        for (item in navItems) {
            if (item.children.isNotEmpty()) {
                sb.append("<div class=\"sidebar-group\">\n")
                sb.append("  <div class=\"sidebar-group-title\">${escapeHtml(item.title)}</div>\n")
                sb.append("  <ul>\n")
                for (child in item.children) {
                    val isActive = child.path?.equals(currentRelHtmlPath, ignoreCase = true) == true
                    val activeClass = if (isActive) " class=\"active\"" else ""
                    val href = if (child.path != null) "${rootPath}docs/${child.path}" else "#"
                    sb.append("    <li><a href=\"$href\"$activeClass>${escapeHtml(child.title)}</a></li>\n")
                }
                sb.append("  </ul>\n")
                sb.append("</div>\n")
            } else if (item.path != null) {
                val isActive = item.path.equals(currentRelHtmlPath, ignoreCase = true)
                val activeClass = if (isActive) " class=\"active\"" else ""
                val href = "${rootPath}docs/${item.path}"
                sb.append("<div class=\"sidebar-group\">\n")
                sb.append("  <ul>\n")
                sb.append("    <li><a href=\"$href\"$activeClass><strong>${escapeHtml(item.title)}</strong></a></li>\n")
                sb.append("  </ul>\n")
                sb.append("</div>\n")
            }
        }
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun generateDocsZip(docsDir: File, zipFile: File) {
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            docsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = "liquid-lsd-docs/" + file.relativeTo(docsDir).path
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
