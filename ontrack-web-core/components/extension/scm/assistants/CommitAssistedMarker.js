import {Tag, Tooltip} from "antd";
import {FaRobot} from "react-icons/fa";
import {assistantsSessionLink, assistedByText} from "@components/extension/scm/assistants/assistants";

/**
 * Small "assisted" marker shown next to the message of a commit written with one or more
 * assistants (agent kinds).
 *
 * The colour only doubles the icon and the word "assisted", it never carries the meaning alone.
 * The marker's accessible name says which assistants ("Assisted by Claude Code"): on the link when
 * the commit carries an agent session link, else on the tag itself, exposed as an image so that
 * the name is read rather than the bare word. The tooltip lists the assistants.
 *
 * When a session link exists, the marker opens it in a new tab.
 *
 * @param assistants List of `{name, sessionLink}`; nothing is rendered when it is empty
 * @param testId Test ID
 */
export default function CommitAssistedMarker({assistants, testId}) {
    if (!assistants || assistants.length === 0) {
        return null
    }

    const label = assistedByText(assistants)
    const sessionLink = assistantsSessionLink(assistants)

    const tooltip = (
        <>
            {
                assistants.length === 1 ?
                    <div>{label}</div> :
                    <>
                        <div>Assisted by:</div>
                        <ul style={{margin: 0, paddingLeft: 16}}>
                            {assistants.map(assistant => <li key={assistant.name}>{assistant.name}</li>)}
                        </ul>
                    </>
            }
            {sessionLink && <div>Opens the agent session in a new tab.</div>}
        </>
    )

    const labelling = sessionLink ? {} : {role: 'img', 'aria-label': label}

    const tag = (
        <Tag
            color="purple"
            variant="outlined"
            icon={<FaRobot aria-hidden="true"/>}
            style={{marginInlineStart: 8, marginInlineEnd: 0}}
            data-testid={testId}
            {...labelling}
        >
            assisted
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
