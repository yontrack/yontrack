import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import ProjectScorecardView from "@components/extension/scorecard/project/ProjectScorecardView";

export default function ProjectScorecardPage() {
    const router = useRouter()
    const {id, set} = router.query

    // The selected set is in the URL, so that a link lands on it - replaced, not pushed: selecting
    // a set is not a navigation to go back from
    const onSetChange = (value) => {
        router.replace({pathname: router.pathname, query: {...router.query, set: value}}, undefined, {shallow: true})
    }

    return (
        <>
            <main>
                <MainLayout>
                    <ProjectScorecardView id={Number(id)} set={set} onSetChange={onSetChange}/>
                </MainLayout>
            </main>
        </>
    )
}
