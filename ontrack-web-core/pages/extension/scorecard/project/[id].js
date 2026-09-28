import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import ProjectScorecardView from "@components/extension/scorecard/project/ProjectScorecardView";

export default function ProjectScorecardPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <ProjectScorecardView id={Number(id)}/>
                </MainLayout>
            </main>
        </>
    )
}
