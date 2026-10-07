package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.*
import org.springframework.stereotype.Component

@Component
class CoreAuthorizationContributor : AuthorizationContributor {

    companion object {
        const val GLOBAL = "global"
        const val ACCOUNTS = "accounts"
        const val ACCOUNT_GROUPS = "account_groups"
        const val PROJECT = "project"
        const val BRANCH = "branch"
        const val PROMOTION_LEVEL = "promotion_level"
        const val VALIDATION_STAMP = "validation_stamp"
        const val BUILD = "build"
        const val EVENTS = "events"
        const val AUDIT = "audit"
    }

    override fun appliesTo(context: Any): Boolean = context is GlobalAuthorizationContext

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        return listOf(
            // Global settings
            Authorization(
                GLOBAL,
                Authorization.SETTINGS,
                user.isGranted(GlobalSettings::class.java)
            ),
            // Account management
            Authorization(
                name = ACCOUNTS,
                action = Authorization.CONFIG,
                authorized = user.isGranted(AccountManagement::class.java)
            ),
            Authorization(
                name = ACCOUNT_GROUPS,
                action = Authorization.CONFIG,
                authorized = user.isGranted(AccountGroupManagement::class.java)
            ),
            // Project
            Authorization(
                PROJECT,
                Authorization.CREATE,
                user.isGranted(ProjectCreation::class.java)
            ),
            // Promotion levels
            Authorization(
                PROMOTION_LEVEL,
                "bulkUpdate",
                user.isGranted(GlobalSettings::class.java)
            ),
            // Promotion levels
            Authorization(
                VALIDATION_STAMP,
                "bulkUpdate",
                user.isGranted(GlobalSettings::class.java)
            ),
            // Audit of all the events
            Authorization(
                name = EVENTS,
                action = AUDIT,
                authorized = user.isGranted(EventsAudit::class.java)
            ),
        )
    }

}