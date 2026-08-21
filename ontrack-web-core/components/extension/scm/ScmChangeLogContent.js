import {useEffect, useState} from "react";
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

    const defaultLayout = [
        {i: "from", x: 0, y: 0, w: 6, h: 5},
        {i: "to", x: 6, y: 0, w: 6, h: 5},
        {i: "links", x: 0, y: 5, w: 12, h: 7},
        {i: "commits", x: 0, y: 12, w: 12, h: 10},
        {i: "issues", x: 0, y: 22, w: 12, h: 10},
    ]

    const [items, setItems] = useState([])

    useEffect(() => {
        if (changeLog) {
            // A boundary build cell, showing a skeleton for as long as the build is not known
            const boundaryCell = (id, label, build) => ({
                id,
                content: build.creation ?
                    <ChangeLogBuild id={id} title={`${label} ${buildKnownName(build)}`} build={build}/> :
                    <GridCell id={id} title={label} loading={loading}/>,
            })
            setItems(
                [
                    boundaryCell("from", "From", changeLog.buildFrom),
                    boundaryCell("to", "To", changeLog.buildTo),
                    {
                        id: "links",
                        content: <ChangeLogLinks id="links" loading={loading}
                                                 linkChanges={changeLog.linkChanges}/>,
                    },
                    {
                        id: "commits",
                        content: <GitChangeLogCommits id="commits" loading={loading}
                                                      commits={changeLog.commits}
                                                      diffLink={changeLog.diffLink}/>,
                    },
                    {
                        // The issues are loaded by the component itself, in parallel
                        // of the rest of the change log.
                        id: "issues",
                        content: <ChangeLogIssues id="issues"
                                                  from={Number(changeLog.buildFrom?.id)}
                                                  to={Number(changeLog.buildTo?.id)}/>,
                    },
                ]
            )
        }
    }, [changeLog, loading]);

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
                        message="This change log cannot be displayed."
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
