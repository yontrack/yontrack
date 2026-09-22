import {Space, Tag, Typography} from "antd";
import ItemList from "@components/common/ItemList";

export default function Display({property}) {

    return (
        <>
            <ItemList size="small">
                {
                    property.value.links.map((link, index) =>
                        <ItemList.Item key={index}>
                            <Space>
                                <Tag>{link.name}</Tag>
                                <Typography.Link href={link.value}>{link.value}</Typography.Link>
                            </Space>
                        </ItemList.Item>
                    )
                }
            </ItemList>
        </>
    )
}