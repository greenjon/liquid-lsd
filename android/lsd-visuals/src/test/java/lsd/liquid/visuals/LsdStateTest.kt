package lsd.liquid.visuals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LsdStateTest {
    @Test
    fun parsesAStateReport() {
        val s = LsdState.parse(
            """{"name":"web-beat-spiral","chain":"Bloom","background":"spaz-gallery","backgroundPicked":"spaz-gallery","channel":3,
              "channels":132,"skipped":false,"effects":true,"bgMode":"hold","hold":true,"holdBars":8,"autoRollBars":4,"follow":false,
              "lockedChain":null,"lockedTransition":"cosmic_singularity","live":false,"broadcaster":null,"bpm":124.5,"beatLocked":true,"paused":false}"""
        )!!
        assertEquals("web-beat-spiral", s.scene)
        assertEquals(3, s.channel)
        assertEquals("Bloom", s.chain)
        assertTrue(s.hold)
        assertEquals(8, s.holdBars)
        assertFalse(s.follow)
        assertNull(s.lockedChain)
        assertEquals("cosmic_singularity", s.lockedTransition)
        assertEquals(124.5, s.bpm, 0.0)
    }

    @Test
    fun missingFieldsTakeDefaultsAndJunkIsNull() {
        val s = LsdState.parse("{}")!!
        assertEquals(16, s.holdBars)
        assertTrue(s.effects)
        assertNull(LsdState.parse("not json"))
    }

    @Test
    fun parsesACatalog() {
        val c = LsdCatalog.parse(
            """{"scenes":[{"channel":1,"name":"a","tags":["hard"],"source":"kifs","pinnedChain":null,"skipped":false,"runnable":true}],
              "backgrounds":[{"index":0,"name":"spaz-gallery","source":"gallery","tags":[]}],
              "fxChains":[{"id":"bloom","name":"Bloom","inRotation":true}],"transitions":[{"id":"x","name":"X","inRotation":false}]}"""
        )!!
        assertEquals(listOf("hard"), c.scenes[0].tags)
        assertEquals("gallery", c.backgrounds[0].source)
        assertTrue(c.fxChains[0].inRotation)
        assertFalse(c.transitions[0].inRotation)
    }

    @Test
    fun commandsAreSafeJavaScript() {
        assertEquals("(function(){var L=window.LSD;if(L&&L.restore)L.restore(\"it\\u0027s \\\"x\\\"\");})()", LsdControl.script("restore", "it's \"x\""))
        assertEquals("(function(){var L=window.LSD;if(L&&L.chain)L.chain(null);})()", LsdControl.script("chain", null))
        assertEquals("(function(){var L=window.LSD;if(L&&L.setSkipped)L.setSkipped([\"a\",\"b\"]);})()", LsdControl.script("setSkipped", listOf("a", "b")))
        assertEquals("{\"a\":1}", LsdControl.unquote("\"{\\\"a\\\":1}\""))
        assertNull(LsdControl.unquote("null"))
    }
}
