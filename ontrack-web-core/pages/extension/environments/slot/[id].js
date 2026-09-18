import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import SlotView from "@components/extension/environments/slot/SlotView";

export default function SlotPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <SlotView id={id}/>
                </MainLayout>
            </main>
        </>
    )
}
