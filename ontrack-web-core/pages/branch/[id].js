import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import BranchView from "@components/branches/BranchView";

export default function BranchPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    {/*
                      * Keyed on the branch, not on the whole path: the branch view loads its branch
                      * once, on mount, so moving between branches has to remount it — but the query
                      * changing must not. Selecting a branch content view writes `?view=` back, and
                      * the build filter permalink writes and clears `?buildFilter=`; keying on the
                      * path made each of those refetch the branch and throw away the state living
                      * above the content view.
                      */}
                    <BranchView id={Number(id)} key={id}/>
                </MainLayout>
            </main>
        </>
    )
}