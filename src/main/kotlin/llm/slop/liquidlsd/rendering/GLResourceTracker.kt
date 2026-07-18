package llm.slop.liquidlsd.rendering

object GLResourceTracker {
    private val enabled = System.getProperty("liquidlsd.trackGLResources", "true").toBoolean()
    private val tracked = java.util.concurrent.ConcurrentHashMap<Int, String>() // id -> description

    fun register(id: Int, description: String) {
        if (enabled) tracked[id] = description
    }

    fun unregister(id: Int) {
        if (enabled) tracked.remove(id)
    }

    fun reportLeaks(): List<String> = tracked.values.toList()

    fun assertNoLeaks() {
        val leaks = reportLeaks()
        if (leaks.isNotEmpty()) {
            val msg = leaks.joinToString("\n")
            System.err.println("[GLResourceTracker] WARNING: ${leaks.size} unreleased GL resource(s):\n$msg")
        }
    }
}
