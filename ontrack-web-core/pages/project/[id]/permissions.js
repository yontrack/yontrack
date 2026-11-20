import {useRouter} from "next/router";
import MainLayout from "@components/layouts/MainLayout";
import ProjectPermissionsView from "@components/projects/permissions/ProjectPermissionsView";

/**
 * The permissions of a project, reached from the "Permissions" command of the project page (#2164).
 */
export default function ProjectPermissionsPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <>
            <main>
                <MainLayout>
                    {id && <ProjectPermissionsView id={Number(id)}/>}
                </MainLayout>
            </main>
        </>
    )
}
