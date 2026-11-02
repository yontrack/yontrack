package net.nemerosa.ontrack.extension.scm.changelog

import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.StructureService

abstract class AbstractChangeLogTemplatingService<C>(
    private val scmChangeLogService: SCMChangeLogService,
    private val structureService: StructureService,
) {

    fun render(
        fromBuild: Build,
        toBuild: Build,
        allQualifiers: Boolean,
        dependencies: List<String>,
        defaultQualifierFallback: Boolean,
        config: C,
        renderer: EventRenderer,
    ): String {
        return if (allQualifiers && dependencies.isNotEmpty()) {
            renderAllQualifiers(
                fromBuild = fromBuild,
                toBuild = toBuild,
                dependencies = dependencies,
                defaultQualifierFallback = defaultQualifierFallback,
                config = config,
                renderer = renderer
            )
        } else {
            // Single change log
            renderChangeLog(
                fromBuild = fromBuild,
                toBuild = toBuild,
                dependencies = dependencies,
                defaultQualifierFallback = defaultQualifierFallback,
                config = config,
                renderer = renderer
            )
        }
    }

    private fun renderAllQualifiers(
        fromBuild: Build,
        toBuild: Build,
        dependencies: List<String>,
        defaultQualifierFallback: Boolean,
        config: C,
        renderer: EventRenderer,
    ): String {
        // Gets the change log boundaries but for the last dependency
        val parsedDependencies = dependencies.map { DependencyLink.parse(it) }
        val firstDependencies = parsedDependencies.dropLast(1)
        val lastDependencyProject = parsedDependencies.last().project
        val (deepFrom, deepTo) = runBlocking {
            scmChangeLogService.getChangeLogBoundaries(
                from = fromBuild,
                to = toBuild,
                dependencies = firstDependencies,
                defaultQualifierFallback = defaultQualifierFallback,
            )
        } ?: return ""

        // Gets all links to the last dependency project and all the common qualifiers
        // Including the default qualifier
        val qualifiers = collectAllQualifiers(lastDependencyProject, deepFrom, deepTo)

        // For each qualifier (including the default one), get a change log
        val qualifiedChangeLogs = runBlocking {
            qualifiers.associateWith { qualifier ->
                scmChangeLogService.getChangeLog(
                    from = deepFrom,
                    to = deepTo,
                    dependencies = listOf(
                        DependencyLink(
                            project = lastDependencyProject,
                            qualifier = qualifier,
                        )
                    ),
                    defaultQualifierFallback = defaultQualifierFallback,
                )
            }
        }

        // Renders as a list
        return qualifiedChangeLogs.entries
            .filter { (_, changeLog) -> changeLog != null && !changeLog.isEmpty() }
            .joinToString(
                separator = renderer.renderSpace()
            ) { (qualifier, changeLog) ->
                check(changeLog != null) {
                    "At this stage, the change log cannot be null"
                }
                renderChangeLog(
                    changeLog = changeLog,
                    config = config,
                    suffix = if (qualifier.isBlank()) {
                        ""
                    } else {
                        " [$qualifier]"
                    },
                    renderer = renderer,
                )
            }
    }

    private fun collectAllQualifiers(projectName: String, vararg builds: Build): List<String> {
        val qualifiers = mutableSetOf<String>()
        for (build in builds) {
            val linkQualifiers = structureService.getBuildDependencyLinks(build, size = 100) {
                it.build.project.name == projectName
            }.pageItems.map { it.qualifier }.filter { it.isNotBlank() }.toSet()
            qualifiers += linkQualifiers
        }
        // Sorting the qualifiers
        val sortedQualifiers = qualifiers.sorted().toMutableList()
        // Always adding the default qualifier
        sortedQualifiers.add(0, "")
        // OK
        return sortedQualifiers.toList()
    }

    private fun renderChangeLog(
        fromBuild: Build,
        toBuild: Build,
        dependencies: List<String>,
        defaultQualifierFallback: Boolean,
        config: C,
        renderer: EventRenderer,
    ): String {
        // ... getting the change log, recursively or not
        val changeLog = runBlocking {
            scmChangeLogService.getChangeLog(
                fromBuild,
                toBuild,
                dependencies.map { DependencyLink.parse(it) },
                defaultQualifierFallback,
            )
        } ?: return ""
        return renderChangeLog(changeLog, config, null, renderer)
    }

    abstract fun renderChangeLog(
        changeLog: SCMChangeLog,
        config: C,
        suffix: String?,
        renderer: EventRenderer
    ): String

}