import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import BranchValidationStampsView from "@components/branches/validationStamps/BranchValidationStampsView";

export default function BranchValidationStampsPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    <BranchValidationStampsView id={Number(id)}/>
                </MainLayout>
            </main>
        </>
    )
}
