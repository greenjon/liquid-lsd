package llm.slop.liquidlsd.parameters

interface ParameterOwner {
    /**
     * Returns a list of all modulatable parameters owned by this object and its children,
     * prepended with the given string prefix to form a unique global path.
     */
    fun getParameterPaths(prefix: String): List<Pair<String, ModulatableParameter>>
}
