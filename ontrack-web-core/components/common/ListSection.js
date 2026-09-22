import {Skeleton, Space, Typography} from "antd";
import ItemList from "@components/common/ItemList";

export default function ListSection({title, extraTitle, icon, loading, items, emptyText}) {
    return (
        <>
            <Space orientation="vertical" className="ot-line">
                <Typography.Title level={3}>
                    <Space>
                        {icon}
                        {title}
                        {extraTitle}
                    </Space>
                </Typography.Title>
                <Skeleton active loading={loading}>
                    <ItemList emptyText={emptyText}>
                        {
                            (items ?? []).map(item =>
                                <ItemList.Item
                                    key={item.id}
                                    data-testid={item.id}
                                    avatar={item.icon}
                                    title={item.title}
                                    description={item.content}
                                />
                            )
                        }
                    </ItemList>
                </Skeleton>
            </Space>
        </>
    )
}
