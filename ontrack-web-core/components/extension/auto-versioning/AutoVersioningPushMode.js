import {Tag} from "antd";

const modes = {
    PR: {
        name: "Pull request",
        color: "blue",
    },
    PUSH: {
        name: "Direct push",
        color: "orange",
    },
}

export default function AutoVersioningPushMode({pushMode}) {
    return (
        <Tag color={modes[pushMode]?.color}>
            {modes[pushMode]?.name ?? pushMode}
        </Tag>
    )
}
