package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

/**
 * Metrics for the auto promotion.
 *
 * Only the `project` and `promotionLevel` tags are used. A `branch` or a `build` tag would make the
 * cardinality unbounded - feature branches alone would blow it up - while promotion level names are a
 * small controlled set per project, and are what an alert would be written against.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Auto promotion metrics")
@APIDescription("Metrics for the auto promotion.")
object AutoPromotionMetrics {

    @APIDescription("Number of auto promotions which have been revoked because one of their prerequisites was no longer valid.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag(Tags.PROJECT, "Name of the project."),
            MetricsMeterTag(Tags.PROMOTION_LEVEL, "Name of the revoked promotion level."),
        ]
    )
    const val autoPromotionRevokeCount = "ontrack_extension_general_auto_promotion_revoke_count"

    @APIDescription("Number of auto promotion revocations which have failed. Read together with the revocation count, which provides the denominator.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag(Tags.PROJECT, "Name of the project."),
            MetricsMeterTag(Tags.PROMOTION_LEVEL, "Name of the promotion level whose revocation failed."),
        ]
    )
    const val autoPromotionRevokeErrorCount = "ontrack_extension_general_auto_promotion_revoke_error_count"

    @DocumentationIgnore
    object Tags {
        const val PROJECT = "project"
        const val PROMOTION_LEVEL = "promotionLevel"
    }
}
