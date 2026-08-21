import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import ScmChangeLogByNameView from "@components/extension/scm/ScmChangeLogByNameView";

export default function ScmChangeLogByNamePage() {
    const router = useRouter()
    const {project, from, to, fromBranch, toBranch} = router.query
    return (
        <>
            <main>
                <MainLayout>
                    <ScmChangeLogByNameView
                        project={project}
                        from={from}
                        to={to}
                        fromBranch={fromBranch}
                        toBranch={toBranch}
                    />
                </MainLayout>
            </main>
        </>
    )
}
