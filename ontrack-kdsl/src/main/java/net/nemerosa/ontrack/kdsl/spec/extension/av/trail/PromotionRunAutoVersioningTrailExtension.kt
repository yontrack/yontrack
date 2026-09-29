package net.nemerosa.ontrack.kdsl.spec.extension.av.trail

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PromotionRunAutoVersioningTrailQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.PromotionRun
import net.nemerosa.ontrack.kdsl.spec.extension.av.toAutoVersioningSourceConfig
import net.nemerosa.ontrack.kdsl.spec.toBranch

/**
 * Size of the pages read from `autoVersioningTrailPaginated`.
 */
private const val TRAIL_PAGE_SIZE = 50

/**
 * Trail of the auto-versioning of this promotion run: every branch it targeted, eligible or not,
 * read page after page from `autoVersioningTrailPaginated`.
 *
 * `null` when the promotion run is not found.
 */
val PromotionRun.autoVersioningTrail: AutoVersioningTrail?
    get() {
        val branches = mutableListOf<AutoVersioningBranchTrail>()
        var offset: Int? = 0
        while (offset != null) {
            val page = graphqlConnector.query(
                PromotionRunAutoVersioningTrailQuery(id.toInt(), offset, TRAIL_PAGE_SIZE)
            )?.promotionRuns?.firstOrNull()
                ?.autoVersioningTrailPaginated
                ?: return null
            page.pageItems.mapTo(branches) { branch ->
                AutoVersioningBranchTrail(
                    connector = connector,
                    branch = branch.branch.branchFragment.toBranch(this),
                    configuration = branch.configuration.autoVersioningSourceConfigFragment
                        .toAutoVersioningSourceConfig(),
                    rejectionReason = branch.rejectionReason,
                    orderId = branch.orderId,
                )
            }
            offset = page.pageInfo?.nextPage?.offset
        }
        return AutoVersioningTrail(branches = branches)
    }
