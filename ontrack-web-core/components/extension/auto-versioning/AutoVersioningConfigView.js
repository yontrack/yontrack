import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import {Skeleton} from "antd";
import MainPage from "@components/layouts/MainPage";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {useState} from "react";
import {gql} from "graphql-request";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import AutoVersioningConfig from "@components/extension/auto-versioning/AutoVersioningConfig";
import {isAuthorized} from "@components/common/authorizations";
import ConfirmCommand from "@components/common/ConfirmCommand";
import {FaTrash} from "react-icons/fa";

const noBranch = {project: {}}

export default function AutoVersioningConfigView({branchId}) {

    const [reloadCount, setReloadCount] = useState(0)
    const reload = () => {
        setReloadCount(it => it + 1)
    }

    const {data, loading, finished} = useQuery(
        gql`
            query BranchAutoVersioning($id: Int!) {
                branch(id: $id) {
                    id
                    name
                    project {
                        id
                        name
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    autoVersioningConfig {
                        configurations {
                            sourceProject
                            sourceBranch
                            sourcePromotion
                            autoApprovalMode
                            autoApproval
                            targetPath
                            targetPropertyType
                            targetProperty
                            targetPropertyRegex
                            targetRegex
                            versionSource
                            postProcessing
                            postProcessingConfig
                            versionRule
                            versionRuleConfig
                            qualifier
                            upgradeBranchPattern
                            validationStamp
                            backValidation
                            prTitleTemplate
                            prBodyTemplateFormat
                            prBodyTemplate
                            buildLinkCreation
                            reviewers
                            notifications {
                                scope
                                channel
                                config
                                notificationTemplate
                            }
                            additionalPaths {
                                path
                                propertyType
                                regex
                                property
                                propertyRegex
                                versionSource
                            }
                            cronSchedule
                            disabled
                            pushMode
                        }
                    }
                }
            }
        `,
        {
            variables: {id: branchId},
            deps: [branchId, reloadCount],
            condition: !!branchId,
            dataFn: data => data.branch,
        }
    )
    const branch = data ?? noBranch

    const commands = []
    if (data) {
        if (isAuthorized(branch, 'branch', 'config')) {
            commands.push(
                <ConfirmCommand
                    key="delete"
                    icon={<FaTrash/>}
                    text="Delete"
                    confirmTitle="Removing the auto-versioning"
                    confirmText="Do you really want to remove the complete auto-versioning configuration for this branch?"
                    confirmOkText="Confirm deletion"
                    gqlQuery={
                        gql`
                            mutation DeleteAutoVersioning($id: Int!) {
                                deleteAutoVersioningConfig(input: {branchId: $id}) {
                                    errors {
                                        message                                            
                                    }
                                }
                            }
                        `
                    }
                    gqlVariables={{id: Number(branchId)}}
                    gqlUserNode="deleteAutoVersioningConfig"
                    onSuccess={reload}
                />
            )
        }
        commands.push(
            <CloseCommand key="close" href={branchUri(branch)}/>
        )
    }

    // A deletion shows the page as loading until the reload it triggers has started
    const [deleting, setDeleting] = useState(false)
    if (deleting && loading) {
        setDeleting(false)
    }

    const onDeleteConfig = (index) => {
        const newConfigurations = branch.autoVersioningConfig.configurations.filter((_, i) => i !== index)
        setDeleting(true)
        callGraphQL({
            query: gql`
                mutation UpdateAutoVersioningConfig(
                    $id: Int!,
                    $configurations: [AutoVersioningSourceConfigInput!]!,
                ) {
                    setAutoVersioningConfig(input: {
                        branchId: $id,
                        configurations: $configurations,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {
                id: branchId,
                configurations: newConfigurations,
            },
        }).finally(reload)
    }

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Auto-versioning")}
            </Head>
            <Skeleton loading={loading || !finished || deleting} active>
                <MainPage
                    title="Auto-versioning"
                    breadcrumbs={downToBranchBreadcrumbs({branch})}
                    commands={commands}
                >
                    <AutoVersioningConfig
                        branch={branch}
                        config={branch.autoVersioningConfig}
                        onDeleteConfig={onDeleteConfig}
                    />
                </MainPage>
            </Skeleton>
        </>
    )
}