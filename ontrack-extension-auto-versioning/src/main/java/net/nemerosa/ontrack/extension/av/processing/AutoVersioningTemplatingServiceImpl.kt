package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingService
import net.nemerosa.ontrack.extension.scm.changelog.ChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.extension.scm.changelog.SemanticChangeLogTemplatingService
import net.nemerosa.ontrack.extension.scm.changelog.SemanticChangeLogTemplatingServiceConfig
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.EventRendererRegistry
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.templating.*
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class AutoVersioningTemplatingServiceImpl(
    private val templatingService: TemplatingService,
    private val structureService: StructureService,
    private val buildDisplayNameService: BuildDisplayNameService,
    private val changeLogTemplatingService: ChangeLogTemplatingService,
    private val semanticChangeLogTemplatingService: SemanticChangeLogTemplatingService,
    private val eventRendererRegistry: EventRendererRegistry,
) : AutoVersioningTemplatingService {

    override fun createAutoVersioningTemplateRenderer(
        order: AutoVersioningOrder,
        currentVersions: Map<String, String>,
    ): AutoVersioningTemplateRenderer {

        val sourceProject: Project by lazy {
            structureService.findProjectByName(order.sourceProject)
                .getOrNull()
                ?: throw ProjectNotFoundException(order.sourceProject)
        }

        val sourceBuild: Build? by lazy {
            order.sourceBuildId?.let {
                structureService.getBuild(ID.of(it))
            }
        }

        val sourcePromotionRun: PromotionRun? by lazy {
            order.sourcePromotionRunId?.let {
                structureService.getPromotionRun(ID.of(it))
            }
        }

        /**
         * When adding new entries, please also update the documentation at
         *
         * ontrack-docs/src/docs/asciidoc/templating/contexts/auto-versioning-context.adoc
         */

        val context: Map<String, Any> by lazy {
            val tmp = mutableMapOf(
                "sourceProject" to sourceProject,
                "targetBranch" to order.branch,
                "PROMOTION" to (order.sourcePromotion ?: ""),
                "PATH" to order.defaultPath.paths.first(),
                "PATHS" to order.allPaths.flatMap { it.paths }.joinToString(","),
                "PROPERTY" to (order.targetProperty ?: ""),
                "VERSION" to order.targetVersion,
                "av" to AutoVersioningOrderTemplatingRenderable(order, currentVersions, sourceProject),
            )
            if (sourceBuild != null) {
                tmp["sourceBuild"] = sourceBuild!!
            }
            if (sourcePromotionRun != null) {
                tmp["sourcePromotionRun"] = sourcePromotionRun!!
            }
            tmp
        }

        return object : AutoVersioningTemplateRenderer {
            override fun render(template: String, renderer: EventRenderer): String {
                return templatingService.render(
                    template = template,
                    context = context,
                    renderer = renderer,
                )
            }
        }

    }

    override fun generatePRInfo(
        order: AutoVersioningOrder,
        avRenderer: AutoVersioningTemplateRenderer,
    ): AutoVersioningPRInfo {

        val title = if (order.prTitleTemplate.isNullOrBlank()) {
            order.getCommitMessage()
        } else {
            avRenderer.render(
                template = order.prTitleTemplate,
                renderer = PlainEventRenderer.INSTANCE,
            )
        }

        val renderer = order.prBodyTemplateFormat?.let {
            eventRendererRegistry.findEventRendererById(it)
        } ?: PlainEventRenderer.INSTANCE

        val body = if (order.prBodyTemplate.isNullOrBlank()) {
            order.getCommitMessage()
        } else {
            avRenderer.render(
                template = order.prBodyTemplate,
                renderer = renderer,
            )
        }
        return AutoVersioningPRInfo(title, body)
    }

    private inner class AutoVersioningOrderTemplatingRenderable(
        private val order: AutoVersioningOrder,
        private val currentVersions: Map<String, String>,
        private val sourceProject: Project,
    ) : TemplatingRenderable {
        override fun render(field: String?, config: TemplatingSourceConfig, renderer: EventRenderer): String =
            when (field) {
                "changelog" -> renderChangeLog(order, currentVersions, sourceProject, config, renderer)
                "semanticChangelog" -> renderSemanticChangeLog(order, currentVersions, sourceProject, config, renderer)
                null -> throw TemplatingRenderableFieldRequiredException()
                else -> throw TemplatingRenderableFieldNotFoundException(field)
            }

    }

    private fun renderChangeLog(
        order: AutoVersioningOrder,
        currentVersions: Map<String, String>,
        sourceProject: Project,
        config: TemplatingSourceConfig,
        renderer: EventRenderer
    ): String {
        val empty = ChangeLogTemplatingServiceConfig.emptyValue(config)
        val (fromBuild, toBuild) = getChangeLogBoundaries(order, currentVersions, sourceProject)
            ?: return empty
        return changeLogTemplatingService.render(
            fromBuild = fromBuild,
            toBuild = toBuild,
            config = config.parse(),
            renderer = renderer,
        )
    }

    private fun renderSemanticChangeLog(
        order: AutoVersioningOrder,
        currentVersions: Map<String, String>,
        sourceProject: Project,
        config: TemplatingSourceConfig,
        renderer: EventRenderer
    ): String {
        val empty = ChangeLogTemplatingServiceConfig.emptyValue(config)
        val (fromBuild, toBuild) = getChangeLogBoundaries(order, currentVersions, sourceProject)
            ?: return empty
        return semanticChangeLogTemplatingService.render(
            fromBuild = fromBuild,
            toBuild = toBuild,
            config = config.parse<SemanticChangeLogTemplatingServiceConfig>(),
            renderer = renderer,
        )
    }

    /**
     * Boundaries of the change log for an auto-versioning order: from the build matching the version
     * currently set in the target file, to the build the auto-versioning is upgrading to.
     *
     * Returns null when either boundary cannot be resolved.
     */
    private fun getChangeLogBoundaries(
        order: AutoVersioningOrder,
        currentVersions: Map<String, String>,
        sourceProject: Project,
    ): Pair<Build, Build>? {
        val sourceBuildId = order.sourceBuildId ?: return null
        // Only the first path is taken into account by the change log
        val targetPath = order.defaultPath.paths.first()
        val currentVersion = currentVersions[targetPath] ?: return null
        // The "to" build of the change log is the build being deployed or promoted
        val toBuild = structureService.getBuild(ID.of(sourceBuildId))
        // For the "from" build, we look for this build in the source project with the
        // current version as display name or name
        val fromBuild = getCurrentBuild(currentVersion, sourceProject) ?: return null
        return fromBuild to toBuild
    }

    private fun getCurrentBuild(currentVersion: String, sourceProject: Project): Build? =
        buildDisplayNameService.findBuildByDisplayName(
            project = sourceProject,
            name = currentVersion,
            onlyDisplayName = false,
        )

}