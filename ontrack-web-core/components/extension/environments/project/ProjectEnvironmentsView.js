import Head from "next/head"
import MainPage from "@components/layouts/MainPage"
import {CloseCommand} from "@components/common/Commands"
import {projectUri} from "@components/common/Links"
import LoadingContainer from "@components/common/LoadingContainer"
import {useProject} from "@components/services/useProject"
import {projectTitle} from "@components/common/Titles"
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs"
import EnvironmentsCommand from "@components/extension/environments/EnvironmentsCommand"
import ProjectEnvironments from "@components/extension/environments/project/ProjectEnvironments"

/**
 * The project environments page: same route, same "Environments" command on the project page.
 *
 * Two commands and no banner (#1795). "All environments" leads back to the home matrix, Close back
 * to the project, and the "still under experiment" note that used to sit on every screen of the
 * feature now appears once, on the Setup page.
 */
export default function ProjectEnvironmentsView({id}) {
    const {project, loading} = useProject({id})
    return (
        <>
            <Head>
                {project && projectTitle(project, "Environments")}
            </Head>
            <MainPage
                title="Environments"
                breadcrumbs={project ? downToProjectBreadcrumbs({project}) : []}
                commands={[
                    <EnvironmentsCommand key="environments" text="All environments"/>,
                    <CloseCommand key="close" href={projectUri({id})}/>,
                ]}
            >
                <LoadingContainer loading={loading}>
                    {
                        project &&
                        <ProjectEnvironments project={project}/>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}
