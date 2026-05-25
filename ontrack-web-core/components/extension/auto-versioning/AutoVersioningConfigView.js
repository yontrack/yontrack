import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import {Skeleton} from "antd";
import MainPage from "@components/layouts/MainPage";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import AutoVersioningConfig from "@components/extension/auto-versioning/AutoVersioningConfig";
import {isAuthorized} from "@components/common/authorizations";
import ConfirmCommand from "@components/common/ConfirmCommand";
import {FaTrash} from "react-icons/fa";

export default function AutoVersioningConfigView({branchId}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [branch, setBranch] = useState({project: {}})
    const [commands, setCommands] = useState([])

    const [reloadCount, setReloadCount] = useState(0)
    const reload = () => {
        setReloadCount(it => it + 1)
    }

    useEffect(() => {
        if (client && branchId) {
            setLoading(true)
            client.request(
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
                {id: branchId}
            ).then(data => {
                setBranch(data.branch)

                const commands = []
                if (isAuthorized(data.branch, 'branch', 'config')) {
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
                    <CloseCommand key="close" href={branchUri(data.branch)}/>
                )
                setCommands(commands)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, branchId, reloadCount])

    const onDeleteConfig = (index) => {
        const newConfigurations = branch.autoVersioningConfig.configurations.filter((_, i) => i !== index)
        setLoading(true)
        client.request(
            gql`
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
            {
                id: branchId,
                configurations: newConfigurations,
            }
        ).finally(reload)
    }

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Auto-versioning")}
            </Head>
            <Skeleton loading={loading} active>
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