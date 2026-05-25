package net.nemerosa.ontrack.kdsl.spec.extension.av

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GetBranchAutoVersioningConfigQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SetAutoVersioningConfigMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoVersioningNotificationInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoVersioningSourceConfigInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoVersioningSourceConfigPathInput
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Branch

fun Branch.setAutoVersioningConfig(
    configurations: List<AutoVersioningSourceConfig>,
) {
    graphqlConnector.mutate(
        SetAutoVersioningConfigMutation(
            id.toInt(),
            configurations.map { config ->
                AutoVersioningSourceConfigInput(
                    autoApproval = Optional.presentIfNotNull(config.autoApproval),
                    autoApprovalMode = Optional.presentIfNotNull(
                        when (config.autoApprovalMode) {
                            AutoApprovalMode.CLIENT -> net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoApprovalMode.CLIENT
                            AutoApprovalMode.SCM -> net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoApprovalMode.SCM
                            null -> net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoApprovalMode.CLIENT
                        }
                    ),
                    postProcessing = Optional.presentIfNotNull(config.postProcessing),
                    postProcessingConfig = Optional.presentIfNotNull(config.postProcessingConfig),
                    sourceBranch = config.sourceBranch,
                    sourceProject = config.sourceProject,
                    sourcePromotion = config.sourcePromotion,
                    targetPath = config.targetPath,
                    targetProperty = Optional.presentIfNotNull(config.targetProperty),
                    targetPropertyRegex = Optional.presentIfNotNull(config.targetPropertyRegex),
                    targetPropertyType = Optional.presentIfNotNull(config.targetPropertyType),
                    targetRegex = Optional.presentIfNotNull(config.targetRegex),
                    upgradeBranchPattern = Optional.presentIfNotNull(config.upgradeBranchPattern),
                    validationStamp = Optional.presentIfNotNull(config.validationStamp),
                    backValidation = Optional.presentIfNotNull(config.backValidation),
                    versionSource = Optional.presentIfNotNull(config.versionSource),
                    buildLinkCreation = Optional.presentIfNotNull(config.buildLinkCreation),
                    reviewers = Optional.presentIfNotNull(config.reviewers),
                    notifications = Optional.presentIfNotNull(
                        config.notifications?.map { n ->
                            AutoVersioningNotificationInput(
                                channel = n.channel,
                                config = n.config,
                                scope = n.scope.map { s ->
                                    net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoVersioningNotificationScope.valueOf(
                                        s.name
                                    )
                                },
                                notificationTemplate = Optional.presentIfNotNull(n.notificationTemplate),
                            )
                        }
                    ),
                    prTitleTemplate = Optional.presentIfNotNull(config.prTitleTemplate),
                    prBodyTemplate = Optional.presentIfNotNull(config.prBodyTemplate),
                    prBodyTemplateFormat = Optional.presentIfNotNull(config.prBodyTemplateFormat),
                    additionalPaths = Optional.presentIfNotNull(
                        config.additionalPaths?.map { path ->
                            AutoVersioningSourceConfigPathInput(
                                path = path.path,
                                regex = Optional.presentIfNotNull(path.regex),
                                property = Optional.presentIfNotNull(path.property),
                                propertyRegex = Optional.presentIfNotNull(path.propertyRegex),
                                propertyType = Optional.presentIfNotNull(path.propertyType),
                                versionSource = Optional.presentIfNotNull(path.versionSource),
                            )
                        }
                    ),
                    cronSchedule = Optional.presentIfNotNull(config.cronSchedule),
                    pushMode = Optional.presentIfNotNull(
                        config.pushMode?.let {
                            net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoVersioningPushMode.valueOf(
                                it.name
                            )
                        }
                    ),
                )
            }
        )
    ) { it?.setAutoVersioningConfig?.payloadUserErrors?.convert() }
}

fun Branch.getAutoVersioningConfig(): List<AutoVersioningSourceConfig> =
    graphqlConnector.query(
        GetBranchAutoVersioningConfigQuery(id.toInt())
    )?.branches?.firstOrNull()?.autoVersioningConfig?.configurations?.map {
        it.autoVersioningSourceConfigFragment.toAutoVersioningSourceConfig()
    } ?: emptyList()
