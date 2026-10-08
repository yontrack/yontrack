import {gql} from "graphql-request";
import {graphQLCallMutation} from "@ontrack/graphql";

/**
 * Sets the assisted change of a build, as the CI does (#2028).
 *
 * @param build Build, whose client is the one setting the property
 * @param assistants Names of the assistants
 * @param assistedCommits Number of assisted commits
 * @param totalCommits Number of commits
 * @param sessionLinks Links to the agent sessions
 * @param previousBuildId ID of the build the change is counted from, if any
 */
export const setAssistedChange = async (build, {
    assistants = ["Claude Code"],
    assistedCommits = null,
    totalCommits = null,
    sessionLinks = [],
    previousBuildId = null,
} = {}) => {
    await graphQLCallMutation(
        build.ontrack.connection,
        'setBuildAssistedChangePropertyById',
        gql`
            mutation SetAssistedChange(
                $id: Int!,
                $assistants: [String!],
                $assistedCommits: Int,
                $totalCommits: Int,
                $sessionLinks: [String!],
                $previousBuildId: Int,
            ) {
                setBuildAssistedChangePropertyById(input: {
                    id: $id,
                    assistants: $assistants,
                    assistedCommits: $assistedCommits,
                    totalCommits: $totalCommits,
                    sessionLinks: $sessionLinks,
                    previousBuildId: $previousBuildId,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            id: Number(build.id),
            assistants,
            assistedCommits,
            totalCommits,
            sessionLinks,
            previousBuildId: previousBuildId !== null ? Number(previousBuildId) : null,
        }
    )
}
