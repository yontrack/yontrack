import {Popover, Space, Tag, Typography} from "antd";

/**
 * A project role as a tag, its ID and description in a popover.
 */
export default function ProjectRoleTag({role}) {
    return (
        <Popover
            content={
                <Space orientation="vertical">
                    <Typography.Text code>{role.id}</Typography.Text>
                    <Typography.Text>{role.description}</Typography.Text>
                </Space>
            }
        >
            <Tag>{role.name}</Tag>
        </Popover>
    )
}
