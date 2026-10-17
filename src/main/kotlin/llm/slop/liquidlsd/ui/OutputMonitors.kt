package llm.slop.liquidlsd.ui

import org.lwjgl.glfw.GLFW.*

/**
 * The monitors GLFW reports, and which one the secondary output window should fill.
 *
 * Auto means "the first monitor that is not GLFW's primary". Wayland has no real primary monitor
 * (GLFW returns the first one the compositor announced), so Auto can pick the wrong screen there;
 * the user can then choose a monitor by name ([UITheme.outputMonitorName]).
 */
object OutputMonitors {

    data class Entry(val handle: Long, val name: String, val width: Int, val height: Int, val isPrimary: Boolean)

    fun list(): List<Entry> {
        val monitors = glfwGetMonitors() ?: return emptyList()
        val primary = glfwGetPrimaryMonitor()
        return (0 until monitors.limit()).map { i ->
            val handle = monitors.get(i)
            val mode = glfwGetVideoMode(handle)
            Entry(handle, glfwGetMonitorName(handle) ?: "Monitor ${i + 1}", mode?.width() ?: 0, mode?.height() ?: 0, handle == primary)
        }
    }

    /** The monitor for [preferredName] if it is connected, otherwise the first non-primary one, otherwise null. */
    fun resolve(preferredName: String): Entry? {
        val all = list()
        if (preferredName.isNotBlank()) all.firstOrNull { it.name == preferredName }?.let { return it }
        return all.firstOrNull { !it.isPrimary }
    }
}
