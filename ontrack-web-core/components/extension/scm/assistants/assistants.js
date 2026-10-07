/**
 * Helpers for the assistants (agent kinds) of the commits of a change log, as read from
 * `SCMCommit.assistants`.
 */

/**
 * Names of assistants, comma-separated.
 *
 * @param assistants List of `{name, sessionLink}`
 */
export const assistantNames = (assistants) => (assistants ?? []).map(it => it.name).join(', ')

/**
 * "Assisted by Claude Code" — the accessible name of the marker of an assisted commit.
 *
 * @param assistants List of `{name, sessionLink}`
 */
export const assistedByText = (assistants) => `Assisted by ${assistantNames(assistants)}`

/**
 * First session link of a list of assistants, if any.
 *
 * @param assistants List of `{name, sessionLink}`
 */
export const assistantsSessionLink = (assistants) => (assistants ?? []).find(it => it.sessionLink)?.sessionLink

/**
 * Number of commits written with at least one assistant.
 *
 * @param commits Commits of the change log, each one as `{commit: {assistants}}`
 */
export const countAssistedCommits = (commits) =>
    (commits ?? []).filter(it => it?.commit?.assistants?.length > 0).length

/**
 * "3 of 12 commits assisted"
 *
 * @param assisted Number of assisted commits
 * @param total Total number of commits
 */
export const assistedCountText = (assisted, total) =>
    `${assisted} of ${total} ${total === 1 ? 'commit' : 'commits'} assisted`
