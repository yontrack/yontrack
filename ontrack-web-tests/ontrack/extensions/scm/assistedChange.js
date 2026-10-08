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
 */
export const setAssistedChange = async (build, {
    assistants = ["Claude Code"],
    assistedCommits = null,
    totalCommits = null,
    sessionLinks = [],
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
            ) {
                setBuildAssistedChangePropertyById(input: {
                    id: $id,
                    assistants: $assistants,
                    assistedCommits: $assistedCommits,
                    totalCommits: $totalCommits,
                    sessionLinks: $sessionLinks,
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
        }
    )
}
