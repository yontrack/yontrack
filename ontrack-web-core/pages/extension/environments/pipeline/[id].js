import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import DeploymentView from "@components/extension/environments/deployment/DeploymentView";

export default function SlotPipelinePage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <DeploymentView id={id}/>
                </MainLayout>
            </main>
        </>
    )
}
