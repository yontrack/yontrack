import {Space} from "antd";
import {Fragment} from "react";

export default function MainPageCommands({commands}) {
    return (
        <Space
            orientation="horizontal"
            wrap
            size={4}>
            {commands && commands.map((command, index) => {
                return <Fragment key={index}>{command}</Fragment>
            })}
        </Space>
    )
}