import LoadingContainer from "@components/common/LoadingContainer";
import {useContext, useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {gqlValidationRunTableContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRunTable from "@components/validationRuns/ValidationRunTable";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import BranchLink from "@components/branches/BranchLink";
import {Divider, Space} from "antd";
import ProjectLink from "@components/projects/ProjectLink";
import Link from "next/link";
import {branchUri} from "@components/common/Links";
import {FaExternalLinkAlt} from "react-icons/fa";

export default function LastValidationsForBranchWidget({title, project, branch, validations, displayPromotions}) {

    const client = useGraphQLClient()
    const [loading, setLoading] = useState(false)

    const [loadedBranch, setLoadedBranch] = useState()
    const [runs, setRuns] = useState([])

    useEffect(() => {
        if (client && project && branch && validations && validations.length > 0) {

            const loadData = async () => {
                setLoading(true)
                try {
                    const data = await client.request(
                        gql`
                            ${gqlValidationRunTableContent}
                            query LastValidationsForBranchWidget(
                                $project: String!,
                                $branch: String!,
                                $validations: [String!]!,
                                $displayPromotions: Boolean!,
                            ) {
                                branch: branchByName(project: $project, name: $branch) {
                                    id
                                    name
                                    displayName
                                    project {
                                        id
                                        name
                                    }
                                    validationStatuses(names: $validations) {
                                        ...ValidationRunTableContent
                                        build {
                                            id
                                            name
                                            releaseProperty {
                                                value
                                            }
                                            promotionRuns(lastPerLevel: true) @include(if: $displayPromotions) {
                                                id
                                                creation {
                                                    time
                                                }
                                                promotionLevel {
                                                    id
                                                    name
                                                    image
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        `,
                        {
                            project,
                            branch,
                            validations,
                            displayPromotions: displayPromotions === true,
                        }
                    )
                    setLoadedBranch(data.branch)
                    if (data.branch) {
                        setRuns(data.branch.validationStatuses)
                    }
                } finally {
                    setLoading(false)
                }
            }

            // noinspection JSIgnoredPromiseFromCall
            loadData()

            // setLoading(true)
        }
    }, [client, project, branch])
    //
    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        if (title) {
            if (loadedBranch) {
                setTitle(
                    <Space>
                        {title}
                        <Link href={branchUri(loadedBranch)}>
                            <FaExternalLinkAlt/>
                        </Link>
                    </Space>
                )
            } else {
                setTitle(title)
            }
        } else if (loadedBranch) {
            setTitle(
                <>
                    Validations for <BranchLink branch={loadedBranch}/>
                    <Divider orientation="vertical"/>
                    <ProjectLink project={loadedBranch.project}/>
                </>
            )
        } else {
            setTitle("Last validations of branch (no branch found)")
        }
    }, [title, loadedBranch])

    return (
        <>
            <LoadingContainer loading={loading}>
                <ValidationRunTable
                    validationRuns={runs}
                    displayBuild={true}
                    displayPromotionRuns={displayPromotions === true}
                />
            </LoadingContainer>
        </>
    )
}