import {gql} from "graphql-request";
import {
    gqlAutoVersioningBranchTrailContent
} from "@components/extension/auto-versioning/AutoVersioningGraphQLFragments";
import {Typography} from "antd";
import AutoVersioningTrailTable, {
    autoVersioningTrailAuditColumn
} from "@components/extension/auto-versioning/AutoVersioningTrailTable";

export default function AutoVersioningPromotionRunTrail({promotionRunId}) {
    // No section header of its own: this trail is rendered inside a collapsible panel on the
    // promotion run page, which already carries the title and the count.
    return (
        <div id="auto-versioning-trail" data-testid="auto-versioning-trail">
            <Typography.Paragraph type="secondary" style={{padding: 8}}>
                List of auto-versioning targets for this promotion run.
            </Typography.Paragraph>
            <AutoVersioningTrailTable
                query={
                    gql`
                        query AutoVersioningPromotionRunTrail(
                            $promotionRunId: Int!,
                            $onlyEligible: Boolean = true,
                            $projectName: String = null,
                        ) {
                            promotionRuns(id: $promotionRunId) {
                                autoVersioningTrailPaginated(
                                    filter: {
                                        onlyEligible: $onlyEligible,
                                        projectName: $projectName,
                                    }
                                ) {
                                    pageInfo {
                                        nextPage {
                                            offset
                                            size
                                        }
                                    }
                                    pageItems {
                                        ...AutoVersioningBranchTrailContent
                                    }
                                }
                            }
                        }
                        ${gqlAutoVersioningBranchTrailContent}
                    `
                }
                variables={{
                    promotionRunId: Number(promotionRunId),
                }}
                queryNode={data => data.promotionRuns[0].autoVersioningTrailPaginated}
                extraColumns={[
                    autoVersioningTrailAuditColumn
                ]}
            />
        </div>
    )
}