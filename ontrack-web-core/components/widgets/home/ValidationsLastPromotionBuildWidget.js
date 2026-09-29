import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import {useContext, useEffect} from "react";
import LoadingContainer from "@components/common/LoadingContainer";
import {gqlValidationRunTableContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRunTable from "@components/validationRuns/ValidationRunTable";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import BuildLink from "@components/builds/BuildLink";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import BranchLink from "@components/branches/BranchLink";
import {Divider} from "antd";
import ProjectLink from "@components/projects/ProjectLink";

const NO_RUNS = []

export default function ValidationsLastPromotionBuildWidget({title, project, branch, promotion, validations}) {

    const {data, loading} = useQuery(
        gql`
            ${gqlValidationRunTableContent}
            query ValidationsLastPromotionBuild(
                $project: String!,
                $branch: String!,
                $promotion: String!,
                $validations: [String!],
            ) {
                promotionLevelByName(project: $project, branch: $branch, name: $promotion) {
                    id
                    name
                    image
                    promotionRunsPaginated(size: 1) {
                        pageItems {
                            build {
                                id
                                name
                                branch {
                                    id
                                    name
                                    displayName
                                    project {
                                        id
                                        name
                                    }
                                }
                                releaseProperty {
                                    value
                                }
                                validations(validationStamps: $validations) {
                                    validationRuns {
                                        ...ValidationRunTableContent
                                    }
                                }
                            }
                        }
                    }
                }
            }
        `,
        {
            variables: {
                project,
                branch,
                promotion,
                validations,
            },
            deps: [project, branch, promotion],
            condition: !!(project && branch && promotion),
            dataFn: data => {
                const pl = data.promotionLevelByName
                const build = pl?.promotionRunsPaginated?.pageItems?.[0]?.build
                const runs = []
                build?.validations?.forEach(validation => {
                    runs.push(...validation.validationRuns)
                })
                return {
                    promotionLevel: pl,
                    build,
                    runs,
                }
            },
        }
    )
    const promotionLevel = data?.promotionLevel
    const build = data?.build
    const runs = data?.runs ?? NO_RUNS

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        if (title) {
            setTitle(title)
        } else if (promotionLevel && build) {
            setTitle(
                <>
                    Validations for build <BuildLink build={build}/> promoted
                    to <PromotionLevelLink
                    promotionLevel={promotionLevel}/>
                    <Divider orientation="vertical"/>
                    <BranchLink branch={build.branch}/>
                    <Divider orientation="vertical"/>
                    <ProjectLink project={build.branch.project}/>
                </>
            )
        } else {
            setTitle("Validations of last promoted build (no build found)")
        }
    }, [title, promotionLevel, build])

    return (
        <>
            <LoadingContainer loading={loading}>
                <ValidationRunTable
                    validationRuns={runs}
                />
            </LoadingContainer>
        </>
    )
}