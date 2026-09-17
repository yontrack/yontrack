import {FaTimes} from "react-icons/fa";
import {Button, Space, Typography} from "antd";
import Link from "next/link";
import {homeUri} from "@components/common/Links";

export function Command({icon, text, href, target, action, title, disabled = false, testId}) {
    return <Button
        type="text"
        data-testid={testId}
        onClick={action}
        title={title}
        disabled={disabled}
    >
        {href && <Link href={href} target={target}>{icon} {text}</Link>}
        {!href && <>
            <Space size={8}>
                {icon}
                <Typography.Text>{text}</Typography.Text>
            </Space>
        </>}
    </Button>
}

export function CloseCommand({href}) {
    return <Command icon={<FaTimes/>} href={href} text="Close"/>
}

export function CloseToHomeCommand() {
    return <CloseCommand href={homeUri()}/>
}
