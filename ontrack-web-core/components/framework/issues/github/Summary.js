import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import {Space, Tag, Typography} from "antd";

export default function IssueGitHubSummary({rawIssue}) {
    return (
        <>
            <Space className="ot-line" orientation="vertical">

                {
                    rawIssue.bodyHtml &&
                    <SafeHTMLComponent htmlContent={rawIssue.bodyHtml}/>
                }

                <Space>

                    State:
                    <Tag color={rawIssue.state === 'open' ? 'green' : 'red'}>{rawIssue.state}</Tag>

                    {
                        rawIssue.milestone &&
                        <>
                            Milestone:
                            <Tag color={rawIssue.milestone.state === 'open' ? 'green' : 'red'}>
                                <Typography.Link
                                    href={rawIssue.milestone.url}>{rawIssue.milestone.title}</Typography.Link>
                            </Tag>
                        </>
                    }

                    {
                        rawIssue.labels &&
                        <>
                            Labels:
                            {
                                rawIssue.labels.map(label => (
                                    <Tag key={label.id} color={`#${label.color}`}>{label.name}</Tag>
                                ))
                            }
                        </>
                    }

                    {
                        rawIssue.assignee &&
                        <>
                            Assignee:
                            <Typography.Link href={rawIssue.assignee.url}>{rawIssue.assignee.login}</Typography.Link>
                        </>
                    }

                </Space>

            </Space>
        </>
    )
}