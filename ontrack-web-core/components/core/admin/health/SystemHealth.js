import {Space} from "antd";
import PageSection from "@components/common/PageSection";
import HealthComponents from "@components/core/admin/health/HealthComponents";
import Connectors from "@components/core/admin/health/Connectors";

export default function SystemHealth({systemHealth}) {
    return (
        <>
            <Space className="ot-line" orientation="vertical">
                {
                    systemHealth.health &&
                    <PageSection title="Health components" padding={true}>
                        <HealthComponents health={systemHealth.health}/>
                    </PageSection>
                }
                <PageSection title={`Connectors (${systemHealth.connectors.percent}%)`} padding={true}>
                    <Connectors connectors={systemHealth.connectors}/>
                </PageSection>
            </Space>
        </>
    )
}