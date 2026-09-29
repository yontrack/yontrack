import {Typography} from "antd";
import Table from "@components/common/table/Table";
import Link from "next/link";
import GitHubIssueState from "@components/extension/github/GitHubIssueState";
import GitHubMilestone from "@components/extension/github/GitHubMilestone";
import GitHubUser from "@components/extension/github/GitHubUser";
import TimestampText from "@components/common/TimestampText";
import GitHubLabels from "@components/extension/github/GitHubLabels";

const {Column} = Table

export default function GithubIssues({issues}) {
    return (
        <>
            <Table
                dataSource={issues}
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
                        <GitHubIssueState state={rawIssue.state}/>
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
                        <GitHubMilestone milestone={rawIssue.milestone}/>
                    )}
                />
                <Column
                    key="assignee"
                    title="Assignee"
                    render={(_, {rawIssue}) => (
                        <GitHubUser user={rawIssue.assignee}/>
                    )}
                />
                <Column
                    key="updatedAt"
                    title="Last update"
                    render={(_, issue) =>
                        <TimestampText value={issue.updateTime}/>
                    }
                />
                <Column
                    key="labels"
                    title="Labels"
                    render={(_, {rawIssue}) =>
                        <GitHubLabels labels={rawIssue.labels}/>
                    }
                />
            </Table>
        </>
    )
}