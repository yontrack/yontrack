/**
 * Model of the registered agents, without any call to the server.
 */

/**
 * Tools offered when registering or editing an agent. The tool is stored as free text, so another
 * name can always be typed.
 */
export const AGENT_TOOLS = ["Claude Code", "Codex", "Copilot", "Devin", "Other"]

/**
 * Pattern of the slug of an agent, as checked by the server.
 */
export const AGENT_SLUG_PATTERN = /^[a-z0-9-]{1,32}$/

/**
 * Last time any token of an agent was used, or `undefined` when none ever was.
 */
export const agentLastUsed = (agent) =>
    (agent.tokens ?? [])
        .map(token => token.lastUsed)
        .filter(it => it)
        .sort()
        .at(-1)

/**
 * Slug of an agent from its `<slug>[agent]` identifier.
 */
export const agentSlug = (agent) => agent.email?.replace(/\[agent]$/, '')
