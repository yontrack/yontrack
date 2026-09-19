import {Table, Typography} from "antd";
import Link from "next/link";
import TimestampText from "@components/common/TimestampText";
import GitLabIssueState from "@components/extension/gitlab/GitLabIssueState";
import GitLabLabels from "@components/extension/gitlab/GitLabLabels";
import GitLabMilestone from "@components/extension/gitlab/GitLabMilestone";

const {Column} = Table

/**
 * The issues of a change log, when the issue service is GitLab.
 *
 * Loaded by its file name - `framework/issues/gitlab-issues` - from the `gitlab` service id, exactly as
 * GitHub's counterpart is. Everything but the plain `Issue` fields comes from `rawIssue`, which is the
 * JSON of `GitLabIssueWrapper`.
 */
export default function GitLabIssues({issues}) {
    return (
        <Table
            dataSource={issues}
            rowKey={issue => issue.displayKey}
            size="small"
        >
            <Column
                key="id"
                title="ID"
                render={(_, issue) => (
                    <Link href={issue.url}>
                        <Typography.Text code>
                            {issue.displayKey}
                        </Typography.Text>
                    </Link>
                )}
            />
            <Column
                key="state"
                title="State"
                render={(_, {rawIssue}) => (
                    <GitLabIssueState state={rawIssue.state}/>
                )}
            />
            <Column
                key="title"
                title="Title"
                render={(_, issue) => (
                    <Typography.Text>{issue.summary}</Typography.Text>
                )}
            />
            <Column
                key="milestone"
                title="Milestone"
                render={(_, {rawIssue}) => (
                    <GitLabMilestone title={rawIssue.milestoneTitle} url={rawIssue.milestoneUrl}/>
                )}
            />
            <Column
                key="updateTime"
                title="Last update"
                render={(_, issue) => (
                    <TimestampText value={issue.updateTime}/>
                )}
            />
            <Column
                key="labels"
                title="Labels"
                render={(_, {rawIssue}) => (
                    <GitLabLabels labels={rawIssue.labels}/>
                )}
            />
        </Table>
    )
}
