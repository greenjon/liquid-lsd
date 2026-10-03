package llm.slop.liquidlsd.control

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiMessageType
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerProfileTest {
    private fun emptyUserDir() = createTempDirectory("controllers").toFile()

    private fun twister(): CompiledController {
        val compiled = ControllerProfileStore(emptyUserDir()).get("midi-fighter-twister")
        assertNotNull(compiled, "built-in Twister profile should load")
        return compiled
    }

    private fun cc(channel: Int, index: Int, value: Int = 127) =
        MidiEvent(channel, MidiMessageType.CC, index, value, value / 127f)

    private fun compile(json: String) = Json.decodeFromString<ControllerProfile>(json).compile()

    // --- Built-in Twister profile, checked against values measured on the hardware ---

    @Test
    fun twisterProfileHasNoStructuralProblems() {
        assertEquals(emptyList(), twister().problems)
    }

    @Test
    fun twisterEncoderTurnsResolveToKnobAndBank() {
        val t = twister()
        assertEquals(ResolvedInput("knob.1", InputKind.ENCODER, 0, EncoderMode.RELATIVE_BINARY_OFFSET, 1f / 127f, 4f), t.resolve(cc(0, 0)))
        assertEquals(ResolvedInput("knob.16", InputKind.ENCODER, 0, EncoderMode.RELATIVE_BINARY_OFFSET, 1f / 127f, 4f), t.resolve(cc(0, 15)))
        assertEquals(ResolvedInput("knob.1", InputKind.ENCODER, 1, EncoderMode.RELATIVE_BINARY_OFFSET, 1f / 127f, 4f), t.resolve(cc(0, 16)))
        assertEquals(ResolvedInput("knob.16", InputKind.ENCODER, 3, EncoderMode.RELATIVE_BINARY_OFFSET, 1f / 127f, 4f), t.resolve(cc(0, 63)))
        assertNull(t.resolve(cc(0, 64)))
    }

    @Test
    fun twisterEncoderSwitchesUseChannelTwoWithSameNumbers() {
        val t = twister()
        assertEquals(ResolvedInput("knob.1.press", InputKind.BUTTON, 0), t.resolve(cc(1, 0)))
        assertEquals(ResolvedInput("knob.6.press", InputKind.BUTTON, 2), t.resolve(cc(1, 37)))
    }

    @Test
    fun twisterSideButtonsShiftFourPerBank() {
        val t = twister()
        // Physical side buttons are CC 8, 10, 11, 13 in bank 1, each further bank adds 4.
        // CC 10 is declared as the shift modifier; the other three are side.1..3.
        assertEquals("side.1", t.resolve(cc(3, 8))?.inputId)
        assertEquals(ResolvedInput("shift", InputKind.MODIFIER, 0), t.resolve(cc(3, 10))?.copy(step = 1f / 127f, accel = 1f))
        assertEquals("side.2", t.resolve(cc(3, 11))?.inputId)
        assertEquals("side.3", t.resolve(cc(3, 13))?.inputId)
        // Bank 2: 12, 14, 15, 17. Bank 4: 20, 22, 23, 25.
        assertEquals(1, t.resolve(cc(3, 12))?.bank)
        assertEquals("shift", t.resolve(cc(3, 14))?.inputId)
        assertEquals(1, t.resolve(cc(3, 14))?.bank)
        assertEquals(ResolvedInput("side.3", InputKind.BUTTON, 3).inputId, t.resolve(cc(3, 25))?.inputId)
        assertEquals(3, t.resolve(cc(3, 25))?.bank)
        // 9 belongs to nothing.
        assertNull(t.resolve(cc(3, 9)))
    }

    @Test
    fun twisterBankSwitchMessagesAnnounceTheNewBank() {
        val t = twister()
        // Switching bank 1 -> 2 sends (ch3, cc0, 0) then (ch3, cc1, 127); only the second enters a bank.
        assertNull(t.bankEntered(cc(3, 0, value = 0)))
        assertEquals(1, t.bankEntered(cc(3, 1, value = 127)))
        // 2 -> 3, then 3 -> 2.
        assertEquals(2, t.bankEntered(cc(3, 2)))
        assertEquals(1, t.bankEntered(cc(3, 1)))
        assertEquals(3, t.bankEntered(cc(3, 3)))
        assertNull(t.bankEntered(cc(0, 1)))      // an encoder turn is not a bank switch
        assertNull(t.bankEntered(cc(3, 8)))      // neither is a side button
    }

    @Test
    fun twisterMatchesLinuxAlsaPortNames() {
        val store = ControllerProfileStore(emptyUserDir())
        assertEquals("midi-fighter-twister", store.matchFor("Midi Fighter Twister [hw:2,0,0]")?.profile?.id)
        assertEquals("midi-fighter-twister", store.matchFor("TWISTER MIDI 1")?.profile?.id)
        assertNull(store.matchFor("Launchpad Mini"))
    }

    @Test
    fun twisterBindsEveryKnobAndRoutesShiftedSwitchesToTheAltCommand() {
        val t = twister()
        assertEquals("knob.3", t.bindingFor("knob.3"))
        assertEquals("knob.16", t.bindingFor("knob.16", bank = 2))
        assertEquals("knob.3.press", t.bindingFor("knob.3.press"))
        assertEquals("knob.3.press_alt", t.bindingFor("knob.3.press", held = listOf("shift")))
        // Shift has no turn binding of its own, so a shifted turn falls back to the plain one.
        assertEquals("knob.3", t.bindingFor("knob.3", held = listOf("shift")))
        assertEquals("nav.button.1", t.bindingFor("side.1"))
        assertEquals("nav.button.3.alt", t.bindingFor("side.3", held = listOf("shift")))
        assertNull(t.bindingFor("knob.17"))
    }

    @Test
    fun twisterBindingsAllNameRegisteredCommands() {
        val registry = CommandRegistry().also { GlobalCommands.registerAll(it); KnobCommands().register(it); NavCommands.register(it) }
        assertEquals(emptyList(), twister().unknownCommands(registry))
    }

    @Test
    fun twisterBanksNamePerformPages() {
        assertEquals(listOf("perform.ab", "perform.bgpv", "perform.mixer", "perform.master"), twister().profile.banks.pages)
        // Every named page must exist among the built-in pages.
        val known = llm.slop.liquidlsd.ui.PerfPageStore(java.io.File("does-not-exist")).all().map { "perform.${it.id}" }
        assertTrue(twister().profile.banks.pages.all { it in known })
    }

    // --- Binding resolution ---

    private val modifierProfile = """{"id":"x","banks":{"count":2},"inputs":[
        {"id":"shift","kind":"MODIFIER","channel":0,"cc":1},
        {"id":"alt","kind":"MODIFIER","channel":0,"cc":2},
        {"id":"b","kind":"BUTTON","channel":0,"cc":10,"count":2}],
        "bindings":{"b.*":"plain.{n}","shift+b.1":"shifted.1","alt+shift+b.1":"both.1"},
        "bankBindings":{"2":{"b.1":"bank2.1","shift+b.1":"bank2.shifted"}}}"""

    @Test
    fun mostSpecificBindingWins() {
        val c = compile(modifierProfile)
        assertEquals(emptyList(), c.problems)
        assertEquals("plain.1", c.bindingFor("b.1"))
        assertEquals("plain.2", c.bindingFor("b.2"))
        assertEquals("shifted.1", c.bindingFor("b.1", held = listOf("shift")))
        assertEquals("both.1", c.bindingFor("b.1", held = listOf("shift", "alt")))
        assertEquals("both.1", c.bindingFor("b.1", held = listOf("alt", "shift")))
        assertEquals("plain.2", c.bindingFor("b.2", held = listOf("shift", "alt")))
    }

    @Test
    fun bankBindingsOverrideGlobalOnesWithTheSameModifiers() {
        val c = compile(modifierProfile)
        assertEquals("plain.1", c.bindingFor("b.1", bank = 0))
        assertEquals("bank2.1", c.bindingFor("b.1", bank = 1))
        assertEquals("bank2.shifted", c.bindingFor("b.1", held = listOf("shift"), bank = 1))
        // A bank binding does not hide a more specific global one.
        assertEquals("both.1", c.bindingFor("b.1", held = listOf("shift", "alt"), bank = 1))
        assertEquals("plain.2", c.bindingFor("b.2", bank = 1))
    }

    @Test
    fun explicitBindingsOverrideWildcardOnes() {
        val c = compile("""{"id":"x","inputs":[{"id":"b","kind":"BUTTON","channel":0,"cc":0,"count":3}],
            "bindings":{"b.2":"special","b.*":"generic.{n}"}}""")
        assertEquals("generic.1", c.bindingFor("b.1"))
        assertEquals("special", c.bindingFor("b.2"))
        assertEquals("generic.3", c.bindingFor("b.3"))
    }

    @Test
    fun wildcardThatMatchesNothingIsReported() {
        val c = compile("""{"id":"x","inputs":[{"id":"b","kind":"BUTTON","channel":0,"cc":0}],
            "bindings":{"nothing.*":"x"}}""")
        assertTrue(c.problems.any { "matches no input" in it }, c.problems.toString())
    }

    @Test
    fun bankPagesCannotOutnumberBanks() {
        val c = compile("""{"id":"x","banks":{"count":1,"pages":["a","b"]}}""")
        assertTrue(c.problems.any { "banks.pages" in it }, c.problems.toString())
    }

    // --- Validation ---

    @Test
    fun overlappingInputsAreReported() {
        val c = compile("""{"id":"x","inputs":[
            {"id":"a","kind":"BUTTON","channel":0,"cc":5},
            {"id":"b","kind":"BUTTON","channel":0,"cc":5}]}""")
        assertTrue(c.problems.any { "both use" in it }, c.problems.toString())
    }

    @Test
    fun outOfRangeChannelAndCcAreReported() {
        val c = compile("""{"id":"x","banks":{"count":4},"inputs":[
            {"id":"a","kind":"ENCODER","channel":16,"cc":0},
            {"id":"b","kind":"ENCODER","channel":0,"cc":100,"count":4,"bankStride":16}]}""")
        assertTrue(c.problems.any { "channel 16" in it }, c.problems.toString())
        assertTrue(c.problems.any { "out of range 0..127" in it }, c.problems.toString())
    }

    @Test
    fun badIdsAndDuplicateInputsAreReported() {
        val c = compile("""{"id":"Bad Id","inputs":[
            {"id":"a+b","kind":"BUTTON","channel":0,"cc":1},
            {"id":"d","kind":"BUTTON","channel":0,"cc":2},
            {"id":"d","kind":"BUTTON","channel":0,"cc":3}]}""")
        assertTrue(c.problems.any { "Profile id" in it })
        assertTrue(c.problems.any { "no spaces, '+' or '*'" in it })
        assertTrue(c.problems.any { "Duplicate input id d" in it })
    }

    @Test
    fun bindingsMustNameRealInputsAndModifiers() {
        val c = compile("""{"id":"x","banks":{"count":2},
            "inputs":[
              {"id":"shift","kind":"MODIFIER","channel":0,"cc":1},
              {"id":"k","kind":"ENCODER","channel":0,"cc":10,"count":2,"press":{"channel":1}},
              {"id":"b","kind":"BUTTON","channel":0,"cc":2}],
            "bindings":{"k.1":"knob.1","shift+k.2.press":"x.y","ghost":"x","b+k.1":"x","shift+ghost":"x"},
            "bankBindings":{"2":{"b":"x"},"3":{"b":"x"}}}""")
        assertEquals(
            listOf("binding 'ghost' refers to unknown input 'ghost'",
                   "binding 'b+k.1': 'b' is not a MODIFIER input",
                   "binding 'shift+ghost' refers to unknown input 'ghost'",
                   "bankBindings key '3' is not a bank in 1..2"),
            c.problems)
    }

    @Test
    fun unknownCommandsAreListedAgainstARegistry() {
        val registry = CommandRegistry().also { GlobalCommands.registerAll(it) }
        val profile = Json.decodeFromString<ControllerProfile>("""{"id":"x","inputs":[
            {"id":"b","kind":"BUTTON","channel":0,"cc":1}],
            "bindings":{"b":"mixer.queue_next"},
            "bankBindings":{"1":{"b":"Global/tapTempo"}}}""")
        assertEquals(emptyList(), profile.compile().unknownCommands(registry))

        val bad = profile.copy(bindings = mapOf("b" to "does.not.exist"))
        assertEquals(listOf("does.not.exist"), bad.compile().unknownCommands(registry))
    }

    // --- Store ---

    @Test
    fun userProfileOverridesBuiltInWithSameId() {
        val dir = emptyUserDir()
        File(dir, "twister-copy.json").writeText(
            """{"id":"midi-fighter-twister","name":"My Twister","match":["Twister"],
               "inputs":[{"id":"only","kind":"BUTTON","channel":0,"cc":1}]}""")
        val store = ControllerProfileStore(dir)

        assertEquals(1, store.all().size)
        assertEquals("My Twister", store.get("midi-fighter-twister")?.profile?.name)
        assertEquals("only", store.get("midi-fighter-twister")?.resolve(cc(0, 1))?.inputId)
    }

    @Test
    fun userProfileIsPreferredWhenBothMatchADevice() {
        val dir = emptyUserDir()
        File(dir, "mine.json").writeText("""{"id":"my-twister","match":["Twister"]}""")
        val store = ControllerProfileStore(dir)
        assertEquals(2, store.all().size)
        assertEquals("my-twister", store.matchFor("Midi Fighter Twister")?.profile?.id)
    }

    @Test
    fun brokenUserProfilesAreSkippedWithoutAffectingOthers() {
        val dir = emptyUserDir()
        File(dir, "garbage.json").writeText("{ not json")
        File(dir, "invalid.json").writeText("""{"id":"Bad Id"}""")
        val store = ControllerProfileStore(dir)
        assertEquals(listOf("midi-fighter-twister"), store.all().map { it.profile.id })
    }

    @Test
    fun missingUserDirectoryIsFine() {
        val store = ControllerProfileStore(File(emptyUserDir(), "does-not-exist"))
        assertFalse(store.all().isEmpty())
    }
}
