package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandRegistryTest {
    private fun ctx() = CommandContext(mockk<Mixer>(relaxed = true))

    private fun registryWith(kind: CommandKind, onRun: (CommandInput) -> Unit): CommandRegistry {
        val registry = CommandRegistry()
        registry.register(Command("test.cmd", kind, "test", "test command") { input, _ -> onRun(input) })
        return registry
    }

    @Test
    fun triggerFiresOncePerPress() {
        var fired = 0
        val registry = registryWith(CommandKind.TRIGGER) { fired++ }
        val c = ctx()

        assertTrue(registry.execute("test.cmd", CommandInput.Press(true), c))
        assertFalse(registry.execute("test.cmd", CommandInput.Press(true), c))  // still held
        assertFalse(registry.execute("test.cmd", CommandInput.Press(false), c)) // release never fires
        assertTrue(registry.execute("test.cmd", CommandInput.Press(true), c))   // pressed again
        assertEquals(2, fired)
    }

    @Test
    fun momentaryHandlerSeesBothEdges() {
        val seen = ArrayList<Boolean>()
        val registry = registryWith(CommandKind.MOMENTARY) { seen += (it as CommandInput.Press).down }
        val c = ctx()

        registry.execute("test.cmd", CommandInput.Press(true), c)
        registry.execute("test.cmd", CommandInput.Press(false), c)
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun scalarAndRelativeCommandsReceiveTheirInput() {
        var scalar = -1f
        var delta = 0f
        val registry = CommandRegistry()
        registry.register(Command("a.scalar", CommandKind.SCALAR, "t", "") { i, _ -> scalar = (i as CommandInput.Value).value })
        registry.register(Command("a.rel", CommandKind.RELATIVE, "t", "") { i, _ -> delta += (i as CommandInput.Delta).steps })
        val c = ctx()

        registry.execute("a.scalar", CommandInput.Value(0.25f), c)
        registry.execute("a.rel", CommandInput.Delta(2f), c)
        registry.execute("a.rel", CommandInput.Delta(-0.5f), c)
        assertEquals(0.25f, scalar)
        assertEquals(1.5f, delta)
    }

    @Test
    fun mismatchedInputTypeIsRejected() {
        var fired = 0
        val registry = registryWith(CommandKind.SCALAR) { fired++ }
        assertFalse(registry.execute("test.cmd", CommandInput.Press(true), ctx()))
        assertEquals(0, fired)
    }

    @Test
    fun unknownIdReturnsFalse() {
        assertFalse(CommandRegistry().execute("nope", CommandInput.Press(true), ctx()))
    }

    @Test
    fun aliasResolvesToCommand() {
        var fired = 0
        val registry = registryWith(CommandKind.TRIGGER) { fired++ }
        registry.registerAlias("Legacy/path", "test.cmd")

        assertEquals("test.cmd", registry.resolveId("Legacy/path"))
        assertNotNull(registry.get("Legacy/path"))
        assertTrue(registry.execute("Legacy/path", CommandInput.Press(true), ctx()))
        assertEquals(1, fired)
        assertNull(registry.resolveId("Other/path"))
    }

    @Test
    fun duplicateIdAndDanglingAliasAreRejected() {
        val registry = registryWith(CommandKind.TRIGGER) {}
        assertFailsWith<IllegalArgumentException> {
            registry.register(Command("test.cmd", CommandKind.TRIGGER, "t", "") { _, _ -> })
        }
        assertFailsWith<IllegalArgumentException> { registry.registerAlias("x", "missing") }
    }

    @Test
    fun globalCommandsKeepEveryLegacyPath() {
        val registry = CommandRegistry().also { GlobalCommands.registerAll(it) }
        val legacy = listOf(
            "Global/queueNext" to GlobalCommands.QUEUE_NEXT,
            "Global/queuePrev" to GlobalCommands.QUEUE_PREV,
            "Global/bgQueueNext" to GlobalCommands.BG_QUEUE_NEXT,
            "Global/bgQueuePrev" to GlobalCommands.BG_QUEUE_PREV,
            "Global/transQueueNext" to GlobalCommands.TRANS_QUEUE_NEXT,
            "Global/transQueuePrev" to GlobalCommands.TRANS_QUEUE_PREV,
            "Global/tapTempo" to GlobalCommands.TAP_TEMPO,
            "Global/autoFade" to GlobalCommands.AUTO_CROSSFADE,
            "Global/snapDeckA" to GlobalCommands.SNAP_A,
            "Global/snapDeckB" to GlobalCommands.SNAP_B
        )
        for ((path, id) in legacy) assertEquals(id, registry.resolveId(path), path)
        assertEquals(legacy.size, registry.all().size)
    }

    @Test
    fun queueCommandsAccumulateDeltasOnTheContext() {
        val registry = CommandRegistry().also { GlobalCommands.registerAll(it) }
        val c = ctx()
        registry.execute(GlobalCommands.QUEUE_NEXT, CommandInput.Press(true), c)
        registry.execute(GlobalCommands.BG_QUEUE_PREV, CommandInput.Press(true), c)
        registry.execute(GlobalCommands.TRANS_QUEUE_NEXT, CommandInput.Press(true), c)
        assertEquals(1, c.queueDelta)
        assertEquals(-1, c.bgQueueDelta)
        assertEquals(1, c.transQueueDelta)
    }
}
