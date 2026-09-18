import {gql} from "graphql-request"
import {gqlBuildJourneyData} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * The build page's Environments cell, in one query.
 *
 * One field, `Build.journey`, rather than the three the cell used to stitch together
 * (`slots`, their `pipelines(buildId:)` and their `lastDeployedPipeline`): those three can disagree
 * about the same slot, and a strip has room for exactly one state per environment.
 */
export const gqlBuildJourney = gql`
    query BuildJourney($buildId: Int!) {
        build(id: $buildId) {
            id
            journey {
                ...BuildJourneyData
            }
        }
    }
    ${gqlBuildJourneyData}
`

/**
 * Where a build is deployed right now, for the surfaces which have room for that and no more.
 *
 * `currentDeployments` rather than `journey`: the build search table and `ProjectPromotionWidget`
 * ask it of every row, and the journey of a build is every slot of its project - eligible and
 * refusing ones included - which is a paragraph per row and a far heavier question for the server to
 * answer twenty times over. The column is titled "Deployments" and that is what it draws.
 *
 * The `qualifier` argument is **omitted**, which is the field's "any qualifier", so a project with a
 * `canary` slot beside its plain production one gets a chip for each. Passing `qualifier: ""` - which
 * is what this column's predecessor did - filters on the *default* qualifier and silently drops every
 * deployment into a qualified slot (#1731).
 */
export const gqlBuildCurrentDeployments = gql`
    query BuildCurrentDeployments($buildId: Int!) {
        build(id: $buildId) {
            id
            currentDeployments {
                id
                number
                status
                slot {
                    id
                    qualifier
                    environment {
                        id
                        name
                        order
                        image
                    }
                    project {
                        id
                        name
                    }
                }
            }
        }
    }
`
