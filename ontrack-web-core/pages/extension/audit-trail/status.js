import {useContext} from "react";
import {Alert} from "antd";
import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import {UserContext} from "@components/providers/UserProvider";
import AuditTrailStatusView from "@components/extension/audit-trail/AuditTrailStatusView";

/**
 * The audit trail status page, read-only, reached from the system group of the user menu: the
 * licence, the evidence storage and the instance key. Gated by the global `auditTrailStatus/view`
 * authorization — the global settings.
 */
export default function AuditTrailStatusPage() {

    const user = useContext(UserContext)
    const canView = !!user?.authorizations?.auditTrailStatus?.view

    return (
        <StandardPage
            pageTitle="Audit trail status"
            commands={[
                <CloseToHomeCommand key="home"/>,
            ]}
        >
            {
                canView ?
                    <AuditTrailStatusView/> :
                    <Alert type="warning" showIcon title="The audit trail status is reserved to the administrators."/>
            }
        </StandardPage>
    )
}
