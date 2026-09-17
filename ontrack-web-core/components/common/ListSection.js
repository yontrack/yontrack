import {List, Skeleton, Space, Typography} from "antd";

export default function ListSection({title, extraTitle, icon, loading, items, emptyText}) {
    return (
        <>
            <Space direction="vertical" className="ot-line">
                <Typography.Title level={3}>
                    <Space>
                        {icon}
                        {title}
                        {extraTitle}
                    </Space>
                </Typography.Title>
                <Skeleton active loading={loading}>
                    <List
                        itemLayout="horizontal"
                        dataSource={items}
                        locale={emptyText ? {emptyText} : undefined}
                        renderItem={(item) =>
                            <List.Item data-testid={item.id}>
                                <List.Item.Meta
                                    avatar={item.icon}
                                    title={item.title}
                                    description={item.content}
                                />
                            </List.Item>
                        }
                    />
                </Skeleton>
            </Space>
        </>
    )
}