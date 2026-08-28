package net.nemerosa.ontrack.kdsl.spec.extension.av

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.AutoVersioningSourceConfigFragment

fun AutoVersioningSourceConfigFragment.toAutoVersioningSourceConfig() =
    AutoVersioningSourceConfig(
        sourceProject = sourceProject,
        sourceBranch = sourceBranch,
        sourcePromotion = sourcePromotion,
        targetPath = targetPath,
        targetRegex = targetRegex,
        targetProperty = targetProperty,
        targetPropertyRegex = targetPropertyRegex,
        targetPropertyType = targetPropertyType,
        autoApproval = autoApproval,
        upgradeBranchPattern = upgradeBranchPattern,
        postProcessing = postProcessing,
        postProcessingConfig = postProcessingConfig,
        versionRule = versionRule,
        versionRuleConfig = versionRuleConfig,
        validationStamp = validationStamp,
        autoApprovalMode = when (autoApprovalMode) {
            net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoApprovalMode.CLIENT -> AutoApprovalMode.CLIENT
            net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.AutoApprovalMode.SCM -> AutoApprovalMode.SCM
            else -> null
        },
        notifications = notifications?.map { n ->
            AutoVersioningNotification(
                channel = n.channel,
                config = n.config,
                scope = n.scope.map { s ->
                    AutoVersioningNotificationScope.valueOf(s.name)
                },
                notificationTemplate = n.notificationTemplate,
            )
        },
        prTitleTemplate = prTitleTemplate,
        prBodyTemplate = prBodyTemplate,
        prBodyTemplateFormat = prBodyTemplateFormat,
        additionalPaths = additionalPaths?.map {
            AutoVersioningSourceConfigPath(
                path = it.path,
                regex = it.regex,
                property = it.property,
                propertyRegex = it.propertyRegex,
                propertyType = it.propertyType,
                versionSource = it.versionSource,
            )
        }
    )
