package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.model.events.*

object AutoVersioningEvents {

    val AUTO_VERSIONING_SUCCESS: EventType = SimpleEventType(
        id = "auto-versioning-success",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has been done.
            
            ${MESSAGE}
            
            Pull request ${#.link?text=PR_NAME&href=PR_LINK}
        """.trimIndent(),
        description = "When an auto versioning request succeeds with the creation of a PR (merged or not).",
        context = eventContext(
            eventProject("Target project"),
            eventBranch("Target branch"),
            eventXPromotionRun("Source promotion run"),
            eventXBuild("Source build"),
            eventXBranch("Source branch"),
            eventXProject("Source project"),
            eventValue("VERSION", "Version being set"),
            eventValue("MESSAGE", "Auto versioning message"),
            eventValue("PR_NAME", "Title of the PR having been created"),
            eventValue("PR_LINK", "Link to the PR having been created"),
        ),
    )

    val AUTO_VERSIONING_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-error",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.
            
            ${MESSAGE}
            
            Error: ${ERROR}
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
        ),
    )

    val AUTO_VERSIONING_REJECTED: EventType = SimpleEventType(
        id = "auto-versioning-rejected",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has been rejected.

            ${MESSAGE}
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
        ),
    )

    val AUTO_VERSIONING_POST_PROCESSING_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-post-processing-error",
        template = $$"""
            Auto versioning post-processing of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.

            ${#.link?text=MESSAGE&href=LINK}
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
        ),
    )

    val AUTO_VERSIONING_PR_MERGE_TIMEOUT_ERROR: EventType = SimpleEventType(
        id = "auto-versioning-pr-merge-timeout-error",
        template = $$"""
            Auto versioning of ${project}/${branch} for dependency ${xProject} version "${VERSION}" has failed.
            
            Timeout while waiting for the PR to be ready to be merged.
            
            Pull request ${#.link?text=PR_NAME&href=PR_LINK}
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
        ),
    )

}