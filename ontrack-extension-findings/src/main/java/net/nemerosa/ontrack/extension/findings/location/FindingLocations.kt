package net.nemerosa.ontrack.extension.findings.location

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Location of a finding, as stored, and the version it carried, if any.
 *
 * @property location Location without any version
 * @property version Version extracted from the location, if any
 */
data class NormalisedLocation(
    val location: String,
    val version: String?,
)

/**
 * Normalisation of the locations of the findings, whatever the format of their report.
 */
object FindingLocations {

    private const val PURL_SCHEME = "pkg:"

    /**
     * Normalises a location.
     *
     * A purl (`pkg:type/namespace/name@version?qualifiers#subpath`) is stored without its version
     * and without its qualifiers — `pkg:maven/org.x/y`, not `pkg:maven/org.x/y@1.2.3` — and its
     * version is returned apart, for the observation. Bumping a package to a version which is still
     * vulnerable must not resolve the finding and open a new one: resolved means fixed, not bumped.
     *
     * Any other location is returned as it is.
     */
    fun normalise(location: String): NormalisedLocation {
        if (!location.startsWith(PURL_SCHEME, ignoreCase = true)) {
            return NormalisedLocation(location, null)
        }
        // Subpath, kept
        val subpathIndex = location.indexOf('#')
        val subpath = if (subpathIndex >= 0) location.substring(subpathIndex) else ""
        var rest = if (subpathIndex >= 0) location.substring(0, subpathIndex) else location
        // Qualifiers, dropped
        val qualifiersIndex = rest.indexOf('?')
        if (qualifiersIndex >= 0) {
            rest = rest.substring(0, qualifiersIndex)
        }
        // Version, after the name, which is the last segment. Looking for the `@` in the last
        // segment only keeps an npm scope written unencoded (`pkg:npm/@angular/core@1.0.0`).
        val nameIndex = rest.lastIndexOf('/') + 1
        val versionIndex = rest.indexOf('@', nameIndex)
        return if (versionIndex >= 0) {
            NormalisedLocation(
                location = rest.substring(0, versionIndex) + subpath,
                version = decode(rest.substring(versionIndex + 1)).takeIf { it.isNotEmpty() },
            )
        } else {
            NormalisedLocation(rest + subpath, null)
        }
    }

    /**
     * Percent-decoding of a purl component. `+` is a literal in a purl, not a space.
     */
    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            value
        }
}
