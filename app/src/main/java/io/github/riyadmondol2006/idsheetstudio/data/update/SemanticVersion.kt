package io.github.riyadmondol2006.idsheetstudio.data.update

/** Minimal semantic version (MAJOR.MINOR.PATCH[-prerelease]) used to compare release tags. */
data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1 // a release outranks its pre-releases
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    /** Matches the build's version-code scheme: MAJOR * 10000 + MINOR * 100 + PATCH. */
    val versionCode: Int get() = major * 10000 + minor * 100 + patch

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(text: String): SemanticVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            return SemanticVersion(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].ifEmpty { "0" }.toInt(),
                patch = m.groupValues[3].ifEmpty { "0" }.toInt(),
                preRelease = m.groupValues[4].ifEmpty { null },
            )
        }

        private fun comparePreRelease(a: String, b: String): Int {
            val pa = a.split('.')
            val pb = b.split('.')
            for (i in 0 until minOf(pa.size, pb.size)) {
                val na = pa[i].toIntOrNull()
                val nb = pb[i].toIntOrNull()
                val c = when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1
                    nb != null -> 1
                    else -> pa[i].compareTo(pb[i])
                }
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }
    }
}
