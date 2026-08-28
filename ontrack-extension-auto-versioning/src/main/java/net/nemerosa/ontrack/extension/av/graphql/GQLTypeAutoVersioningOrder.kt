package net.nemerosa.ontrack.extension.av.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoVersioningOrder(
    private val gqlEnumAutoApprovalMode: GQLEnumAutoApprovalMode,
    private val scmDetector: SCMDetector,
) : GQLType {

    override fun getTypeName(): String = AutoVersioningOrder::class.java.simpleName

    override fun createType(cache: GQLTypeCache?): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Auto versioning processing order")
            .stringField(AutoVersioningOrder::uuid, "UUID for the processing order")
            .stringField(AutoVersioningOrder::sourceProject, "Source project for the processing order")
            .stringField(AutoVersioningOrder::sourcePromotion, "Source promotion for the processing order")
            .stringField(AutoVersioningOrder::qualifier, "Qualifier for the link between the source and target")
            .field {
                it.name("branch")
                    .description("Target branch for the processing order")
                    .type(GraphQLTypeReference(GQLTypeBranch.BRANCH))
            }
            .stringField(AutoVersioningOrder::targetPath, "Target path(s) for the processing order")
            .field {
                it.name("targetPaths")
                    .description("Target path(s) for the processing order")
                    .deprecate("Deprecated, use `targetPath`")
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val order = env.getSource<AutoVersioningOrder>()!!
                        order.defaultPath.paths
                    }
            }
            .stringField(
                AutoVersioningOrder::targetRegex,
                "Regex used to identify the line to update in the target files"
            )
            .stringField(AutoVersioningOrder::targetProperty, "Property in the target file")
            .stringField(
                AutoVersioningOrder::targetPropertyRegex,
                "Regex used in property value to identify the version"
            )
            .stringField(AutoVersioningOrder::targetPropertyType, "Type of the target file")
            .stringField(AutoVersioningOrder::targetVersion, "Version to set")
            .booleanField(AutoVersioningOrder::autoApproval, "If the PR must be auto approved & merged")
            .stringField(AutoVersioningOrder::upgradeBranchPattern, "Pattern for the branch being used for the update")
            .stringField(AutoVersioningOrder::postProcessing, "Post processing type")
            .field {
                it.name("postProcessingConfig")
                    .description("JSON configuration for the post processing")
                    .type(GQLScalarJSON.INSTANCE)
            }
            .stringField(AutoVersioningOrder::versionRule, "Version rule type")
            .field {
                it.name("versionRuleConfig")
                    .description("JSON configuration for the version rule")
                    .type(GQLScalarJSON.INSTANCE)
            }
            .stringField(
                AutoVersioningOrder::validationStamp,
                "Ontrack validation stamp associated with this auto versioning (used for checks)"
            )
            .enumField(
                AutoVersioningOrder::autoApprovalMode,
                "Defines the way the PR is merged when auto approval is set."
            )
            .stringListField(
                AutoVersioningOrder::reviewers,
                "List of reviewers to always add to the pull request"
            )
            .field {
                it.name("repositoryHtmlURL")
                    .description("Link to the target repository")
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        val order: AutoVersioningOrder = env.getSource()!!
                        scmDetector.getSCM(order.branch.project)
                            ?.repositoryHtmlURL
                    }
            }
            .localDateTimeField(AutoVersioningOrder::schedule)
            .listField(AutoVersioningOrder::additionalPaths)
            .build()
}