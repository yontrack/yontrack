import {Alert, Typography} from "antd";

export default function GlobalMessage({type, content}) {



    return (
        <>
            <Alert
                title={
                    <Typography.Text>{content}</Typography.Text>
                }
                showIcon
                type={type.toLowerCase()}
            />
        </>
    )
}