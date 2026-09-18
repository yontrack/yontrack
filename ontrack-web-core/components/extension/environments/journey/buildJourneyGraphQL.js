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
