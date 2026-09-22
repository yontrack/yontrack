import {Space, Typography} from "antd";
import {FaArrowRight} from "react-icons/fa";
import Duration from "@components/common/Duration";

export default function BitbucketPipelinesNotificationChannelConfig({
                                                                        config,
                                                                        workspace,
                                                                        repository,
                                                                        branch,
                                                                        pipeline,
                                                                        variables,
                                                                        callMode,
                                                                        timeoutSeconds,
                                                                    }) {
    return (
        <Space orientation="vertical">
            <Space size={4} wrap>
                <Typography.Text>Triggering</Typography.Text>
                {
                    pipeline ?
                        <Typography.Text code>custom: {pipeline}</Typography.Text> :
                        <Typography.Text>the default pipeline</Typography.Text>
                }
                <Typography.Text>on</Typography.Text>
                <Typography.Text code>{workspace}/{repository}@{branch}</Typography.Text>
                (<Typography.Text code>{config}</Typography.Text>)
            </Space>
            {
                variables && variables.length > 0 &&
                <>
                    <Typography.Text>Variables:</Typography.Text>
                    <ul>
                        {
                            variables.map(({name, value}) => (
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
                    callMode === 'SYNC' ? 'Synchronous (waits for completion)' : 'Asynchronous (fire and forget)'
                }
            </Space>
            {
                callMode === 'SYNC' &&
                <Space>
                    <Typography.Text>Timeout:</Typography.Text>
                    <Duration seconds={timeoutSeconds} displaySecondsInTooltip={true} defaultText="Default"/>
                </Space>
            }
        </Space>
    )
}
