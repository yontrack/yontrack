import ChangeLogBuild from "@components/extension/scm/ChangeLogBuild";
import Head from "next/head";
import {buildKnownName, title} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToBranchBreadcrumbs, homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {branchUri, homeUri} from "@components/common/Links";
import GridTable from "@components/grid/GridTable";
import GridTableContextProvider from "@components/grid/GridTableContext";
import GridCell from "@components/grid/GridCell";
import GitChangeLogCommits from "@components/extension/git/GitChangeLogCommits";
import ChangeLogIssues from "@components/extension/issues/ChangeLogIssues";
import ChangeLogLinks from "@components/extension/scm/ChangeLogLinks";
import ChangeLogSemantic from "@components/extension/scm/views/ChangeLogSemantic";
import ChangeLogViewSelector from "@components/extension/scm/views/ChangeLogViewSelector";
import useChangeLogViewSelection from "@components/extension/scm/views/useChangeLogViewSelection";
import {Alert, Empty, Typography} from "antd";

/**
 * Rendering of a change log, whatever the way its boundaries have been identified
 * (using their IDs or using their names).
 *
 * @param changeLog the change log to display, null when it could not be computed
 * @param loading true while the change log is being loaded
 * @param error message explaining why the change log could not be obtained, if any
 */
export default function ScmChangeLogContent({changeLog, loading, error}) {

    const {views, selectedViewKey, selectChangeLogView, options, setSemanticOption} =
        useChangeLogViewSelection()

    const semantic = selectedViewKey === 'semantic'

    // The commits cell is always in the classic view, and an option in the semantic one.
    const showCommits = !semantic || options.commits

    // Layout and items are built from the same list, in the same render: react-grid-layout
    // only re-derives its internal layout from a changed `layout` prop, so a layout naming a
    // cell the item list does not carry - or the other way round - collapses widgets into the
    // top-left corner (#1634).
    const cells = [
        {id: "from", w: 6, h: 5},
        {id: "to", w: 6, h: 5},
        {id: "links", w: 12, h: 7},
        ...(showCommits ? [{id: "commits", w: 12, h: 10}] : []),
        ...(semantic ? [{id: "semantic", w: 12, h: 14}] : [{id: "issues", w: 12, h: 10}]),
    ]

    const defaultLayout = cells.reduce(({layout, x, y}, cell) => ({
        layout: [...layout, {i: cell.id, x, y, w: cell.w, h: cell.h}],
        // Two half-width cells sit side by side; anything else starts its own row
        x: x + cell.w >= 12 ? 0 : x + cell.w,
        y: x + cell.w >= 12 ? y + cell.h : y,
    }), {layout: [], x: 0, y: 0}).layout

    // A boundary build cell, showing a skeleton for as long as the build is not known
    const boundaryCell = (id, label, build) => ({
        id,
        content: build.creation ?
            <ChangeLogBuild id={id} title={`${label} ${buildKnownName(build)}`} build={build}/> :
            <GridCell id={id} title={label} loading={loading}/>,
    })

    // Computed directly from props (not via useState+useEffect) so that GridTable
    // always receives its full item list on the very same render as `defaultLayout` -
    // otherwise react-grid-layout mounts with 0 children, and when the items are
    // filled in a tick later it falls back to its stale (empty) internal layout,
    // collapsing every widget into a default 1x1 slot at the top-left corner (#1634).
    const cellContent = {
        from: () => boundaryCell("from", "From", changeLog.buildFrom),
        to: () => boundaryCell("to", "To", changeLog.buildTo),
        links: () => ({
            id: "links",
            content: <ChangeLogLinks id="links" loading={loading}
                                     linkChanges={changeLog.linkChanges}/>,
        }),
        commits: () => ({
            id: "commits",
            content: <GitChangeLogCommits id="commits" loading={loading}
                                          commits={changeLog.commits}
                                          diffLink={changeLog.diffLink}/>,
        }),
        // The issues and the semantic rendering are loaded by their own component, in
        // parallel of the rest of the change log.
        issues: () => ({
            id: "issues",
            content: <ChangeLogIssues id="issues"
                                      from={Number(changeLog.buildFrom?.id)}
                                      to={Number(changeLog.buildTo?.id)}/>,
        }),
        semantic: () => ({
            id: "semantic",
            content: <ChangeLogSemantic id="semantic"
                                        from={Number(changeLog.buildFrom?.id)}
                                        to={Number(changeLog.buildTo?.id)}
                                        options={options}
                                        onOptionChange={setSemanticOption}/>,
        }),
    }

    const items = changeLog ? cells.map(cell => cellContent[cell.id]()) : []

    return (
        <>
            <Head>
                {
                    changeLog ?
                        title(`Change log | From ${buildKnownName(changeLog.buildFrom)} to ${buildKnownName(changeLog.buildTo)}`) :
                        "Change log"
                }
            </Head>
            <MainPage
                title={
                    changeLog ?
                        `Change log from ${buildKnownName(changeLog.buildFrom)} to ${buildKnownName(changeLog.buildTo)}` :
                        "No change log"
                }
                breadcrumbs={
                    changeLog && changeLog.buildFrom?.branch ? downToBranchBreadcrumbs(changeLog.buildFrom) : homeBreadcrumbs()
                }
                commands={[
                    <ChangeLogViewSelector
                        key="view"
                        views={views}
                        selectedViewKey={selectedViewKey}
                        onSelect={selectChangeLogView}
                    />,
                    <CloseCommand
                        key="close"
                        href={
                            changeLog && changeLog.buildFrom?.branch ? branchUri(changeLog.buildFrom.branch) : homeUri()
                        }
                    />,
                ]}
            >
                {
                    error && <Alert
                        type="error"
                        title="This change log cannot be displayed."
                        description={error}
                    />
                }
                {
                    !error && changeLog && items &&
                    // Each cell manages its own loading state, so that the issues
                    // can be loaded in parallel of the rest of the change log.
                    <GridTableContextProvider isExpandable={false} isDraggable={false}>
                        <GridTable
                            rowHeight={30}
                            layout={defaultLayout}
                            items={items}
                            isResizable={false}
                            isDraggable={false}
                        />
                    </GridTableContextProvider>
                }
                {
                    !error && !changeLog && !loading && <Empty
                        description={
                            <Typography.Text>
                                No change log could be computed for these builds.
                            </Typography.Text>
                        }
                    />
                }
            </MainPage>
        </>
    )
}
