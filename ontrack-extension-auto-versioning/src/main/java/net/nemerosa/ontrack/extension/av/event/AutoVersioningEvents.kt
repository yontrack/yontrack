package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.model.events.*

object AutoVersioningEvents {

    private val auditValues = arrayOf(
        eventValue("AUDIT_NAME", "Text of the link to the auto versioning audit entry"),
        eventValue("AUDIT_LINK", "Link to the auto versioning audit entry"),
    )

    val AUTO_VERSIONING_SUCCESS: EventType = SimpleEventType(
        id = "auto-versioning-success",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has been done.
            
            ${MESSAGE}
            
            ${#.link?text=CHANGE_NAME&href=CHANGE_LINK}

            ${#.link?text=AUDIT_NAME&href=AUDIT_LINK}
        """.trimIndent(),
        description = "When an auto versioning request succeeds, either with a PR (merged or not) or with a direct push to the target branch.",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version being set"),
            eventValue("MESSAGE", "Auto versioning message"),
            eventValue("CHANGE_NAME", "Text of the link to the change the auto versioning made: its PR, or the commit it pushed"),
            eventValue("CHANGE_LINK", "Link to the change the auto versioning made: its PR, or the commit it pushed"),
            eventValue("PR_NAME", "Title of the PR having been created, only set when a PR was created"),
            eventValue("PR_LINK", "Link to the PR having been created, only set when a PR was created"),
            eventValue("COMMIT", "Commit having been pushed, only set on a direct push"),
            eventValue("COMMIT_LINK", "Link to the commit having been pushed, only set on a direct push"),
            *auditValues,
        ),
    )

    val AUTO_VERSIONING_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-error",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.
            
            ${MESSAGE}
            
            Error: ${ERROR}

            ${#.link?text=AUDIT_NAME&href=AUDIT_LINK}
        """.trimIndent(),
        description = "When an auto versioning request fails because of a general error.",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version being set"),
            eventValue("MESSAGE", "Auto versioning message"),
            eventValue("ERROR", "Error message"),
            *auditValues,
        ),
    )

    val AUTO_VERSIONING_REJECTED: EventType = SimpleEventType(
        id = "auto-versioning-rejected",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has been rejected.

            ${MESSAGE}

            ${#.link?text=AUDIT_NAME&href=AUDIT_LINK}
        """.trimIndent(),
        description = "When an auto versioning request is rejected by the version rule of its configuration, typically because the version to set would be older than the version already present in the target files.",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version having been rejected"),
            eventValue("MESSAGE", "Reason for the rejection"),
            *auditValues,
        ),
    )

    val AUTO_VERSIONING_POST_PROCESSING_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-post-processing-error",
        template = $$"""
            Auto versioning post-processing of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.

            ${#.link?text=MESSAGE&href=LINK}

            ${#.link?text=AUDIT_NAME&href=AUDIT_LINK}
        """.trimIndent(),
        description = "When an auto versioning request fails because of the post-processing.",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version being set"),
            eventValue("MESSAGE", "Auto versioning message"),
            eventValue("LINK", "Link to the post processing process"),
            *auditValues,
        ),
    )

    val AUTO_VERSIONING_PR_MERGE_TIMEOUT_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-pr-merge-timeout-error",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.
            
            Timeout while waiting for the PR to be ready to be merged.
            
            Pull request ${#.link?text=PR_NAME&href=PR_LINK}

            ${#.link?text=AUDIT_NAME&href=AUDIT_LINK}
        """.trimIndent(),
        description = "When an auto versioning request fails because the corresponding PR could not be merged in time.",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version being set"),
            eventValue("PR_NAME", "Title of the PR having been created"),
            eventValue("PR_LINK", "Link to the PR having been created"),
            *auditValues,
        ),
    )

}