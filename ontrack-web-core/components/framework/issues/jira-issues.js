import {Typography} from "antd";
import Table from "@components/common/table/Table";
import Link from "next/link";
import TimestampText from "@components/common/TimestampText";
import JiraIssueStatus from "@components/extension/jira/JiraIssueStatus";
import JiraIssueVersions from "@components/extension/jira/JiraIssueVersions";
import JiraIssueLinks from "@components/extension/jira/JiraIssueLinks";

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
                    render={(_, issue) => <JiraIssueStatus status={issue.rawIssue.status}/>}
                />
                <Column
                    key="summary"
                    title="Summary"
                    render={(_, issue) => (
                        <Typography.Text>{issue.summary}</Typography.Text>
                    )}
                />
                <Column
                    key="affectedVersion"
                    title="Affected versions"
                    render={(_, {rawIssue}) =>
                        <JiraIssueVersions versions={rawIssue.affectedVersions}/>
                    }
                />
                <Column
                    key="fixVersion"
                    title="Fix versions"
                    render={(_, {rawIssue}) =>
                        <JiraIssueVersions versions={rawIssue.fixVersions}/>
                    }
                />
                <Column
                    key="issueType"
                    title="Issue type"
                    render={(_, {rawIssue}) =>
                        <Typography.Text>{rawIssue.issueType}</Typography.Text>
                    }
                />
                <Column
                    key="assignee"
                    title="Assignee"
                    render={(_, {rawIssue}) =>
                        <Typography.Text>{rawIssue.assignee}</Typography.Text>
                    }
                />
                <Column
                    key="updateTime"
                    title="Last update"
                    render={(_, issue) =>
                        <TimestampText value={issue.updateTime}/>
                    }
                />
                <Column
                    key="links"
                    title="Links"
                    render={(_, {rawIssue}) =>
                        <JiraIssueLinks links={rawIssue.links}/>
                    }
                />
            </Table>
        </>
    )
}