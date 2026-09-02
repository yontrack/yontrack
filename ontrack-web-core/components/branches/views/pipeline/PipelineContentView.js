import {useContext, useEffect, useState} from "react";
import {Space} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useEventForRefresh} from "@components/common/EventsContext";
import {useRefresh} from "@components/common/RefreshUtils";
import useRangeSelection from "@components/common/RangeSelection";
import {ValidationStampFilterContext} from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import useBuildFilterSelection from "@components/branches/views/useBuildFilterSelection";
import useBuildSelection from "@components/branches/views/pipeline/useBuildSelection";
import {needsMoreBuilds} from "@components/branches/views/pipeline/buildSelection";
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
 * Like every content view it fetches its own data, so adding a fifth view never means editing a
 * shared fetcher. What it does NOT own is the validation stamp filter, which lives above the view
 * switch precisely so that a user's filter follows them from one view to the next.
 *
 * @param branch Branch being displayed
 */
export default function PipelineContentView({branch}) {

    const vsfContext = useContext(ValidationStampFilterContext)

    // Pagination status
    const [pagination, setPagination] = useState({
        offset: 0,
        size: 10,
    })

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
    const latestBuild = facts?.allBuilds?.pageItems?.[0]

    // The page of builds shown by the timeline, under the active filter
    const {data: buildsPage, loading, finished} = useQuery(
        gqlPipelineBuilds,
        {
            variables: {
                branchId: Number(branch.id),
                offset: pagination.offset,
                size: pagination.size,
                filterType: selectedBuildFilter?.type,
                // GraphQL type for the filter data is expected to be a string
                filterData: selectedBuildFilter ? JSON.stringify(selectedBuildFilter.data) : undefined,
            },
            deps: [branch, pagination, selectedBuildFilter, reloadCount, buildCreated],
            initialData: null,
            dataFn: data => data.branches[0].buildsPaginated,
        }
    )

    // `useQuery` only reports the loading as started from within its effect, so a view which showed
    // as loaded before the first fetch resolved would render an empty state over a branch which has
    // builds. Same guard the builds view makes.
    const loadingBuilds = loading || !finished

    const pageInfo = buildsPage?.pageInfo

    // Accumulation of the builds across the pages. A plain `const` will not do here: "load more"
    // appends, so the list is state built up over several responses rather than derived from the
    // latest one.
    const [builds, setBuilds] = useState([])
    // `pagination` is read but deliberately not a dependency: the effect must run once per RESPONSE,
    // not once per request. Adding it would append the same page again the moment the offset moved,
    // before the page it named had arrived.
    useEffect(() => {
        if (buildsPage) {
            if (pagination.offset > 0) {
                setBuilds(previous => [...previous, ...buildsPage.pageItems])
            } else {
                setBuilds(buildsPage.pageItems)
            }
        }
    }, [buildsPage])

    const onLoadMore = () => {
        if (pageInfo?.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    const {selectedBuildId, selectBuild} = useBuildSelection({builds})

    // A stage card names the latest build at its level, which for a low level can sit far below the
    // loaded page. Asking for it loads up to it rather than doing nothing.
    const [pendingBuildId, setPendingBuildId] = useState(null)
    useEffect(() => {
        if (needsMoreBuilds(builds, pendingBuildId, pageInfo)) {
            if (!loadingBuilds) {
                setPagination(pageInfo.nextPage)
            }
        } else if (pendingBuildId) {
            // Either it arrived, or there is nothing left to load and it never will
            setPendingBuildId(null)
        }
    }, [builds, pendingBuildId, pageInfo, loadingBuilds])

    const onSelectBuild = (buildId) => {
        selectBuild(buildId)
        setPendingBuildId(buildId)
    }

    // Range selection, feeding the change log button in the toolbar
    const rangeSelection = useRangeSelection()

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
                totalBuilds={facts?.allBuilds?.pageInfo?.totalSize}
                latestBuild={latestBuild}
                loading={loadingFacts || !finishedFacts}
            />
            {/* Renders nothing at all on a branch with no promotion levels */}
            <PipelineStages
                promotionLevels={promotionLevels}
                onSelectBuild={onSelectBuild}
            />
            <BuildTimeline
                builds={builds}
                loading={loadingBuilds}
                pageInfo={pageInfo}
                onLoadMore={onLoadMore}
                promotionLevels={promotionLevels}
                selectedFilter={vsfContext.selectedFilter}
                selectedBuildId={selectedBuildId}
                onSelect={onSelectBuild}
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
