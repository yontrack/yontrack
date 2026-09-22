import StandardPage from "@components/layouts/StandardPage";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import {useRouter} from "next/router";
import {useProject} from "@components/services/useProject";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {Space} from "antd";
import LoadingContainer from "@components/common/LoadingContainer";
import ProjectBuildSearchForm from "@components/projects/search/ProjectBuildSearchForm";
import {useProjectBuildSearch} from "@components/projects/search/useProjectBuildSearch";
import ProjectBuildSearchTable from "@components/projects/search/ProjectBuildSearchTable";
import {useState} from "react";

export default function ProjectBuildSearchPage() {

    const router = useRouter()
    const {id} = router.query

    const {project, loading: loadingProject} = useProject({id: Number(id)})

    const {builds, setBuilds, search, loading: loadingBuilds} = useProjectBuildSearch({project})

    const [selectedBuilds, setSelectedBuilds] = useState([])

    const onBuildSelected = (build) => {
        if (selectedBuilds.length < 2) {
            setSelectedBuilds(prevBuilds => [...prevBuilds, build])
            setBuilds(prevBuilds => prevBuilds.map(it => {
                if (it.id === build.id) {
                    return {
                        ...build,
                        selected: true,
                    }
                } else {
                    return it
                }
            }))
        }
    }

    const onBuildUnselected = (build) => {
        setSelectedBuilds(prevSelectedBuilds => prevSelectedBuilds.filter(it => it.id !== build.id))
        setBuilds(prevBuilds => prevBuilds.map(it => {
            if (it.id === build.id) {
                return {
                    ...build,
                    selected: false,
                }
            } else {
                return it
            }
        }))
    }

    return (
        <>
            <StandardPage
                pageTitle="Build search"
                loading={loadingProject}
                commands={[
                    <CloseCommand key="close" href={projectUri({id: Number(id)})}/>,
                ]}
                breadcrumbs={[
                    ...downToProjectBreadcrumbs({project})
                ]}
            >
                <LoadingContainer loading={loadingProject}>
                    <Space orientation="vertical" className="ot-line">
                        <ProjectBuildSearchForm
                            project={project}
                            onSubmit={search}
                            loading={loadingBuilds}
                        />
                        <ProjectBuildSearchTable
                            builds={builds}
                            selectedBuilds={selectedBuilds}
                            loading={loadingBuilds}
                            buildSelectable={selectedBuilds.length < 2}
                            onBuildSelected={onBuildSelected}
                            onBuildUnselected={onBuildUnselected}
                        />
                    </Space>
                </LoadingContainer>
            </StandardPage>
        </>
    )
}