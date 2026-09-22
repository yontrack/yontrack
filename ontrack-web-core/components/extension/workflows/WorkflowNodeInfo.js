import {Space, Typography} from "antd";
import {FaClock} from "react-icons/fa";
import Duration from "@components/common/Duration";

export default function WorkflowNodeInfo({node}) {
    return (
        <>
            <Space orientation="vertical" className="ot-line">
                {
                    node.description && <Typography type="secondary">{node.description}</Typography>
                }
                <Space>
                    <FaClock title="Timeout"/>
                    <Duration seconds={node.timeout}/>
                </Space>
            </Space>
        </>
    )
}