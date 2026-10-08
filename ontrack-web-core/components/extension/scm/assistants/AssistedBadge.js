import {Tag, Tooltip} from "antd";
import {FaQuestionCircle, FaRobot} from "react-icons/fa";
import {assistedChangeSummary} from "@components/extension/scm/assistants/assistants";

const UNKNOWN_WITHOUT_REASON = "Whether the commits of this build were assisted could not be computed."

/**
 * Whether the commits of a build were written with assistants (#2032), from its assisted change
 * (#2028).
 *
 * - **Assisted** - a purple tag with a robot icon, "Assisted". Its tooltip gives "3 of 12 commits,
 *   by Claude Code" and links to the agent sessions behind the commits.
 * - **Unknown** - a neutral tag with a question mark, "Assisted: unknown". Its tooltip gives the
 *   reason.
 * - **Not assisted**, or not computed yet - nothing: most builds are not assisted, and a badge on
 *   every one of them would be noise.
 *
 * Accessible: the icons are hidden from assistive technologies, the tag says what it means in words
 * and carries a text alternative with the counts, the assistants or the reason - so that neither
 * the colour nor the icon is the only cue. The tag can be focused, which shows its tooltip. The
 * session links of its tooltip are a shortcut for the mouse: the build's *Assisted change* property
 * lists them as plain links.
 *
 * Phone-safe: it is one short tag, and renders in the mobile build screen's header.
 *
 * @param assistedChange `Build.assistedChange` - select it with `gqlAssistedChangeFields`. Null
 *                       when it has been neither computed nor set yet.
 * @param testId Test ID of the tag
 */
export default function AssistedBadge({assistedChange, testId}) {
    if (!assistedChange) {
        return null
    }

    if (assistedChange.basis === 'UNKNOWN') {
        const reason = assistedChange.unknownReason
        return (
            <Tooltip title={reason ? `Unknown: ${reason}` : UNKNOWN_WITHOUT_REASON}>
                <Tag
                    className="ot-assisted-badge"
                    icon={<FaQuestionCircle aria-hidden="true"/>}
                    style={{marginInlineEnd: 0}}
                    data-testid={testId}
                    tabIndex={0}
                    role="img"
                    aria-label={reason ? `Assisted: unknown, ${reason}` : "Assisted: unknown"}
                >
                    Assisted: unknown
                </Tag>
            </Tooltip>
        )
    }

    if (!assistedChange.assisted) {
        return null
    }

    const summary = assistedChangeSummary(assistedChange)
    const sessionLinks = assistedChange.sessionLinks ?? []

    const tooltip = (
        <>
            <div>{summary}</div>
            {
                sessionLinks.map((link, index) => (
                    <div key={link}>
                        <a href={link} target="_blank" rel="noopener noreferrer">
                            {sessionLinks.length > 1 ? `Agent session ${index + 1}` : 'Agent session'}
                        </a>
                    </div>
                ))
            }
        </>
    )

    return (
        <Tooltip title={tooltip}>
            <Tag
                className="ot-assisted-badge"
                color="purple"
                variant="outlined"
                icon={<FaRobot aria-hidden="true"/>}
                style={{marginInlineEnd: 0}}
                data-testid={testId}
                tabIndex={0}
                role="img"
                aria-label={summary ? `Assisted: ${summary}` : "Assisted"}
            >
                Assisted
            </Tag>
        </Tooltip>
    )
}
