import {gql} from "graphql-request";
import {gqlSignatureActorFields} from "@components/common/actors/actors";

/**
 * The *Agents* section of a build page (#2033): what agents had to do with the build, in two lists
 * which are never merged nor linked - a commit trailer names a tool, never a registered agent.
 *
 * - **Assisted by**, from git: the build's assisted change (#2028).
 * - **Actions by agents**, from the event log: the build's events whose actor is an agent
 *   (`Build.agentActions`).
 */

/**
 * Size of a page of the actions by agents.
 */
export const AGENT_ACTIONS_PAGE_SIZE = 10

/**
 * What the build page selects on its build to know whether the *Agents* section has anything to
 * show: the first action by an agent, if any. The assisted change is selected anyway, for the
 * badge of the header (`gqlAssistedChangeFields`).
 *
 * A page of one, and its next page: an action touching a project the user cannot see is left out
 * of a page, which can then be empty while the next one is not.
 */
export const gqlBuildAgentActionsProbe = `
    agentActionsProbe: agentActions(offset: 0, size: 1) {
        pageInfo {
            nextPage {
                offset
            }
        }
        pageItems {
            id
        }
    }
`

/**
 * Whether a page of actions by agents shows there is at least one.
 *
 * @param page `EventAgentActionPaginated`
 */
export const hasAgentActions = (page) =>
    (page?.pageItems?.length ?? 0) > 0 || !!page?.pageInfo?.nextPage

/**
 * Whether the *Agents* section of a build has something to show: its commits were assisted, or an
 * agent acted on it. A build whose assisted change is unknown and on which no agent acted shows no
 * section - the header badge already says it is unknown.
 *
 * @param build Build selected with `gqlAssistedChangeFields` and `gqlBuildAgentActionsProbe`
 */
export const hasAgentsSection = (build) =>
    !!build?.assistedChange?.assisted || hasAgentActions(build?.agentActionsProbe)

/**
 * The content of the *Agents* section: the assisted change of the build, with the build it was
 * computed from, and a page of the actions by agents, newest first.
 */
export const gqlBuildAgents = gql`
    query BuildAgents($id: Int!, $offset: Int!, $size: Int!) {
        build(id: $id) {
            id
            assistedChange {
                assisted
                basis
                unknownReason
                assistants
                assistedCommits
                totalCommits
                sessionLinks
                previousBuild {
                    id
                    name
                    displayName
                }
            }
            agentActions(offset: $offset, size: $size) {
                pageInfo {
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    id
                    time
                    user
                    ${gqlSignatureActorFields}
                    message
                    eventType {
                        id
                    }
                }
            }
        }
    }
`

/**
 * How the assisted change was obtained, in words: computed from the change log, set by the CI, or
 * unknown with its reason.
 *
 * @param assistedChange `Build.assistedChange`
 */
export const assistedBasisText = (assistedChange) => {
    switch (assistedChange?.basis) {
        case 'COMPUTED':
            return "Computed by Yontrack from the change log"
        case 'SET_BY_CI':
            return "Set by the CI"
        case 'UNKNOWN':
            return assistedChange.unknownReason ? `Unknown: ${assistedChange.unknownReason}` : "Unknown"
        default:
            return "Not computed yet"
    }
}

/**
 * "3 of 12 commits since 41" - the share of assisted commits in the change of the build. Null when
 * the counts are not known, which happens when the CI set the value without them.
 *
 * @param assistedChange `Build.assistedChange`, with its `previousBuild`
 */
export const assistedCommitsText = (assistedChange) => {
    const {assistedCommits, totalCommits, previousBuild} = assistedChange ?? {}
    if (assistedCommits === null || assistedCommits === undefined || totalCommits === null || totalCommits === undefined) {
        return null
    }
    const commits = `${assistedCommits} of ${totalCommits} ${totalCommits === 1 ? 'commit' : 'commits'}`
    return previousBuild ? `${commits} since ${previousBuild.displayName || previousBuild.name}` : commits
}
