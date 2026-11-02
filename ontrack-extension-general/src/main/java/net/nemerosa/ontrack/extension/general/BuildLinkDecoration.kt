package net.nemerosa.ontrack.extension.general

/**
 * Counts of the links of a build. The links themselves are loaded by the UI, when the user
 * hovers the decoration.
 *
 * `linksCount` and `upstreamCount` shipped in 5.5.11. A key inside a JSON value cannot trigger the
 * `DeprecationInstrumentation`, so they get no runtime warning - an exception to ADR 0018.
 *
 * @property buildId ID of the decorated build
 * @property dependencyCount Number of links to the builds this one uses
 * @property consumerCount Number of links from the builds using this one
 * @property maxLinks Maximum number of links the UI lists, on each side
 */
class BuildLinkDecorationList(
    val buildId: Int,
    val dependencyCount: Int,
    val consumerCount: Int,
    val maxLinks: Int,
) {

    @Deprecated("Removed in V7. Use dependencyCount instead. See #2130")
    val linksCount: Int get() = dependencyCount

    @Deprecated("Removed in V7. Use consumerCount instead. See #2130")
    val upstreamCount: Int get() = consumerCount
}
