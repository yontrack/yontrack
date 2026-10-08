/**
 * Helpers for the actor of a signature (#2025): the agent behind what was done, `null` for a
 * person. See `ActorBadge`.
 */

/**
 * The fields of a `SignatureActor`, to select under any field typed `SignatureActor` - the `actor`
 * of a `Signature` (`creation { user time ${gqlSignatureActorFields} }`), of a `SlotPipelineChange`,
 * and of the data and the override of an admission rule.
 *
 * A plain selection rather than a GraphQL fragment: it is interpolated in queries which already
 * define fragments of their own, and the same fragment defined twice in one document is refused by
 * the server.
 */
export const gqlSignatureActorFields = `
    actor {
        kind
        agent
        displayName
        tool
        owner
        sessionId
        sessionLink
    }
`

/**
 * Whether something was done by an agent.
 *
 * @param signature Anything with an `actor` - a `Signature`, a `SlotPipelineChange`...
 */
export const isAgentSignature = (signature) => !!signature?.actor

/**
 * "Claude, owned by alice@example.com" - what the badge of an agent says.
 *
 * @param actor `SignatureActor`
 */
export const agentText = (actor) => `${actor.displayName}, owned by ${actor.owner}`

/**
 * "by agent Claude, owned by alice@example.com" - the accessible name of the badge of an agent,
 * which says in words that it is an agent: the robot icon says it to the eye only.
 *
 * @param actor `SignatureActor`
 * @param prefix Optional text before the agent ("by")
 */
export const agentAccessibleName = (actor, prefix) =>
    `${prefix ? `${prefix} ` : ''}agent ${agentText(actor)}`
