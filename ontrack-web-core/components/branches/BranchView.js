import Head from "next/head";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlBranchContentFragment} from "@components/branches/BranchGraphQLFragments";
import {gqlInformationFragment, gqlPropertiesFragment, gqlUserMenuActionFragment} from "@components/services/fragments";
import {branchTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {Space} from "antd";
import BranchFavourite from "@components/branches/BranchFavourite";
import {branchBreadcrumbs} from "@components/common/Breadcrumbs";
import {useEffect, useState} from "react";
import NewBuildCommand from "@components/builds/NewBuildCommand";
import DisableBranchCommand from "@components/branches/DisableBranchCommand";
import {isAuthorized} from "@components/common/authorizations";
import BranchDeleteCommand from "@components/branches/BranchDeleteCommand";
import {CloseCommand, Command} from "@components/common/Commands";
import {FaMedal, FaProjectDiagram, FaStamp} from "react-icons/fa";
import {branchLinksUri, branchPromotionLevelsUri, branchValidationStampsUri, projectUri} from "@components/common/Links";
import UserMenuActions from "@components/entities/UserMenuActions";
import JumpToBranch from "@components/branches/JumpToBranch";
import LoadingContainer from "@components/common/LoadingContainer";
import BranchInfoViewDrawer from "@components/branches/BranchInfoViewDrawer";
import BranchContent from "@components/branches/BranchContent";
import {useEventForRefresh} from "@components/common/EventsContext";

export default function BranchView({id}) {

    const branchUpdated = useEventForRefresh("branch.updated")

    const {data: branch, loading} = useQuery(
        gql`
            query GetBranch($id: Int!) {
                branch(id: $id) {
                    ...BranchContent
                    authorizations {
                        name
                        action
                        authorized
                    }
                    properties {
                        ...propertiesFragment
                    }
                    information {
                        ...informationFragment
                    }
                    userMenuActions {
                        ...userMenuActionFragment
                    }
                }
            }
            ${gqlBranchContentFragment}
            ${gqlPropertiesFragment}
            ${gqlInformationFragment}
            ${gqlUserMenuActionFragment}
        `,
        {
            variables: {
                id: Number(id),
            },
            deps: [branchUpdated],
            initialData: null,
            dataFn: data => data.branch,
        }
    )

    const [commands, setCommands] = useState([])

    useEffect(() => {
        if (branch && !loading) {
            const commands = []
            commands.push(
                <NewBuildCommand
                    key="build-create"
                    branch={branch}
                />
            )
            if (isAuthorized(branch, "branch", "disable")) {
                commands.push(
                    <DisableBranchCommand
                        key="disable-enable"
                        branch={branch}
                    />
                )
            }
            if (isAuthorized(branch, "branch", "delete")) {
                commands.push(
                    <BranchDeleteCommand
                        key="delete"
                        id={branch.id}
                    />
                )
            }
            commands.push(
                <Command
                    key="promotionLevels"
                    icon={<FaMedal/>}
                    href={branchPromotionLevelsUri(branch)}
                    text="Promotions"
                    title="Management of the promotion levels for this branch"
                />,
                <Command
                    key="validationStamps"
                    icon={<FaStamp/>}
                    href={branchValidationStampsUri(branch)}
                    text="Validations"
                    title="Management of the validation stamps for this branch"
                />,
                <UserMenuActions key="userMenuActions" actions={branch.userMenuActions}/>,
                <JumpToBranch key="branch" projectName={branch.project.name}/>,
                <Command
                    key="links"
                    icon={<FaProjectDiagram/>}
                    href={branchLinksUri(branch)}
                    text="Links"
                    title="Displays downstream and upstream dependencies"
                />,
                <CloseCommand key="close" href={projectUri(branch.project)}/>,
            )
            setCommands(commands)
        }
    }, [branch, loading])

    return (
        <>
            <Head>
                {branchTitle(branch)}
            </Head>
            <MainPage
                title={
                    <Space>
                        {branch?.name}
                        {!loading && branch !== null && <BranchFavourite branch={branch}/>}
                    </Space>
                }
                breadcrumbs={branchBreadcrumbs(branch)}
                commands={commands}
            >
                <LoadingContainer loading={loading} tip="Loading branch">
                    {
                        branch && <>
                            <BranchInfoViewDrawer branch={branch} loadingBranch={loading}/>
                            <BranchContent branch={branch}/>
                        </>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}