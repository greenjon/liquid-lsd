package llm.slop.liquidlsd.update

/**
 * Lightweight, zero-dependency Semantic Versioning parser and comparator.
 *
 * Adheres to SemVer 2.0.0 precedence rules:
 * - Compares major, minor, and patch numbers numerically.
 * - Releases without a pre-release tag have higher precedence than those with one (e.g. 1.0.0 > 1.0.0-beta.42).
 * - Pre-release tags are split by '.' and compared segment by segment:
 *   - Numeric segments are compared numerically (e.g. beta.42 > beta.41).
 *   - Non-numeric segments are compared ASCII lexically (e.g. rc.1 > beta.99).
 * - Strips optional leading 'v' / 'V' and trailing whitespace.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val raw: String
) : Comparable<SemVer> {

    val isPreRelease: Boolean
        get() = !preRelease.isNullOrBlank()

    val isSnapshot: Boolean
        get() = preRelease?.contains("SNAPSHOT", ignoreCase = true) == true

    override fun compareTo(other: SemVer): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)

        // SemVer 2.0.0 rule: A normal release has higher precedence than a pre-release
        if (this.preRelease == null && other.preRelease != null) return 1
        if (this.preRelease != null && other.preRelease == null) return -1
        if (this.preRelease == null && other.preRelease == null) return 0

        // Both have pre-release suffixes: compare dot-separated identifiers
        val thisSegments = this.preRelease!!.split(".")
        val otherSegments = other.preRelease!!.split(".")
        val maxLen = maxOf(thisSegments.size, otherSegments.size)

        for (i in 0 until maxLen) {
            val seg1 = thisSegments.getOrNull(i)
            val seg2 = otherSegments.getOrNull(i)

            if (seg1 == null) return -1 // Shorter pre-release identifier has lower precedence
            if (seg2 == null) return 1

            val num1 = seg1.toIntOrNull()
            val num2 = seg2.toIntOrNull()

            val cmp = when {
                num1 != null && num2 != null -> num1.compareTo(num2)
                num1 != null && num2 == null -> -1 // Numeric identifiers have lower precedence than non-numeric
                num1 == null && num2 != null -> 1
                else -> seg1.compareTo(seg2)
            }
            if (cmp != 0) return cmp
        }

        return 0
    }

    override fun toString(): String = raw

    companion object {
        /**
         * Parses a version string into a [SemVer] instance.
         * Tolerates missing patch numbers (e.g. "1.0"), leading 'v', and build metadata.
         */
        fun parseOrNull(versionStr: String?): SemVer? {
            if (versionStr.isNullOrBlank()) return null
            val clean = versionStr.trim().removePrefix("v").removePrefix("V")
            
            // Separate build metadata (+metadata)
            val withoutMetadata = clean.substringBefore('+')
            
            // Separate pre-release (-prerelease)
            val dashIdx = withoutMetadata.indexOf('-')
            val (corePart, prePart) = if (dashIdx >= 0) {
                withoutMetadata.substring(0, dashIdx) to withoutMetadata.substring(dashIdx + 1)
            } else {
                withoutMetadata to null
            }

            val numParts = corePart.split(".")
            val major = numParts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = numParts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = numParts.getOrNull(2)?.toIntOrNull() ?: 0

            return SemVer(
                major = major,
                minor = minor,
                patch = patch,
                preRelease = prePart?.takeIf { it.isNotBlank() },
                raw = versionStr.trim()
            )
        }

        fun parse(versionStr: String): SemVer {
            return parseOrNull(versionStr) ?: SemVer(0, 0, 0, preRelease = versionStr.trim(), raw = versionStr.trim())
        }
    }
}
