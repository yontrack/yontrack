import {Skeleton, Space, theme, Typography} from "antd";
import TimestampText from "@components/common/TimestampText";
import {buildVersion} from "@components/branches/views/pipeline/pipelineFacts";

/**
 * Three facts about the branch, and nothing else.
 *
 * NOT a header: there is no gradient band, no page title and no "New build" button here, because the
 * page chrome above already provides all three and a content view which rebuilds the app shell just
 * makes the user read the branch's name twice.
 *
 * @param totalBuilds The branch's unfiltered total. Deliberately not the size of the filtered page:
 *   a number which moves when you filter is a readout of the filter, not a fact about the branch.
 * @param latestBuild The most recent build of the branch, again outside any filter
 * @param loading Whether the facts are still being fetched
 */
export default function PipelineStats({totalBuilds, latestBuild, loading}) {

    const {token} = theme.useToken()

    // The "latest version" is the most recent build's own display name - NOT the most recent build
    // which happens to carry a release. That is a different question, more expensive to answer, and
    // its answer is stale the moment an unreleased build lands.
    const version = buildVersion(latestBuild)

    const stat = (id, label, children) => (
        <Space direction="vertical" size={0} data-testid={`pipeline-stat-${id}`}>
            <Typography.Text type="secondary" style={{fontSize: token.fontSizeSM}}>{label}</Typography.Text>
            <Typography.Text style={{fontSize: token.fontSizeHeading4}}>{children}</Typography.Text>
        </Space>
    )

    return (
        <Skeleton loading={loading} active paragraph={{rows: 1}} title={false}>
            <Space size={token.marginXL} wrap data-testid="pipeline-stats">
                {stat('total-builds', "Total builds", totalBuilds ?? 0)}
                {
                    // Hidden, not blank: for a project which does not use releases the display name
                    // IS the build name, and a "Latest version" repeating the build below it would
                    // be an invented fact.
                    version && stat('latest-version', "Latest version", version)
                }
                {
                    // The age is what a scanning reader wants; the absolute time stays on hover
                    latestBuild?.creation?.time &&
                    stat(
                        'latest-build',
                        "Latest build",
                        <TimestampText value={latestBuild.creation.time} relative={true}/>,
                    )
                }
            </Space>
        </Skeleton>
    )
}
