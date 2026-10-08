import {Tag, Tooltip} from "antd";
import {FaRobot} from "react-icons/fa";
import {agentAccessibleName, agentText} from "@components/common/actors/actors";

/**
 * Who did something - a person, or an agent (#2032).
 *
 * The one component to render the user of a signature with, wherever an actor shows: build,
 * validation run status, promotion run, deployment change, admission rule data or override, event.
 *
 * - **A person** is rendered exactly as before: the user name, as plain text, nothing added.
 * - **An agent** is a tag with a robot icon, "Claude, owned by alice@example.com". Its tooltip gives
 *   the agent's identifier, its owner, its tool and its session.
 *
 * Accessible: the robot icon is hidden from assistive technologies, and the tag carries a text
 * alternative saying "agent" in words - "by agent Claude, owned by alice@example.com" - so that
 * neither the colour nor the icon is the only cue. When the agent gave a link to its session, the
 * tag itself is the link, opened in a new tab without access to its opener: a link inside a tooltip
 * cannot be reached from the keyboard. This is the shape of `CommitAssistedMarker` (#2021).
 *
 * @param signature Anything carrying a `user` and an `actor`: a `Signature` (the `creation` of a
 *                  build, a promotion run, a validation run status), a `SlotPipelineChange`, the
 *                  `data` or the `override` of an admission rule. `actor` is the `SignatureActor`,
 *                  `null` for a person - select it with `gqlSignatureActorFields`. A query not
 *                  selecting it renders every actor as a person.
 * @param prefix Text before the actor, for a person as for an agent - "by" renders "by alice", or
 *               the badge "by Claude, owned by alice@example.com"
 * @param hideHuman Renders nothing for a person - for the places which did not show the user
 *                  before, and must now say that an agent did it (build header, build rows)
 * @param link Whether the badge of an agent links to its session, when there is one. Pass `false`
 *             when the badge is rendered inside another link.
 * @param testId Test ID of the badge of an agent
 */
export default function ActorBadge({signature, prefix, hideHuman = false, link = true, testId}) {
    const actor = signature?.actor

    if (!actor) {
        if (hideHuman || !signature?.user) {
            return null
        }
        return <>{prefix ? `${prefix} ` : ''}{signature.user}</>
    }

    const label = agentAccessibleName(actor, prefix)
    const sessionLink = link ? actor.sessionLink : null

    const tooltip = (
        <>
            <div>Agent {actor.agent}</div>
            <div>Owned by {actor.owner}</div>
            {actor.tool && <div>Tool: {actor.tool}</div>}
            {actor.sessionId && <div>Session: {actor.sessionId}</div>}
            {sessionLink && <div>Opens the agent session in a new tab.</div>}
        </>
    )

    // The name is on the link when there is one, else on the tag itself, exposed as an image so that
    // the name is read rather than the bare text
    const labelling = sessionLink ? {} : {role: 'img', 'aria-label': label}

    const tag = (
        <Tag
            className="ot-actor-badge"
            color="purple"
            variant="outlined"
            icon={<FaRobot aria-hidden="true"/>}
            style={{marginInlineEnd: 0}}
            data-testid={testId}
            {...labelling}
        >
            {prefix ? `${prefix} ` : ''}{agentText(actor)}
        </Tag>
    )

    return (
        <Tooltip title={tooltip}>
            {
                sessionLink ?
                    <a href={sessionLink} target="_blank" rel="noopener noreferrer" aria-label={label}>
                        {tag}
                    </a> :
                    tag
            }
        </Tooltip>
    )
}
