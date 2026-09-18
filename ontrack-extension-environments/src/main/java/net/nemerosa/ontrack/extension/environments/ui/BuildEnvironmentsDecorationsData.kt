package net.nemerosa.ontrack.extension.environments.ui

/**
 * One environment a build is deployed in, as the build's decoration carries it.
 *
 * Since #1794 the decoration is drawn as a journey chip rather than as a bare environment icon, and
 * a chip carries the environment's icon beside the state. [environmentOrder] and [environmentImage]
 * are there so that it can: they are exactly what `EnvironmentImage` needs to draw an icon from an
 * environment already in hand. Without them the renderer would have to ask the server for each
 * environment separately - one request per decoration per build, on a branch page listing fifty
 * builds.
 *
 * @property environmentId The environment's id
 * @property environmentName Its name
 * @property environmentOrder Its rank, which is also what colours a generated icon
 * @property environmentImage Whether it has an icon of its own to draw instead
 * @property slotId The slot, which is what the chip links to
 * @property qualifier The slot's qualifier, empty for the default one
 * @property pipelineId The deployment which put the build there
 */
data class BuildEnvironmentsDecorationsData(
    val environmentId: String,
    val environmentName: String,
    val environmentOrder: Int,
    val environmentImage: Boolean,
    val slotId: String,
    val qualifier: String,
    val pipelineId: String,
)
