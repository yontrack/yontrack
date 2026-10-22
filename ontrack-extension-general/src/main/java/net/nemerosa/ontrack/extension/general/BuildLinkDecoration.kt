package net.nemerosa.ontrack.extension.general

/**
 * Counts of the links of a build. The links themselves are loaded by the UI, when the user
 * hovers the decoration.
 *
 * @property buildId ID of the decorated build
 * @property linksCount Number of downstream links
 * @property upstreamCount Number of upstream links
 * @property maxLinks Maximum number of links the UI lists, in each direction
 */
class BuildLinkDecorationList(
    val buildId: Int,
    val linksCount: Int,
    val upstreamCount: Int,
    val maxLinks: Int,
)
