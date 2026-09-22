import {Alert} from "antd";
import HealthIndicator from "@components/core/admin/health/HealthIndicator";
import ItemList from "@components/common/ItemList";

export default function Connectors({connectors}) {
    return (
        <>
            <ItemList>
                {
                    connectors.statuses.map((status, index) =>
                        <ItemList.Item
                            key={index}
                            avatar={<HealthIndicator status={status.status.type}/>}
                            title={`${status.status.description.connector.type} (${status.status.description.connector.name})`}
                            description={status.status.description.connection}
                        >
                            {
                                status.status.error &&
                                <Alert type="error" title={status.status.error}/>
                            }
                        </ItemList.Item>
                    )
                }
            </ItemList>
        </>
    )
}
