package com.cydoniancitizen.bingee.data.update

data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parse(raw: String): SemanticVersion? {
            val match = VERSION_REGEX.find(raw) ?: return null
            val (maj, min, pat) = match.destructured
            val major = maj.toIntOrNull() ?: return null
            val minor = min.toIntOrNull() ?: return null
            val patch = pat.toIntOrNull() ?: return null
            return SemanticVersion(major, minor, patch)
        }
    }
}
