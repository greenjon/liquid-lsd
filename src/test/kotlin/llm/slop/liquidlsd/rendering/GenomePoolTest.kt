package llm.slop.liquidlsd.rendering

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenomePoolTest {
    private fun member(name: String, modified: Long) = PoolMember(name, emptyList(), emptyMap(), "/g/$name.lsd", modified)

    @Test
    fun newerMembersWeighMoreAndFavouritesTriple() {
        val pool = listOf(member("old", 1), member("mid", 2), member("new", 3))
        val w = GenomePool.weights(pool, emptySet())
        assertEquals(1f, w[0]); assertEquals(1.5f, w[1]); assertEquals(2f, w[2])
        val f = GenomePool.weights(pool, setOf("/g/old.lsd"))
        assertEquals(3f, f[0]); assertEquals(1.5f, f[1])
    }

    @Test
    fun singleMemberAndUnknownPathsAreNeutral() {
        assertEquals(1f, GenomePool.weights(listOf(member("a", 5)), emptySet())[0])
        assertEquals(1f, GenomePool.weights(listOf(PoolMember("t", emptyList(), emptyMap())), setOf("")).single())
    }

    @Test
    fun pickFollowsTheWeightsButNeverExcludes() {
        val pool = listOf(member("old", 1), member("new", 2))
        val rnd = Random(7)
        val counts = HashMap<String, Int>()
        repeat(6000) { counts.merge(GenomePool.pick(pool, setOf("/g/old.lsd"), rnd).name, 1, Int::plus) }
        // old: 1 x 3 = 3, new: 2 -> 60% / 40%
        assertTrue(counts.getValue("old") in 3300..3900, counts.toString())
        assertTrue(counts.getValue("new") > 2000)
    }
}
