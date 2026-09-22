import {Space, Typography} from "antd";
import {gql} from "graphql-request";
import Link from "next/link";
import {FaArrowRight} from "react-icons/fa";
import Duration from "@components/common/Duration";
import {useQuery} from "@components/services/GraphQL";
import LoadingContainer from "@components/common/LoadingContainer";

export default function GitHubWorkflowNotificationChannelConfig({
                                                                    config,
                                                                    owner,
                                                                    repository,
                                                                    workflowId,
                                                                    reference,
                                                                    inputs,
                                                                    callMode,
                                                                    timeoutSeconds
                                                                }) {

    const {data: url, loading} = useQuery(
        gql`
            query GitHubConfiguration($config: String!) {
                gitHubConfiguration(name: $config) {
                    url
                }
            }
        `,
        {
            variables: {config},
            initialData: '',
            deps: [config],
            dataFn: data => data.gitHubConfiguration?.url,
        }
    )

    return (
        <>
            <LoadingContainer loading={loading}>
                <Space orientation="vertical">
                    <Space size={4}>
                        <Typography.Text>Triggering workflow at</Typography.Text>
                        {
                            url && <Link
                                href={`${url}/${owner}/${repository}/actions`}>{url}/{owner}/{repository}/actions/workflows/${workflowId}</Link>
                        }
                        (<Typography.Text code>{config}</Typography.Text>)
                        <Typography.Text code>{workflowId}@{reference}</Typography.Text>
                    </Space>
                    {
                        inputs && inputs.length > 0 &&
                        <>
                            <Typography.Text>Inputs:</Typography.Text>
                            <ul>
                                {
                                    inputs.map(({name, value}) => (
                                        <li key={name}>
                                            <Space>
                                                <Typography.Text code>{name}</Typography.Text>
                                                <FaArrowRight/>
                                                <Typography.Text code>{value}</Typography.Text>
                                            </Space>
                                        </li>
                                    ))
                                }
                            </ul>
                        </>
                    }
                    <Space>
                        Call mode:
                        {
                            callMode === 'ASYNC' && 'Asynchronous (fire and forget)'
                        }
                        {
                            callMode === 'SYNC' && 'Synchronous (waits for completion)'
                        }
                    </Space>
                    <Space>
                        <Typography.Text>Timeout:</Typography.Text>
                        <Duration seconds={timeoutSeconds} displaySecondsInTooltip={true} defaultText="Default"/>
                    </Space>
                </Space>
            </LoadingContainer>
        </>
    )
}