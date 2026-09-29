import {Space, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import Link from "next/link";
import {FaArrowRight} from "react-icons/fa";
import Duration from "@components/common/Duration";

export default function JenkinsNotificationChannelConfig({config, job, parameters, callMode, timeout}) {

    const {data: url} = useQuery(
        gql`
            query GetJenkinsConfiguration($config: String!) {
                jenkinsConfiguration(name: $config) {
                    url
                }
            }
        `,
        {
            variables: {config},
            deps: [config],
            condition: !!config,
            initialData: '',
            dataFn: data => data.jenkinsConfiguration?.url,
        }
    )

    return (
        <>
            <Space orientation="vertical">
                <Space size={4}>
                    <Typography.Text>Triggering job at</Typography.Text>
                    {
                        url && <Link href={url}>{url}</Link>
                    }
                    (<Typography.Text code>{config}</Typography.Text>)
                    <Typography.Text code>{job}</Typography.Text>
                </Space>
                {
                    parameters && parameters.length > 0 &&
                    <>
                        <Typography.Text>Parameters:</Typography.Text>
                        <ul>
                            {
                                parameters.map(({name, value}) => (
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
                    <Duration seconds={timeout} displaySecondsInTooltip={true} defaultText="Default"/>
                </Space>
            </Space>
        </>
    )
}