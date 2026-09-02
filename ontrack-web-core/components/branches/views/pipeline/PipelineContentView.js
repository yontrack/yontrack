import {useContext, useEffect} from "react";
import {useRouter} from "next/router";
import {Space} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useEventForRefresh} from "@components/common/EventsContext";
import {useRefresh} from "@components/common/RefreshUtils";
import useRangeSelection from "@components/common/RangeSelection";
import {ValidationStampFilterContext} from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import useBuildFilterSelection from "@components/branches/views/useBuildFilterSelection";
import useBranchBuildsPage from "@components/branches/views/useBranchBuildsPage";
import useBuildSelection from "@components/branches/views/pipeline/useBuildSelection";
import {buildSelectionParam, needsMoreBuilds} from "@components/branches/views/pipeline/buildSelection";
import {gqlPipelineBranchFacts, gqlPipelineBuilds} from "@components/branches/views/pipeline/pipelineQueries";
import PipelineToolbar from "@components/branches/views/pipeline/PipelineToolbar";
import PipelineStats from "@components/branches/views/pipeline/PipelineStats";
import PipelineStages from "@components/branches/views/pipeline/PipelineStages";
import BuildTimeline from "@components/branches/views/pipeline/BuildTimeline";
import BuildInspector from "@components/branches/views/pipeline/BuildInspector";

/**
 * The pipeline branch content view: a branch read as a promotion pipeline - what is release-ready
 * right now - with the full build list one click away in the view menu.
 *
 * Four regions, in the order they answer questions: what is on this branch (stats), what does a
 * release have to go through (pipeline), what has been built lately (timeline), and what is true of
 * the one build I am looking at (inspector).
 *
 * What it does NOT own is the validation stamp filter, which lives above the view switch precisely
 * so that a user's filter follows them from one view to the next.
 *
 * @param branch Branch being displayed
 */
export default function PipelineContentView({branch}) {

    const router = useRouter()
    const vsfContext = useContext(ValidationStampFilterContext)

    // Same filter, same storage and same permalink semantics as the builds view
    const {selectedBuildFilter, onSelectedBuildFilter, onPermalinkBuildFilter} =
        useBuildFilterSelection({branch})

    const buildCreated = useEventForRefresh("build.created")
    const [reloadCount, reload] = useRefresh()

    // What the branch says about itself, outside any filter
    const {data: facts, loading: loadingFacts, finished: finishedFacts} = useQuery(
        gqlPipelineBranchFacts,
        {
            variables: {branchId: Number(branch.id)},
            deps: [branch, reloadCount, buildCreated],
            initialData: null,
            dataFn: data => data.branch,
        }
    )

    const promotionLevels = facts?.promotionLevels ?? []
    const validationStamps = facts?.validationStamps ?? []
    const totalBuilds = facts?.allBuilds?.pageInfo?.totalSize
    const latestBuild = facts?.allBuilds?.pageItems?.[0]

    // The page of builds shown by the timeline, on the same terms as the builds view's table
    const {builds, pageInfo, loadingBuilds, loadMore} = useBranchBuildsPage({
        branch,
        query: gqlPipelineBuilds,
        selectedBuildFilter,
    })

    // A build asked for but not in the loaded page yet - a `?build=` deep link to an older build, or
    // a stage card naming the latest build at its level - is LOADED UP TO rather than ignored. Both
    // routes go through the URL, which is why one rule covers them: selecting writes `?build=`, and
    // this reads it back.
    const requestedBuildId = router.query?.[buildSelectionParam]
    const resolving = needsMoreBuilds(builds, requestedBuildId, pageInfo)
    // `loadMore` is deliberately not a dependency: it is a fresh closure on every render, so listing
    // it would run this effect on every render instead of on the two things that actually decide
    // whether another page is due. It is idempotent for the page already in flight, which is what
    // makes calling the closure of an earlier render safe.
    useEffect(() => {
        if (resolving && !loadingBuilds) {
            loadMore()
        }
    }, [resolving, loadingBuilds])

    // While `resolving`, the selection holds its request instead of falling back to the most recent
    // build and rewriting the URL - the pages being loaded are being loaded for that request.
    const {selectedBuildId, selectBuild} = useBuildSelection({builds, resolving})

    // Range selection, feeding the change log button in the toolbar
    const rangeSelection = useRangeSelection()

    const hasBuilds = builds.length > 0

    return (
        <Space direction="vertical" size={16} className="ot-line">
            <PipelineToolbar
                branch={branch}
                selectedBuildFilter={selectedBuildFilter}
                onSelectedBuildFilter={onSelectedBuildFilter}
                onPermalinkBuildFilter={onPermalinkBuildFilter}
                scmChangeLogEnabled={facts?.scmBranchInfo?.changeLogs}
                rangeSelection={rangeSelection}
                loading={loadingBuilds}
            />
            <PipelineStats
                totalBuilds={totalBuilds}
                latestBuild={latestBuild}
                loading={loadingFacts || !finishedFacts}
            />
            {/* Nothing at all on a branch with no promotion levels, and nothing on a branch with no
                builds either: a full band of "never reached" stages above an empty timeline states
                the obvious loudly, and the empty state below says the one thing worth saying. */}
            {
                hasBuilds &&
                <PipelineStages
                    promotionLevels={promotionLevels}
                    onSelectBuild={selectBuild}
                />
            }
            <BuildTimeline
                builds={builds}
                loading={loadingBuilds}
                pageInfo={pageInfo}
                onLoadMore={loadMore}
                promotionLevels={promotionLevels}
                selectedFilter={vsfContext.selectedFilter}
                selectedBuildId={selectedBuildId}
                onSelect={selectBuild}
                rangeSelection={rangeSelection}
            />
            {/* Nothing to inspect when the branch has no build; the timeline says so on its own */}
            <BuildInspector
                buildId={selectedBuildId}
                selectedFilter={vsfContext.selectedFilter}
                showValidations={validationStamps.length > 0}
                onChange={reload}
            />
        </Space>
    )
}
