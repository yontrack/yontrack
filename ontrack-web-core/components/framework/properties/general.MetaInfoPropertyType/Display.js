import {Space, Tag, Typography} from "antd";
import ItemList from "@components/common/ItemList";
import Link from "next/link";

export default function Display({property}) {

    return (
        <>
            <ItemList>
                {
                    property.value.items.map((item, index) =>
                        <ItemList.Item
                            key={index}
                            title={
                                <Space>
                                    {
                                        item.category &&
                                        <Tag>{item.category}</Tag>
                                    }
                                    {item.name}
                                </Space>
                            }
                        >
                            {
                                !item.link &&
                                <Typography.Text>{item.value}</Typography.Text>
                            }
                            {
                                item.link &&
                                <Link href={item.link}>{item.value}</Link>
                            }
                        </ItemList.Item>
                    )
                }
            </ItemList>
        </>
    )
}