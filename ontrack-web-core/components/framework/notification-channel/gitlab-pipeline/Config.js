import {Space, Typography} from "antd";
import {FaArrowRight} from "react-icons/fa";
import Duration from "@components/common/Duration";

export default function GitLabPipelineNotificationChannelConfig({
                                                                    config,
                                                                    project,
                                                                    ref,
                                                                    variables,
                                                                    callMode,
                                                                    timeoutSeconds,
                                                                }) {
    return (
        <Space orientation="vertical">
            <Space size={4} wrap>
                <Typography.Text>Triggering a pipeline on</Typography.Text>
                <Typography.Text code>{project}@{ref}</Typography.Text>
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
