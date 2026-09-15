package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext

object ShaderLocationsPreferencesPanel {
    private var customFolderPathBuf: ImString? = null

    fun drawContent(session: SessionContext) {
        session.uiTheme.h2("ISF Shader Locations & Libraries")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Manage search paths for Interactive Shader Format (ISF) generators, filters, and transitions. Directories are scanned recursively and prioritized by origin.")
        ImGui.spacing()

        if (customFolderPathBuf == null) {
            customFolderPathBuf = ImString(256)
        }
        ImGui.setNextItemWidth(360f)
        ImGui.inputTextWithHint("##custom_folder_path", "Enter absolute path to folder...", customFolderPathBuf!!)
        ImGui.sameLine()
        if (ImGui.button("Add Folder##add_isf_dir")) {
            val pathStr = customFolderPathBuf!!.get().trim()
            if (pathStr.isNotBlank()) {
                val added = llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.addCustomDirectory(pathStr)
                if (added) {
                    customFolderPathBuf!!.set("")
                    // Async scan so the render thread is never stalled by disk I/O.
                    llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                        llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                    })
                }
            }
        }
        itemTooltip("Add an arbitrary local directory containing ISF shaders.")

        ImGui.sameLine()
        if (ImGui.button("Rescan Now##rescan_isf")) {
            // Async scan so the render thread is never stalled by disk I/O.
            llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
            })
        }
        itemTooltip("Force immediate re-scan of all enabled ISF directories.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val resolvedDirs = llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.getResolvedDirectories()

        val tableFlags = ImGuiTableFlags.Borders or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingFixedFit
        if (ImGui.beginTable("##isf_dirs_table", 5, tableFlags)) {
            ImGui.tableSetupColumn("En", ImGuiTableColumnFlags.WidthFixed, 30f)
            ImGui.tableSetupColumn("Path / Expanded Path", ImGuiTableColumnFlags.WidthStretch, 0.5f)
            ImGui.tableSetupColumn("Origin Badge", ImGuiTableColumnFlags.WidthFixed, 110f)
            ImGui.tableSetupColumn("Status", ImGuiTableColumnFlags.WidthFixed, 90f)
            ImGui.tableSetupColumn("Actions", ImGuiTableColumnFlags.WidthFixed, 90f)
            ImGui.tableHeadersRow()

            resolvedDirs.forEach { resolved ->
                ImGui.tableNextRow()

                // Col 0: Enabled checkbox
                ImGui.tableSetColumnIndex(0)
                val enabled = ImBoolean(resolved.config.isEnabled)
                if (ImGui.checkbox("##en_${resolved.config.path}", enabled)) {
                    llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.toggleDirectoryEnabled(resolved.config.path, enabled.get())
                    llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                        llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                        llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                    })
                }

                // Col 1: Path
                ImGui.tableSetColumnIndex(1)
                ImGui.text(resolved.config.path)
                if (resolved.config.path != resolved.expandedPath) {
                    session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                        ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "-> ${resolved.expandedPath}")
                    }
                }

                // Col 2: Badge
                ImGui.tableSetColumnIndex(2)
                val badgeText = when (resolved.config.type) {
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.BUILT_IN -> "Built-in"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.SYSTEM_STANDARD -> "System"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.USER_STANDARD -> "User"
                    llm.slop.liquidlsd.rendering.isf.DirectorySourceType.CUSTOM -> "Custom"
                }
                ImGui.text(badgeText)

                // Col 3: Status
                ImGui.tableSetColumnIndex(3)
                when (resolved.status) {
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.ACTIVE -> ImGui.textColored(0.2f, 0.8f, 0.2f, 1.0f, "Active")
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.MISSING -> ImGui.textColored(0.9f, 0.7f, 0.1f, 1.0f, "Missing")
                    llm.slop.liquidlsd.rendering.isf.DirectoryStatus.UNREADABLE -> ImGui.textColored(0.9f, 0.2f, 0.2f, 1.0f, "Unreadable")
                }

                // Col 4: Actions
                ImGui.tableSetColumnIndex(4)
                if (resolved.config.type == llm.slop.liquidlsd.rendering.isf.DirectorySourceType.BUILT_IN) {
                    ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f, "Protected")
                } else {
                    if (ImGui.button("Remove##${resolved.config.path}")) {
                        llm.slop.liquidlsd.rendering.isf.ISFDirectoryManager.removeDirectory(resolved.config.path)
                        llm.slop.liquidlsd.rendering.isf.ISFLibraryRegistry.scanLibraryAsync(onComplete = {
                            llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()
                            llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
                            llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
                        })
                    }
                }
            }
            ImGui.endTable()
        }
    }
}
