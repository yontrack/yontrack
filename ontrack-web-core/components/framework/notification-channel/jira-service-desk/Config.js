import {Descriptions, Space, Tag, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import Link from "next/link";
import YesNo from "@components/common/YesNo";
import JiraCustomFields from "@components/extension/jira/JiraCustomFields";
import JiraServiceDeskRequestStatus from "@components/extension/jira/JiraServiceDeskRequestStatus";

export default function JiraServiceDeskNotificationChannelConfig({
                                                                     configName,
                                                                     serviceDeskId,
                                                                     requestTypeId,
                                                                     useExisting,
                                                                     requestStatus,
                                                                     searchTerm,
                                                                     fields,
                                                                 }) {

    const {data: url} = useQuery(
        gql`
            query GetJiraConfiguration($config: String!) {
                jiraConfiguration(name: $config) {
                    url
                }
            }
        `,
        {
            variables: {config: configName},
            deps: [configName],
            condition: !!configName,
            initialData: '',
            dataFn: data => data.jiraConfiguration?.url,
        }
    )

    const items = [
        {
            key: 'configName',
            label: 'Jira server',
            children: <Space>
                <Tag>{configName}</Tag>
                {
                    url && <Link href={url}>{url}</Link>
                }
            </Space>,
            span: 6,
        },
        {
            key: 'serviceDeskId',
            label: 'Service desk ID',
            children: <Tag>{serviceDeskId}</Tag>,
            span: 6,
        },
        {
            key: 'requestTypeId',
            label: 'Request type ID',
            children: <Tag>{requestTypeId}</Tag>,
            span: 6,
        },
        {
            key: 'useExisting',
            label: 'Using existing issue',
            children: <YesNo value={useExisting}/>,
            span: 6,
        },
        {
            key: 'requestStatus',
            label: 'Request status for existing',
            children: <JiraServiceDeskRequestStatus status={requestStatus}/>,
            span: 6,
        },
        {
            key: 'searchTerm',
            label: 'Search term',
            children: <Typography.Text code>{searchTerm}</Typography.Text>,
            span: 6,
        },
        {
            key: 'fields',
            label: 'Fields',
            children: <JiraCustomFields customFields={fields}/>,
            span: 12,
        },
    ]

    return (
        <>
            <Descriptions
                items={items}
                column={12}
            />
        </>
    )
}