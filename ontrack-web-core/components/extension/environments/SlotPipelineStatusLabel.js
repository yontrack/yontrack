import {Space} from "antd";
import SlotPipelineStatusIcon from "@components/extension/environments/SlotPipelineStatusIcon";

export const slotPipelineStatusLabels = {
    CANDIDATE: "Candidate",
    RUNNING: "Running",
    CANCELLED: "Cancelled",
    DONE: "Deployed",
    FAILED: "Failed",
}

export default function SlotPipelineStatusLabel({status, showText = true}) {
    return (
        <>
            <Space>
                <SlotPipelineStatusIcon status={status}/>
                {
                    showText && slotPipelineStatusLabels[status]
                }
            </Space>
        </>
    )
}