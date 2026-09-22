import {Space, Typography} from "antd";
import ItemList from "@components/common/ItemList";
import Link from "next/link";

export default function SCMCatalogTeamsInformation({info}) {
    return <ItemList>
        {
            info.data.map((team, index) =>
                <ItemList.Item key={index}>
                    <Space>
                        {
                            team.url && <Link href={team.url}>
                                <Typography.Text>{team.name}</Typography.Text>
                            </Link>
                        }
                        {
                            !team.url && <Typography.Text>{team.name}</Typography.Text>
                        }
                        {
                            team.role && <Typography.Text disabled>{team.role}</Typography.Text>
                        }
                    </Space>
                </ItemList.Item>
            )
        }
    </ItemList>
}