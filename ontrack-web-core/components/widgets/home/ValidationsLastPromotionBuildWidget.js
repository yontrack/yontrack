import {gql} from "graphql-request";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useContext, useEffect, useState} from "react";
import LoadingContainer from "@components/common/LoadingContainer";
import {gqlValidationRunTableContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRunTable from "@components/validationRuns/ValidationRunTable";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import BuildLink from "@components/builds/BuildLink";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import BranchLink from "@components/branches/BranchLink";
import {Divider} from "antd";
import ProjectLink from "@components/projects/ProjectLink";

export default function ValidationsLastPromotionBuildWidget({title, project, branch, promotion, validations}) {

    const client = useGraphQLClient()
    const [loading, setLoading] = useState(false)

    const [promotionLevel, setPromotionLevel] = useState()
    const [build, setBuild] = useState()
    const [runs, setRuns] = useState([])

    useEffect(() => {
        if (client && project && branch && promotion) {
            setLoading(true)
            client.request(
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
                    project,
                    branch,
                    promotion,
                    validations,
                }
            ).then(data => {
                const pl = data.promotionLevelByName
                if (pl) {
                    setPromotionLevel(pl)
                    const promotionRuns = pl.promotionRunsPaginated.pageItems
                    if (promotionRuns.length > 0) {
                        const build = promotionRuns[0].build
                        setBuild(build)
                        const runs = []
                        build.validations.forEach(validation => {
                            runs.push(...validation.validationRuns)
                        })
                        setRuns(runs)
                    }
                }
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, project, branch, promotion])

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