import {Space} from "antd";
import QueueDispatchResult from "@components/extension/queue/QueueDispatchResult";

/**
 * data = [QueueDispatchResult]
 */
export default function Info({data}) {
    return (
        <>
            <Space orientation="vertical">
                {
                    data.map(it => <QueueDispatchResult key={it.id} result={it}/>)
                }
            </Space>
        </>
    )
}