import Head from "next/head";
import {buildTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {buildBreadcrumbs} from "@components/common/Breadcrumbs";
import LoadingContainer from "@components/common/LoadingContainer";
import {gql} from "graphql-request";
import {CloseCommand, Command} from "@components/common/Commands";
import {branchUri, buildAuditTrailUri, buildLinksUri} from "@components/common/Links";
import {
    gqlDecorationFragment,
    gqlInformationFragment,
    gqlPropertiesFragment,
    gqlUserMenuActionFragment
} from "@components/services/fragments";
import BuildContent from "@components/builds/BuildContent";
import {Space} from "antd";
import Decorations from "@components/framework/decorations/Decorations";
import InfoViewDrawer from "@components/common/InfoViewDrawer";
import {useQuery} from "@components/services/GraphQL";
import StoredGridLayoutResetCommand from "@components/grid/StoredGridLayoutResetCommand";
import StoredGridLayoutContextProvider from "@components/grid/StoredGridLayoutContext";
import {FaProjectDiagram, FaShieldAlt} from "react-icons/fa";
import UserMenuActions from "@components/entities/UserMenuActions";
import EditBuildCommand from "@components/builds/EditBuildCommand";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import {useRefresh} from "@components/common/RefreshUtils";
import BuildDeleteCommand from "@components/builds/BuildDeleteCommand";
import {isAuthorized} from "@components/common/authorizations";
import PreviousBuildCommand from "@components/builds/PreviousBuildCommand";
import NextBuildCommand from "@components/builds/NextBuildCommand";
import {buildVisit, useRecordVisit} from "@components/search/palette/recentlyVisited";
import {gqlSignatureActorFields} from "@components/common/actors/actors";
import ActorBadge from "@components/common/actors/ActorBadge";
import {gqlAssistedChangeFields} from "@components/extension/scm/assistants/assistants";
import AssistedBadge from "@components/extension/scm/assistants/AssistedBadge";
import {gqlBuildAgentActionsProbe} from "@components/builds/agents/buildAgents";

const noBuild = {branch: {project: {}}}

export default function BuildView({id}) {

    const [refreshState, refresh] = useRefresh()

    const {data: queriedBuild, loading, finished} = useQuery(
        gql`
            query GetBuild($id: Int!) {
                build(id: $id) {
                    id
                    name
                    description
                    annotatedDescription
                    creation {
                        user
                        time
                        ${gqlSignatureActorFields}
                    }
                    ${gqlAssistedChangeFields}
                    ${gqlBuildAgentActionsProbe}
                    userMenuActions {
                        ...userMenuActionFragment
                    }
                    releaseProperty {
                        value
                    }
                    properties {
                        ...propertiesFragment
                    }
                    information {
                        ...informationFragment
                    }
                    decorations {
                        ...decorationContent
                    }
                    branch {
                        id
                        name
                        project {
                            id
                            name
                        }
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    previousBuild {
                        id
                        name
                        displayName
                    }
                    nextBuild {
                        id
                        name
                        displayName
                    }
                }
            }

            ${gqlDecorationFragment}
            ${gqlPropertiesFragment}
            ${gqlInformationFragment}
            ${gqlUserMenuActionFragment}
        `,
        {
            variables: {id: Number(id)},
            deps: [id, refreshState],
            condition: !!id,
            initialData: noBuild,
            dataFn: data => data.build,
        }
    )
    const build = queriedBuild ?? noBuild
    const loadingBuild = loading || !finished

    // Listed by the command palette before anything is typed
    useRecordVisit(buildVisit(build))

    const commands = []
    if (build.id) {
        commands.push(
            <InfoViewDrawer
                key="details"
                id="build-info"
                entityType="BUILD"
                entityName="build"
                entity={build}
            />,
            <PreviousBuildCommand
                key={`previous-${build.id}`}
                previousBuild={build.previousBuild}
            />,
            <NextBuildCommand
                key={`next-${build.id}`}
                nextBuild={build.nextBuild}
            />,
            <UserMenuActions
                key="tools"
                actions={build.userMenuActions}
            />,
            <Command
                key="links"
                icon={<FaProjectDiagram/>}
                href={buildLinksUri(build)}
                text="Links"
                title="Displays downstream and upstream dependencies"
            />,
        )
        // Granted when the build has a trail to read: the licence is folded in on the server
        if (isAuthorized(build, "auditTrail", "view")) {
            commands.push(
                <Command
                    key="audit-trail"
                    icon={<FaShieldAlt/>}
                    href={buildAuditTrailUri(build)}
                    text="Audit trail"
                    title="Displays the audit trail of the build: its entries and their verification"
                    testId="build-audit-trail-command"
                />,
            )
        }
        if (isAuthorized(build, "build", "edit")) {
            commands.push(
                <EditBuildCommand
                    build={build}
                    onSuccess={refresh}
                    key="edit"
                />
            )
        }
        commands.push(
            <StoredGridLayoutResetCommand key="reset"/>,
        )
        if (isAuthorized(build, "build", "delete")) {
            commands.push(<BuildDeleteCommand key="delete" id={build.id}/>)
        }
        commands.push(<CloseCommand key="close" href={branchUri(build.branch)}/>)
    }

    return (
        <>
            <Head>
                {buildTitle(build)}
            </Head>
            <StoredGridLayoutContextProvider>
                <MainPage
                    title={
                        <Space>
                            {build.name}
                            <Decorations entity={build}/>
                            {/* Whether an agent wrote its commits, and whether an agent created it (#2032) */}
                            <AssistedBadge assistedChange={build.assistedChange} testId="build-assisted"/>
                            <ActorBadge signature={build.creation} prefix="by" hideHuman={true} testId="build-actor"/>
                            <AnnotatedDescription entity={build} type="secondary" disabled={false}/>
                        </Space>
                    }
                    breadcrumbs={buildBreadcrumbs(build)}
                    commands={commands}
                >
                    <LoadingContainer loading={loadingBuild} tip="Loading build">
                        <BuildContent build={build}/>
                    </LoadingContainer>
                </MainPage>
            </StoredGridLayoutContextProvider>
        </>
    )
}