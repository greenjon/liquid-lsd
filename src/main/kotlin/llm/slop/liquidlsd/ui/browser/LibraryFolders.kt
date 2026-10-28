package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.FileSystemManager
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files

/**
 * Folders inside the saved sections of the Library (Saved presets, Saved single FX, Saved chains, Saved transitions): listing,
 * creating, renaming, moving and deleting them, and moving saved files between them. Every function here only touches the disk and
 * returns what moved or went; the caller hands that to [LibraryRefs] so playlists, queues and the rest follow. Stock sections have no
 * folder on disk and are never managed.
 */
object LibraryFolders {
    private val logger = KotlinLogging.logger {}

    /** The directory a saved section lives in, or null for sections that are not files (stock, live video). */
    fun rootOf(kind: BrowseKind, section: BrowseSection): File? = when (section) {
        BrowseSection.SAVED -> when (kind) {
            BrowseKind.SRC -> FileSystemManager.getPresetsRoot()
            BrowseKind.TRANS -> FileSystemManager.getTransitionsRoot()
            BrowseKind.FX -> null
        }
        BrowseSection.SINGLE -> FileSystemManager.getFxPresetsRoot()
        BrowseSection.CHAIN -> FileSystemManager.getFxChainsRoot()
        BrowseSection.STOCK, BrowseSection.LIVE -> null
    }

    /** The file extension a saved section holds. */
    fun extensionOf(kind: BrowseKind, section: BrowseSection): String? = when (section) {
        BrowseSection.SAVED -> if (kind == BrowseKind.SRC) "lsd" else if (kind == BrowseKind.TRANS) "lsdtrans" else null
        BrowseSection.SINGLE -> "lsdfx"
        BrowseSection.CHAIN -> "lsdfxchain"
        BrowseSection.STOCK, BrowseSection.LIVE -> null
    }

    /** The saved section of [kind] that holds [file], by its extension. */
    fun sectionOf(kind: BrowseKind, file: File): BrowseSection? =
        BrowseCatalog.sectionsFor(kind).firstOrNull { extensionOf(kind, it).equals(file.extension, ignoreCase = true) }

    /** The directory of [folder] ('/'-separated, "" for the top level) under [root]. */
    fun dirOf(root: File, folder: String): File = if (folder.isEmpty()) root else File(root, folder)

    private class DirCache(val at: Long, val dirs: List<String>)
    private val dirCache = HashMap<String, DirCache>()
    private const val DIR_CACHE_MS = 1000L

    /** Drops the folder listings, so a folder just created, renamed or deleted shows at once. */
    fun invalidate() = dirCache.clear()

    /**
     * Every folder under [root], '/'-separated and relative, parents before children; hidden folders (".trash") are left out.
     * Cached for a second, since the tree asks every frame.
     */
    fun dirsUnder(root: File): List<String> {
        val key = root.absolutePath
        val now = System.currentTimeMillis()
        dirCache[key]?.takeIf { now - it.at < DIR_CACHE_MS }?.let { return it.dirs }
        val dirs = if (!root.isDirectory) emptyList() else root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isDirectory && it != root }
            .map { it.relativeTo(root).path.replace('\\', '/') }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()
        dirCache[key] = DirCache(now, dirs)
        return dirs
    }

    /**
     * Where Save As starts for [section] of [kind]: the folder of [loadedFrom] (the file the deck, chain or slot was loaded from) so a
     * variation lands next to its original; else the folder selected in the Library tree; else the section's top level.
     */
    fun saveDirFor(kind: BrowseKind, section: BrowseSection, loadedFrom: File?): File {
        val root = rootOf(kind, section) ?: return FileSystemManager.getPresetsRoot()
        loadedFrom?.absoluteFile?.parentFile?.takeIf { it.isDirectory && isInside(it, root) }?.let { return it }
        val selected = BrowserPane.scopeOf(kind) as? BrowseScope.Folder
        if (selected != null && selected.section == section) dirOf(root, selected.folder).takeIf { it.isDirectory }?.let { return it }
        return root
    }

    /** Why [name] can't be a folder or file name, or null when it can. */
    fun nameProblem(name: String): String? = when {
        name.isBlank() -> "Enter a name."
        name.startsWith(".") -> "Names can't start with a dot."
        name.any { it in "/\\:*?\"<>|" } -> "Names can't contain / \\ : * ? \" < > |"
        else -> null
    }

    /** True when [file] is [dir] or lies below it, with `..` and links resolved. */
    fun isInside(file: File, dir: File): Boolean = file.canonicalFile.toPath().startsWith(dir.canonicalFile.toPath())

    /** Makes folder [name] inside [parentFolder] of [root]. */
    fun createFolder(root: File, parentFolder: String, name: String): Result<File> {
        nameProblem(name)?.let { return Result.failure(IllegalArgumentException(it)) }
        val dir = File(dirOf(root, parentFolder), name.trim())
        if (!isInside(dir, root)) return Result.failure(IllegalArgumentException("Outside the library"))
        if (dir.exists()) return Result.failure(IllegalArgumentException("'${dir.name}' already exists"))
        if (!dir.mkdirs()) return Result.failure(IllegalStateException("Could not create '${dir.name}'"))
        invalidate()
        return Result.success(dir)
    }

    /** Saved files (any of the Library's preset extensions) under [dir], at any depth. */
    fun savedFilesUnder(dir: File): List<File> =
        if (!dir.isDirectory) emptyList()
        else dir.walkTopDown().filter { it.isFile && LibraryRefs.kindOf(it) != null }.toList()

    /** Renames [folder] of [root] to [newName] in place; the result maps each saved file inside from its old path to its new one. */
    fun renameFolder(root: File, folder: String, newName: String): Result<Map<File, File>> {
        nameProblem(newName)?.let { return Result.failure(IllegalArgumentException(it)) }
        val dir = dirOf(root, folder)
        return moveDir(root, dir, File(dir.parentFile, newName.trim()))
    }

    /** Moves [folder] of [root] into [destFolder] ("" for the top level), keeping its name. */
    fun moveFolder(root: File, folder: String, destFolder: String): Result<Map<File, File>> {
        val dir = dirOf(root, folder)
        val destParent = dirOf(root, destFolder)
        if (isInside(destParent, dir)) return Result.failure(IllegalArgumentException("A folder can't go inside itself"))
        return moveDir(root, dir, File(destParent, dir.name))
    }

    private fun moveDir(root: File, from: File, to: File): Result<Map<File, File>> {
        if (from == root || !from.isDirectory || !isInside(from, root)) return Result.failure(IllegalArgumentException("Not a folder of this section"))
        if (!isInside(to, root)) return Result.failure(IllegalArgumentException("Outside the library"))
        if (to.exists()) return Result.failure(IllegalArgumentException("'${to.name}' already exists there"))
        val before = savedFilesUnder(from)
        return try {
            to.parentFile?.mkdirs()
            Files.move(from.toPath(), to.toPath())
            invalidate()
            FileSystemManager.clearScanCache()
            Result.success(before.associateWith { File(to, it.relativeTo(from).path) })
        } catch (e: Exception) {
            logger.error(e) { "Could not move folder ${from.path} to ${to.path}" }
            Result.failure(e)
        }
    }

    /** A planned file move: where each file would go, and which of them already have a file of that name at the destination. */
    data class MovePlan(val moves: Map<File, File>, val conflicts: List<File>)

    /** Plans moving [files] into [destDir]. Files already there are left out. With [keepBoth], a name clash gets a `_2` (`_3`...) suffix instead. */
    fun planMove(files: List<File>, destDir: File, keepBoth: Boolean): MovePlan {
        val moves = LinkedHashMap<File, File>()
        val conflicts = mutableListOf<File>()
        val taken = HashSet<String>()
        for (file in files.distinct()) {
            if (file.absoluteFile.parentFile == destDir.absoluteFile) continue
            var target = File(destDir, file.name)
            if (target.exists() || target.name in taken) {
                if (!keepBoth) { conflicts += file; continue }
                target = uniqueTarget(destDir, file, taken)
            }
            taken += target.name
            moves[file] = target
        }
        return MovePlan(moves, conflicts)
    }

    private fun uniqueTarget(destDir: File, file: File, taken: Set<String>): File {
        var n = 2
        while (true) {
            val candidate = File(destDir, "${file.nameWithoutExtension}_$n.${file.extension}")
            if (!candidate.exists() && candidate.name !in taken) return candidate
            n++
        }
    }

    /** Carries out [moves] inside [root]; returns the ones that happened. */
    fun moveFiles(root: File, moves: Map<File, File>): Map<File, File> {
        val done = LinkedHashMap<File, File>()
        for ((from, to) in moves) {
            if (!isInside(from, root) || !isInside(to, root) || to.exists()) continue
            try {
                to.parentFile?.mkdirs()
                Files.move(from.toPath(), to.toPath())
                done[from] = to
            } catch (e: Exception) {
                logger.error(e) { "Could not move ${from.path} to ${to.path}" }
            }
        }
        if (done.isNotEmpty()) FileSystemManager.clearScanCache()
        return done
    }

    /** Deletes [folder] of [root] with everything in it (to the system trash when there is one); returns the saved files that went. */
    fun deleteFolder(root: File, folder: String): Result<List<File>> {
        val dir = dirOf(root, folder)
        if (folder.isEmpty() || !dir.isDirectory || !isInside(dir, root)) return Result.failure(IllegalArgumentException("Not a folder of this section"))
        val files = savedFilesUnder(dir)
        if (!FileSystemManager.moveToTrash(dir)) dir.deleteRecursively()
        invalidate()
        FileSystemManager.clearScanCache()
        // A partial failure still reports the files that did go, so their references are dropped.
        val gone = files.filter { !it.exists() }
        return if (dir.exists() && gone.isEmpty()) Result.failure(IllegalStateException("Could not delete '${dir.name}'")) else Result.success(gone)
    }
}
