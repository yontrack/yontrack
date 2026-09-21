package net.nemerosa.ontrack.extension.scm.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scm.changelog.*
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeLinkChange
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.events.EventRendererRegistry
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.LinkChangeService
import org.springframework.stereotype.Component

@Component
class GQLTypeSCMChangeLog(
    private val gqlTypeSCMDecoratedCommit: GQLTypeSCMDecoratedCommit,
    private val gqlTypeSCMChangeLogIssues: GQLTypeSCMChangeLogIssues,
    private val gqlTypeLinkChange: GQLTypeLinkChange,
    private val linkChangeService: LinkChangeService,
    private val scmDetector: SCMDetector,
    private val scmChangeLogExportService: SCMChangeLogExportService,
    private val eventRendererRegistry: EventRendererRegistry,
    private val gqlInputChangeLogTemplatingServiceConfig: GQLInputChangeLogTemplatingServiceConfig,
    private val gqlInputSemanticChangeLogTemplatingServiceConfig: GQLInputSemanticChangeLogTemplatingServiceConfig,
    private val changeLogTemplatingService: ChangeLogTemplatingService,
    private val semanticChangeLogTemplatingService: SemanticChangeLogTemplatingService,
) : GQLType {

    override fun getTypeName(): String = SCMChangeLog::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(SCMChangeLog::class))
            .field(SCMChangeLog::from)
            .field(SCMChangeLog::to)
            .field {
                it.name(SCMChangeLog::commits.name)
                    .description(getPropertyDescription(SCMChangeLog::commits))
                    .type(listType(gqlTypeSCMDecoratedCommit.typeRef))
            }
            .field(
                SCMChangeLog::issues,
                gqlTypeSCMChangeLogIssues
            )

            .field {
                it.name("diffLink")
                    .description("URL to get the file diff between the two builds")
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val changeLog = env.getSource<SCMChangeLog>()!!
                        val project = changeLog.from.project
                        scmDetector.getSCM(project)?.getDiffLink(changeLog.fromCommit, changeLog.toCommit)
                    }
            }

            .field {
                it.name("linkChanges")
                    .description("All dependency changes")
                    .type(listType(gqlTypeLinkChange.typeRef))
                    .dataFetcher { env ->
                        val changeLog = env.getSource<SCMChangeLog>()!!
                        linkChangeService.linkChanges(
                            changeLog.from,
                            changeLog.to,
                        )
                    }
            }

            .field {
                it.name("export")
                    .description("Exporting the issues of a change log. You may want to use the `render` field instead, which has more advanced options.")
                    .argument { arg ->
                        arg.name("request")
                            .description("How to generate the exported change log")
                            .type(GraphQLTypeReference("SCMChangeLogExportInput")) // Defined in templating.graphqls
                    }
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val changeLog = env.getSource<SCMChangeLog>()
                        val input = parseOptionalArgument<SCMChangeLogExportInput>("request", env)
                        scmChangeLogExportService.export(
                            changeLog = changeLog,
                            input = input,
                        )
                    }
            }

            .field {
                it.name("render")
                    .description("Rendering the change log.")
                    .argument(
                        stringArgument(
                            name = RENDER_ARG_RENDERER,
                            description = "Renderer to use for the change log rendering",
                        )
                    )
                    .argument { arg ->
                        arg.name(RENDER_ARG_CONFIG)
                            .description("Configuration for the rendering")
                            .type(gqlInputChangeLogTemplatingServiceConfig.typeRef)
                    }
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val changeLog = env.getSource<SCMChangeLog>()!!
                        val renderer = env.getArgument<String>(RENDER_ARG_RENDERER)
                            ?.let { eventRendererRegistry.findEventRendererById(it) }
                            ?: PlainEventRenderer.INSTANCE
                        val config = parseOptionalArgument<ChangeLogTemplatingServiceConfig>(RENDER_ARG_CONFIG, env)
                            ?: ChangeLogTemplatingServiceConfig()
                        changeLogTemplatingService.render(
                            fromBuild = changeLog.from,
                            toBuild = changeLog.to,
                            renderer = renderer,
                            config = config,
                        )
                    }
            }

            .field {
                it.name("semantic")
                    .description("Semantic change log")
                    .argument(
                        stringArgument(
                            name = RENDER_ARG_RENDERER,
                            description = "Renderer to use for the change log rendering",
                        )
                    )
                    .argument { arg ->
                        arg.name(RENDER_ARG_CONFIG)
                            .description("Configuration for the rendering")
                            .type(gqlInputSemanticChangeLogTemplatingServiceConfig.typeRef)
                    }
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val changeLog = env.getSource<SCMChangeLog>()!!
                        val renderer = env.getArgument<String>(RENDER_ARG_RENDERER)
                            ?.let { eventRendererRegistry.findEventRendererById(it) }
                            ?: PlainEventRenderer.INSTANCE
                        val config =
                            parseOptionalArgument<SemanticChangeLogTemplatingServiceConfig>(RENDER_ARG_CONFIG, env)
                                ?: SemanticChangeLogTemplatingServiceConfig()
                        semanticChangeLogTemplatingService.render(
                            fromBuild = changeLog.from,
                            toBuild = changeLog.to,
                            renderer = renderer,
                            config = config,
                        )
                    }
            }

            .build()

    companion object {
        const val RENDER_ARG_RENDERER = "renderer"
        const val RENDER_ARG_CONFIG = "config"
    }

}