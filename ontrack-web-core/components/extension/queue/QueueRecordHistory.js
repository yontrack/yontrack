import {Typography} from "antd";
import TimestampText from "@components/common/TimestampText";
import ItemList from "@components/common/ItemList";

export default function QueueRecordHistory({record}) {
    return (
        <>
            <ItemList>
                {
                    (record.history ?? []).map((item, index) =>
                        <ItemList.Item
                            key={index}
                            title={
                                <Typography.Text code>{item.state}</Typography.Text>
                            }
                            description={
                                <TimestampText value={item.time}/>
                            }
                        />
                    )
                }
            </ItemList>
        </>
    )
}
