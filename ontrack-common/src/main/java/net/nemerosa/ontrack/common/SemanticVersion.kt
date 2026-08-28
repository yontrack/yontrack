package net.nemerosa.ontrack.common

/**
 * Representation of a [semantic version](https://semver.org), with full support for the
 * prerelease precedence rules.
 *
 * This is deliberately _not_ the [Version] class: the latter accepts any trailing text after the
 * numeric parts (so `1.2.3-rc1` parses as `1.2.3` and compares _equal_ to `1.2.3`) and maps
 * non-numeric segments to `-1`. Both behaviours are unacceptable when the comparison is used to
 * decide if a version change is an upgrade or a downgrade.
 *
 * The parsing is lenient in two ways compared to the semver.org grammar: a leading `v` or `V` is
 * accepted, and a missing minor and/or patch number defaults to `0`. Real-life version files carry
 * values such as `v1.2` and rejecting them would turn a cosmetic difference into a failure.
 *
 * @property major Major version number
 * @property minor Minor version number
 * @property patch Patch version number
 * @property prerelease Optional prerelease part (without the leading `-`)
 * @property build Optional build metadata (without the leading `+`), ignored for the precedence
 */
data class SemanticVersion(
    val major: Int,
    val minor: Int = 0,
    val patch: Int = 0,
    val prerelease: String? = null,
    val build: String? = null,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        // Numeric parts first
        val numeric = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (numeric != 0) return numeric
        // Build metadata is not taken into account for the precedence
        return comparePrereleases(prerelease, other.prerelease)
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        prerelease?.let { append("-$it") }
        build?.let { append("+$it") }
    }

    companion object {

        /**
         * `[v]major[.minor[.patch]][-prerelease][+build]`
         *
         * Numeric parts must not have a leading zero, as per the semver.org grammar.
         */
        private val PATTERN = ("^[vV]?(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?(?:\\.(0|[1-9]\\d*))?" +
                "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
                "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$").toRegex()

        /**
         * Parses a [value] into a [SemanticVersion].
         *
         * @param value Value to parse
         * @return Parsed version or `null` if the [value] is not a semantic version
         */
        fun parse(value: String?): SemanticVersion? {
            if (value.isNullOrBlank()) return null
            val m = PATTERN.matchEntire(value.trim()) ?: return null
            return SemanticVersion(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                patch = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                prerelease = m.groupValues[4].takeIf { it.isNotEmpty() },
                build = m.groupValues[5].takeIf { it.isNotEmpty() },
            )
        }

        /**
         * Compares two prerelease parts according to the semver.org precedence rules: a version
         * without a prerelease has a higher precedence than the same version with one, numeric
         * identifiers are compared numerically and have a lower precedence than alphanumeric ones,
         * and a larger set of identifiers wins when all the preceding ones are equal.
         */
        private fun comparePrereleases(a: String?, b: String?): Int = when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> compareIdentifiers(a.split("."), b.split("."))
        }

        private fun compareIdentifiers(a: List<String>, b: List<String>): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val cmp = compareIdentifier(a[i], b[i])
                if (cmp != 0) return cmp
            }
            return a.size.compareTo(b.size)
        }

        private fun compareIdentifier(a: String, b: String): Int {
            val na = a.toIntOrNull()
            val nb = b.toIntOrNull()
            return when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> a.compareTo(b)
            }
        }

    }
}
