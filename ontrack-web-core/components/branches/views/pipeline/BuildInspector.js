import {Skeleton, theme} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {gqlPipelineBuildInspection} from "@components/branches/views/pipeline/pipelineQueries";
import BuildInspectorPromotions from "@components/branches/views/pipeline/BuildInspectorPromotions";
import BuildInspectorValidations from "@components/branches/views/pipeline/BuildInspectorValidations";

/**
 * Promotions and validations for the selected build, so a build can be inspected without leaving
 * the branch.
 *
 * THE LAYOUT IS CONTAINER-DRIVEN, not viewport-driven: `auto-fit` over a 360px minimum. The content
 * region's width already varies with the collapsible sidebar, so a viewport media query would be
 * wrong the moment someone collapses the sidebar on a narrow screen - the panels would still be
 * stacked in a region which by then has room for two.
 *
 * The two columns are also equal. Giving validations twice the width would be exactly backwards for
 * a promotion-heavy project, and nothing here knows which kind of project it is looking at.
 *
 * @param buildId Id of the selected build, or nothing when there is no build to inspect
 * @param selectedFilter The active validation stamp filter, if any
 * @param showValidations Whether the branch has any validation stamp at all
 * @param onChange Called when the inspected build changes on the server (a promotion is granted)
 */
export default function BuildInspector({buildId, selectedFilter, showValidations, onChange}) {

    const {token} = theme.useToken()

    const [reloadCount, reload] = useRefresh()

    const {data: build, loading, finished} = useQuery(
        gqlPipelineBuildInspection,
        {
            variables: {buildId: Number(buildId)},
            deps: [buildId, reloadCount],
            condition: !!buildId,
            initialData: null,
            dataFn: data => data.build,
        }
    )

    const onPromotion = () => {
        // Both the panel and the regions above it are stale after a promotion: the timeline card
        // gains a medal and the stage card gains a build.
        reload()
        onChange?.()
    }

    if (!buildId) return null

    return (
        <div
            data-testid="build-inspector"
            style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
                gap: token.marginSM,
                alignItems: 'start',
            }}
        >
            <Skeleton loading={loading || !finished} active>
                {
                    build && <>
                        <BuildInspectorPromotions build={build} onChange={onPromotion}/>
                        {
                            // Hidden outright on a branch with no stamps: an empty validations panel
                            // would suggest the build is missing runs it was never going to have.
                            showValidations &&
                            <BuildInspectorValidations build={build} selectedFilter={selectedFilter}/>
                        }
                    </>
                }
            </Skeleton>
        </div>
    )
}
