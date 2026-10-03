import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import BuildAuditTrailView from "@components/extension/audit-trail/BuildAuditTrailView";

/**
 * The audit trail of a build, reached from the "Audit trail" command of the build page.
 */
export default function BuildAuditTrailPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <BuildAuditTrailView id={Number(id)}/>
                </MainLayout>
            </main>
        </>
    )
}
