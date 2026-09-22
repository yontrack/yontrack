import {Alert, List} from "antd";
import HealthIndicator from "@components/core/admin/health/HealthIndicator";

export default function Connectors({connectors}) {
    return (
        <>
            <List>
                {
                    connectors.statuses.map((status, index) =>
                        <List.Item key={index}>
                            <List.Item.Meta
                                avatar={<HealthIndicator status={status.status.type}/>}
                                title={`${status.status.description.connector.type} (${status.status.description.connector.name})`}
                                description={status.status.description.connection}
                            />
                            {
                                status.status.error &&
                                <Alert type="error" title={status.status.error}/>
                            }
                        </List.Item>
                    )
                }
            </List>
        </>
    )
}