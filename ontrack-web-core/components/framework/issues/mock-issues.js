import {Typography} from "antd";
import Table from "@components/common/table/Table";
import Link from "next/link";
import GitHubIssueState from "@components/extension/github/GitHubIssueState";
import GitHubMilestone from "@components/extension/github/GitHubMilestone";
import GitHubUser from "@components/extension/github/GitHubUser";
import TimestampText from "@components/common/TimestampText";
import GitHubLabels from "@components/extension/github/GitHubLabels";

const {Column} = Table

export default function MockIssues({issues}) {
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
                    key="status"
                    title="Status"
                    render={(_, issue) => <Typography.Text>{issue.status.name}</Typography.Text>}
                />
                <Column
                    key="summary"
                    title="Summary"
                    render={(_, issue) => (
                        <Typography.Text>{issue.summary}</Typography.Text>
                    )}
                />
                <Column
                    key="updateTime"
                    title="Last update"
                    render={(_, issue) =>
                        <TimestampText value={issue.updateTime}/>
                    }
                />
            </Table>
        </>
    )
}