import {Popover, Space, Typography} from "antd";

export default function GlobalRole({role}) {
    return <Popover
        content={
            <Space orientation="vertical">
                <Typography.Text code>
                    {role.id}
                </Typography.Text>
                <Typography.Text>
                    {role.description}
                </Typography.Text>
            </Space>
        }
    >
        <Typography.Text>
            {role.name}
        </Typography.Text>
    </Popover>
}