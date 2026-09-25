import MainPage from "@components/layouts/MainPage";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import Head from "next/head";
import {projectTitle} from "@components/common/Titles";
import {projectBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import {
    gqlDecorationFragment,
    gqlInformationFragment,
    gqlPropertiesFragment,
    gqlUserMenuActionFragment
} from "@components/services/fragments";
import PageSection from "@components/common/PageSection";
import BranchList from "@components/branches/BranchList";
import {Empty, Space} from "antd";
import RowTag from "@components/common/RowTag";
import BranchBox from "@components/branches/BranchBox";
import JumpToBranch from "@components/branches/JumpToBranch";
import ProjectFavourite from "@components/projects/ProjectFavourite";
import {useEventForRefresh} from "@components/common/EventsContext";
import InfoViewDrawer from "@components/common/InfoViewDrawer";
import UserMenuActions from "@components/entities/UserMenuActions";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {isAuthorized} from "@components/common/authorizations";
import DisableProjectCommand from "@components/projects/DisableProjectCommand";
import DisabledProjectBanner from "@components/projects/DisabledProjectBanner";
import {gqlBranchContentFragment} from "@components/branches/BranchGraphQLFragments";
import NewBranchCommand from "@components/branches/NewBranchCommand";
import ProjectEnvironmentsCommand from "@components/extension/environments/project/ProjectEnvironmentsCommand";
import ProjectBuildSearchCommand from "@components/projects/ProjectBuildSearchCommand";
import ProjectDeleteCommand from "@components/projects/ProjectDeleteCommand";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import {useQuery} from "@components/services/GraphQL";
import {useRecordVisit, projectVisit} from "@components/search/palette/recentlyVisited";
import {useRefresh} from "@components/common/RefreshUtils";
import ProjectEditCommand from "@components/projects/ProjectEditCommand";
import ProjectLabelsCommand from "@components/projects/ProjectLabelsCommand";
import LabelChip from "@components/labels/LabelChip";
import ProjectSecuritySection from "@components/extension/findings/project/ProjectSecuritySection";

export default function ProjectView({id}) {

    const [refreshCount, refresh] = useRefresh()

    const favouriteRefreshCount = useEventForRefresh("branch.favourite")
    const projectUpdated = useEventForRefresh("project.updated")
    const branchCreated = useEventForRefresh("branch.created")

    const {data: project, loading} = useQuery(
        gql`
            query GetProject($id: Int!) {
                project(id: $id) {
                    ...ProjectContent
                    properties {
                        ...propertiesFragment
                    }
                    information {
                        ...informationFragment
                    }
                    userMenuActions {
                        ...userMenuActionFragment
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    branches(order: true, count: 6) {
                        ...BranchContent
                        favourite
                        decorations {
                            ...decorationContent
                        }
                    }
                    favouriteBranches: branches(favourite: true, order: true) {
                        ...BranchContent
                        favourite
                        decorations {
                            ...decorationContent
                        }
                        latestBuild: builds(count: 1) {
                            id
                            name
                            displayName
                        }
                        promotionLevels {
                            id
                            name
                            image
                            promotionRuns(first: 1) {
                                build {
                                    id
                                    name
                                    displayName
                                }
                            }
                        }
                    }
                }
            }

            ${gqlDecorationFragment}
            ${gqlPropertiesFragment}
            ${gqlInformationFragment}
            ${gqlUserMenuActionFragment}
            ${gqlProjectContentFragment}
            ${gqlBranchContentFragment}
        `,
        {
            initialData: {},
            variables: {id: Number(id)},
            deps: [refreshCount, favouriteRefreshCount, projectUpdated, branchCreated],
            dataFn: data => data.project,
        }
    )

    // Listed by the command palette before anything is typed
    useRecordVisit(projectVisit(project))

    const [branches, setBranches] = useState([])
    const [favouriteBranches, setFavouriteBranches] = useState([])
    const [commands, setCommands] = useState([])

    useEffect(() => {
        if (project) {
            setFavouriteBranches(project.favouriteBranches)
            setBranches(project.branches)

            // Commands
            const commands = [
                <InfoViewDrawer
                    key="details"
                    id="project-info"
                    entityType="PROJECT"
                    entityName="project"
                    entity={project}
                />
            ]
            // Commands depending on the project authorizations & state
            commands.push(
                <NewBranchCommand
                    key="branch-create"
                    project={project}
                />
            )
            if (isAuthorized(project, 'project', 'disable')) {
                commands.push(
                    <DisableProjectCommand
                        key="disable-enable"
                        project={project}
                    />
                )
            }
            // All the rest of commands
            commands.push(
                <ProjectEnvironmentsCommand key="environments" id={project.id}/>,
                <ProjectBuildSearchCommand key="search" id={project.id}/>,
                <UserMenuActions key="userMenuActions" actions={project.userMenuActions}/>,
                <JumpToBranch key="branch" projectName={project.name}/>,
            )
            // Assigning labels to the project
            if (isAuthorized(project, 'project', 'labels')) {
                commands.push(
                    <ProjectLabelsCommand key="labels" project={project}/>
                )
            }
            // Editing the project
            if (isAuthorized(project, 'project', 'edit')) {
                commands.push(
                    <ProjectEditCommand key="edit" project={project}/>
                )
            }
            // Deleting the project
            if (isAuthorized(project, 'project', 'delete')) {
                commands.push(
                    <ProjectDeleteCommand key="delete" id={project.id}/>
                )
            }
            // All the rest of commands
            commands.push(
                <CloseCommand key="close" href={homeUri()}/>,
            )
            // Setting the commands
            setCommands(commands)
        }
    }, [project])


    return (
        <>
            <Head>
                {projectTitle(project)}
            </Head>
            <MainPage
                pageId="project"
                title={
                    <Space>
                        {project.name}
                        <ProjectFavourite project={project}/>
                        {
                            project.labels && project.labels.length > 0 &&
                            <Space size={4} data-testid="project-labels">
                                {
                                    project.labels.map(label =>
                                        <LabelChip key={label.id} label={label}/>
                                    )
                                }
                            </Space>
                        }
                        <AnnotatedDescription entity={project} type="secondary" disabled={false}/>
                    </Space>
                }
                breadcrumbs={projectBreadcrumbs(project)}
                commands={commands}
            >
                <Space orientation="vertical" className="ot-line" size={16}>
                    <DisabledProjectBanner project={project}/>
                    {
                        favouriteBranches && favouriteBranches.length > 0 &&
                        <PageSection
                            loading={loading}
                            title="Favourite branches"
                            height="250px"
                            padding={true}
                        >
                            <BranchList
                                branches={favouriteBranches}
                                showProject={false}
                            />
                        </PageSection>
                    }
                    <PageSection
                        loading={loading}
                        title="Last active branches"
                        height="300px"
                        padding={true}
                    >
                        {
                            (!branches || branches.length === 0) &&
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="No branch has been created for this project"
                            />
                        }
                        {
                            branches && branches.length > 0 &&
                            <Space orientation="horizontal" size={16} wrap>
                                {
                                    branches.map(branch => <RowTag key={branch.id}>
                                            <BranchBox branch={branch}/>
                                        </RowTag>
                                    )
                                }
                            </Space>
                        }
                    </PageSection>
                    {
                        project.id &&
                        <ProjectSecuritySection project={project}/>
                    }
                </Space>
            </MainPage>
        </>
    )
}