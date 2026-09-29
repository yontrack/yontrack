import {useState} from "react";
import {CloseCommand} from "@components/common/Commands";
import {buildUri} from "@components/common/Links";
import Head from "next/head";
import {subBuildTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToBuildBreadcrumbs} from "@components/common/Breadcrumbs";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import {Skeleton} from "antd";
import {getLocallySelectedDependencyLinksMode, setLocallySelectedDependencyLinksMode} from "@components/storage/local";
import {FaProjectDiagram, FaStream} from "react-icons/fa";
import DependencyLinksModeButton from "@components/links/DependencyLinksModeButton";
import BuildLinksGraph from "@components/links/BuildLinksGraph";
import BuildLinksTree from "@components/links/BuildLinksTree";

const NO_BUILD = {branch: {project: ''}}

export default function BuildLinksView({id}) {

    const [selectedDependencyLinksMode, setDependencyLinksMode] = useState('')

    const changeDependencyLinksMode = (mode) => {
        setDependencyLinksMode(mode)
        setLocallySelectedDependencyLinksMode(mode)
    }

    const {data: loadedBuild, loading, finished} = useQuery(
        gql`
            query GetBuild($id: Int!) {
                build(id: $id) {
                    id
                    name
                    branch {
                        id
                        name
                        project {
                            id
                            name
                        }
                    }
                    releaseProperty {
                        value
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [id],
            condition: !!id,
            dataFn: data => data.build,
        }
    )
    const build = loadedBuild ?? NO_BUILD

    const commands = loadedBuild ? [
        <CloseCommand key="close" href={buildUri(loadedBuild)}/>,
    ] : []

    // Until chosen on this page, the mode is the one stored locally, once the build is loaded
    const dependencyLinksMode = selectedDependencyLinksMode ||
        (loadedBuild ? (getLocallySelectedDependencyLinksMode() ?? 'graph') : '')

    return (
        <>
            <Head>
                {subBuildTitle(build, "Links")}
            </Head>
            <MainPage
                title="Links"
                breadcrumbs={downToBuildBreadcrumbs({build: build})}
                commands={commands}
            >
                <DependencyLinksModeButton
                    key="graph"
                    icon={<FaProjectDiagram/>}
                    selectedMode={dependencyLinksMode}
                    mode="graph"
                    action={changeDependencyLinksMode}
                    title="Displays the dependencies as a graph"
                />
                <DependencyLinksModeButton
                    key="tree"
                    icon={<FaStream/>}
                    selectedMode={dependencyLinksMode}
                    mode="tree"
                    action={changeDependencyLinksMode}
                    title="Displays the dependencies as a tree"
                />
                <Skeleton active loading={loading || !finished}>
                    {
                        dependencyLinksMode === 'graph' && <BuildLinksGraph build={build}/>
                    }
                    {
                        dependencyLinksMode === 'tree' &&
                        <BuildLinksTree build={build} changeDependencyLinksMode={changeDependencyLinksMode}/>
                    }
                </Skeleton>
            </MainPage>
        </>
    )
}