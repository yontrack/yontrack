/**
 * Agent criteria of the build filters (#2036): whether the build was assisted, and which actor
 * created it. Both are optional, and are sent as they are to the `assisted` and `actor` fields of
 * `StandardBuildFilter` and `BuildSearchForm`.
 */

/**
 * Suffix of the identifier of an agent, `<slug>[agent]`.
 */
const AGENT_SUFFIX = "[agent]"

/**
 * Pattern of the identifier of an agent.
 */
const AGENT_IDENTIFIER = /^[a-z0-9-]{1,32}\[agent]$/

/**
 * Options of the assisted criterion.
 */
export const buildAssistedOptions = [
    {value: "YES", label: "Assisted"},
    {value: "NO", label: "Not assisted"},
    {value: "UNKNOWN", label: "Unknown"},
]

/**
 * Options of the actor criterion: the builds of the persons, those of any agent, then each
 * registered agent the user can see, by its identifier.
 *
 * The agents a user sees are only their own, unless they are an administrator: the identifier of any
 * other agent can be typed, and is offered as an option of its own while it is being typed.
 *
 * @param agents Registered agents, with their `email` (their identifier) and `fullName`
 * @param search Text being typed in the control, if any
 * @param value Selected actor, kept as an option even when not among the agents
 */
export const buildActorOptions = (agents = [], search = "", value = undefined) => {
    const options = [
        {value: "HUMAN", label: "Humans"},
        {value: "AGENT", label: "Agents"},
    ]
    const identifiers = new Set(agents.map(agent => agent.email))
    const agentOptions = agents.map(agent => ({
        value: agent.email,
        label: `${agent.fullName} (${agent.email})`,
    }))
    const extra = [value, typedAgentIdentifier(search)]
        .filter(identifier => identifier && identifier !== "HUMAN" && identifier !== "AGENT")
        .filter((identifier, index, all) => all.indexOf(identifier) === index)
        .filter(identifier => !identifiers.has(identifier))
    extra.forEach(identifier => agentOptions.push({value: identifier, label: identifier}))
    if (agentOptions.length > 0) {
        options.push({
            label: "Agent",
            title: "Agent",
            options: agentOptions,
        })
    }
    return options
}

/**
 * The identifier of an agent being typed, if the text is one: the suffix is added when missing.
 *
 * @param search Text being typed
 * @returns {string|undefined} The identifier, `<slug>[agent]`, in lowercase
 */
export const typedAgentIdentifier = (search) => {
    const text = search?.trim()?.toLowerCase()
    if (!text) return undefined
    const identifier = text.endsWith(AGENT_SUFFIX) ? text : `${text}${AGENT_SUFFIX}`
    return AGENT_IDENTIFIER.test(identifier) ? identifier : undefined
}
