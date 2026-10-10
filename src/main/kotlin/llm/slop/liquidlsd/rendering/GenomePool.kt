package llm.slop.liquidlsd.rendering

/** One kept offspring as the sampler sees it: its name, lineage tags and the base value of every source parameter. */
class PoolMember(val name: String, val tags: List<String>, val values: Map<String, Float>)
