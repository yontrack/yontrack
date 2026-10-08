import {gql} from "graphql-request";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {registerAgent} from "@ontrack/agents";
import {setAssistedChange} from "@ontrack/extensions/scm/assistedChange";

/**
 * A build created, validated and promoted by an agent with a session, and whose commits are said by
 * the agent to be assisted - what the badges of agents (#2032) are drawn from.
 *
 * The agent is owned by the account the tests run as: an agent has its owner's rights, narrowed
 * by the agent policy, which leaves it creating builds, validating them, and promoting them to a
 * level which admits agents.
 *
 * @return `{agent, owner, session, build, run, promotionRun}` - the email of the `owner`, the
 * `build` as the agent sees it, the validation `run` and the `promotionRun` the agent made
 */
export const provisionAgentBuild = async (ontrack) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const promotionLevel = await branch.createPromotionLevel()
    await graphQLCallMutation(
        ontrack.connection,
        'setPromotionLevelAgentsAdmittedPropertyById',
        gql`
            mutation AgentsAdmitted($id: Int!) {
                setPromotionLevelAgentsAdmittedPropertyById(input: {id: $id, admitted: true}) {
                    errors {
                        message
                    }
                }
            }
        `,
        {id: Number(promotionLevel.id)}
    )

    const owner = (await graphQLCall(
        ontrack.connection,
        gql`{ user { account { email } } }`
    )).user.account.email

    const agent = await registerAgent(ontrack, {displayName: "Claude", tool: "Claude Code"})
    const session = {id: `session-${agent.id}`, link: `https://claude.ai/code/session-${agent.id}`}
    const asAgent = agent.client(session)

    const agentBranch = await asAgent.getBranchById(branch.id)
    const build = await agentBranch.createBuild()
    const run = await build.validate(validationStamp)
    const promotionRun = await build.promote(promotionLevel)
    await setAssistedChange(build, {
        assistants: ["Claude Code"],
        assistedCommits: 2,
        totalCommits: 3,
        sessionLinks: [session.link],
    })

    return {agent, owner, session, build, run, promotionRun}
}
