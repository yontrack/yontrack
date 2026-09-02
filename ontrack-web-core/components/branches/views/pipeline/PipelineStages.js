import {theme} from "antd";
import PipelineStageCard from "@components/branches/views/pipeline/PipelineStageCard";

/**
 * The promotion pipeline: one stage card per configured promotion level, in the branch's own order.
 *
 * Scrolls horizontally rather than wrapping or shrinking. A twelve-level pipeline is a long thing
 * and it reads as one; cards which got narrower as levels were added would make the same branch look
 * different on Tuesday because someone added a level on Monday.
 *
 * A branch with NO promotion levels renders nothing at all - not an empty state. There is no
 * pipeline to be empty of: the branch simply does not work that way, and a panel inviting the user
 * to fix that is the promotions page's job, not this band's.
 *
 * @param promotionLevels The branch's levels, lowest first, each carrying its `promotedBuildCount`
 *   and its latest run
 * @param onSelectBuild Called with a build id when a stage's latest build is picked
 */
export default function PipelineStages({promotionLevels, onSelectBuild}) {

    const {token} = theme.useToken()

    if (!promotionLevels || promotionLevels.length === 0) return null

    return (
        <div
            data-testid="pipeline-stages"
            style={{
                display: 'flex',
                gap: token.marginSM,
                overflowX: 'auto',
                // Room for the scrollbar to appear without the cards jumping when it does
                paddingBottom: token.paddingXS,
            }}
        >
            {
                promotionLevels.map(promotionLevel =>
                    <PipelineStageCard
                        key={promotionLevel.id}
                        promotionLevel={promotionLevel}
                        promotedBuildCount={promotionLevel.promotedBuildCount}
                        latestRun={promotionLevel.promotionRunsPaginated?.pageItems?.[0]}
                        onSelectBuild={onSelectBuild}
                    />
                )
            }
        </div>
    )
}
