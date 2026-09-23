import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import ProjectFindingsView from "@components/extension/findings/project/ProjectFindingsView";

export default function ProjectFindingsPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <ProjectFindingsView id={Number(id)}/>
                </MainLayout>
            </main>
        </>
    )
}
