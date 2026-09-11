"use client"

/**
 * Where builds are currently deployed, asked for **separately** from everything
 * else the screen needs.
 *
 * This is not a style choice. `Build.currentDeployments` is contributed by the
 * environments extension, and `GQLBuildSlotPipelinesFieldContributor` only
 * registers it when `environmentsLicense.environmentFeatureEnabled` - so on an
 * instance without that licence the field is **absent from the schema**, not
 * merely empty. A query naming it then fails *validation*, which fails the whole
 * document: no identity, no promotions, no validations, no action buttons. A
 * mobile screen would go from "no deployments shown" to "Could not load the
 * build" on the strength of a licence it never mentions.
 *
 * The desktop UI does not hit this because its environments panel is its own
 * component with its own query - only that panel breaks. Splitting the query is
 * how the mobile screens get the same isolation: this one may fail on its own,
 * and the screen around it carries on.
 *
 * `error` is therefore an expected state, not a bug. Callers show what they can
 * and say the rest is unavailable.
 */

import {gql} from "graphql-request"
import {useQuery} from "@components/services/GraphQL"

/** The slot fields a deployment badge or row needs. */
const DEPLOYMENT_FIELDS = `
    id
    end
    slot {
        id
        environment {
            id
            name
        }
    }
`

/**
 * One build's current deployments.
 *
 * @param {string|number} id The build's id.
 * @returns {{deployments: Array, unavailable: boolean}} `unavailable` means the
 *   instance has no environments feature - not that the build is deployed
 *   nowhere, which is a different and sayable thing.
 */
export function useMobileBuildDeployments(id) {
    const query = useQuery(
        gql`
            query MobileBuildDeployments($id: Int!) {
                build(id: $id) {
                    id
                    currentDeployments {
                        ${DEPLOYMENT_FIELDS}
                    }
                }
            }
        `,
        {variables: {id: Number(id)}, deps: [id]}
    )
    return {
        deployments: query.data?.build?.currentDeployments ?? [],
        unavailable: Boolean(query.error),
    }
}

/**
 * The current deployments of every build on one page of a branch, keyed by build
 * id.
 *
 * A map rather than a list because the caller already has the builds and only
 * needs to look each one up - and because a build with no deployments and a
 * build the query never reached both come back as "nothing", which is the right
 * answer for a badge strip either way.
 *
 * @param {string|number} branchId
 * @param {number} size The same page size the builds were asked for.
 * @param {Object} filter The build search, from `useMobileBuildFilter` - the
 *   whole thing, because its `input` and its `signature` are two halves of one
 *   fact and only ever travel together. It has to be the **same** search the
 *   builds were asked under: this query is a second page of the same list, and a
 *   page asked for under different terms holds different builds - so a filtered
 *   screen would look every build up in a map built from the *unfiltered* page
 *   and find nothing, losing every badge.
 * @returns {Record<string, Array>}
 */
export function useMobileBranchDeployments(branchId, size, filter) {
    const query = useQuery(
        gql`
            query MobileBranchDeployments($id: Int!, $size: Int!, $filter: StandardBuildFilter) {
                branch(id: $id) {
                    id
                    buildsPaginated(offset: 0, size: $size, filter: $filter) {
                        pageItems {
                            id
                            currentDeployments {
                                ${DEPLOYMENT_FIELDS}
                            }
                        }
                    }
                }
            }
        `,
        {
            // The signature and not the input itself: the input is a fresh object
            // on every render, and a dependency array holding it would refetch
            // for ever.
            variables: {id: Number(branchId), size, filter: filter.input},
            deps: [branchId, size, filter.signature],
        }
    )
    const items = query.data?.branch?.buildsPaginated?.pageItems ?? []
    return Object.fromEntries(items.map(item => [String(item.id), item.currentDeployments ?? []]))
}
