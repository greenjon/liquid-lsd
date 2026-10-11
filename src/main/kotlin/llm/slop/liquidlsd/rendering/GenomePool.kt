package llm.slop.liquidlsd.rendering

/**
 * One kept offspring as the sampler sees it: its name, lineage tags and the base value of every source parameter.
 * [path] (absolute) and [modified] (file time) feed the pick weighting; tests may leave them at their defaults.
 */
class PoolMember(
    val name: String,
    val tags: List<String>,
    val values: Map<String, Float>,
    val path: String = "",
    val modified: Long = 0L
)

/** Weighted draws from a Garden pool: newer offspring and favourites come up more often, nothing is excluded. */
object GenomePool {
    /** How many times likelier a favourite is than an ordinary member of the same age. */
    const val FAVORITE_FACTOR = 3f
    /** The newest member is this many times likelier than the oldest (weights are linear in age rank). */
    const val NEWEST_FACTOR = 2f

    /** Weights in [pool] order: age rank maps linearly from 1 (oldest) to [NEWEST_FACTOR] (newest), times [FAVORITE_FACTOR] for favourites. */
    fun weights(pool: List<PoolMember>, favorites: Set<String>): FloatArray {
        val byAge = pool.indices.sortedBy { pool[it].modified }
        val w = FloatArray(pool.size)
        byAge.forEachIndexed { rank, i ->
            val recency = if (pool.size < 2) 1f else 1f + (NEWEST_FACTOR - 1f) * rank / (pool.size - 1)
            w[i] = recency * (if (pool[i].path.isNotEmpty() && pool[i].path in favorites) FAVORITE_FACTOR else 1f)
        }
        return w
    }

    /** Draws one member; [pool] must not be empty. */
    fun pick(pool: List<PoolMember>, favorites: Set<String>, random: kotlin.random.Random): PoolMember {
        val w = weights(pool, favorites)
        var r = random.nextFloat() * w.sum()
        for (i in pool.indices) { r -= w[i]; if (r < 0f) return pool[i] }
        return pool.last()
    }
}
