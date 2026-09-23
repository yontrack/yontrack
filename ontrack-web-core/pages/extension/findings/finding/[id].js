import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import FindingView from "@components/extension/findings/finding/FindingView";

export default function FindingPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <FindingView id={Number(id)}/>
                </MainLayout>
            </main>
        </>
    )
}
