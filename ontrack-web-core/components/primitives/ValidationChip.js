// @ts-check

import {theme, Typography} from "antd"
import Link from "next/link"
import EntityIcon from "@components/primitives/EntityIcon"
import StatePill from "@components/primitives/StatePill"
import ValidationStampGlyph from "@components/primitives/ValidationStampGlyph"
import {NONE_STATUS_ID} from "@components/validationRuns/ValidationRunStatusConfig"
import {useValidationStateColors} from "@components/primitives/validationStateColors"

/**
 * A validation stamp and the state of its run, read as one thing.
 *
 * The stamp's identity stays put and the STATE IS CARRIED IN COLOUR - the chip's
 * outline and its state pill are tinted by the run status, while the stamp icon
 * beside them never changes. That way a column of chips scans by hue, and a
 * stamp does not appear to become a different stamp when it starts failing.
 *
 * Colour alone is never the whole story: the pill spells the status out, and the
 * chip's accessible name is always "STAMP — Status", present whether or not the
 * pill is rendered. That is the same contract `ValidationRunStatusIcon` honours,
 * and it is why `displayStatus={false}` still names the state.
 *
 * WHERE THE HUES COME FROM. Radius, type and the neutral surfaces are Ant Design
 * tokens; the status hues come from `ValidationRunStatusConfig` by way of
 * `validationStateColors`, which explains why they are not tokens.
 */

/**
 * @param {Object} props
 * @param {{id: any, name: string, image?: boolean, dataType?: {descriptor?: {id?: string}}}} [props.validationStamp]
 * @param {{id: string, name?: string}} [props.statusID] The run's last status.
 *   Absent means no run yet, which is drawn neutrally and never as an outcome.
 * @param {boolean} [props.displayText] Show the stamp's name.
 * @param {boolean} [props.displayStatus] Show the status pill. The state is
 *   still announced when this is off.
 * @param {number} [props.size] Stamp icon box size in px.
 * @param {string} [props.href]
 * @param {() => void} [props.onClick]
 * @param {string} [props.id] Rendered as `data-testid`.
 */
export default function ValidationChip({
                                           validationStamp,
                                           statusID,
                                           displayText = true,
                                           displayStatus = true,
                                           size = 22,
                                           href,
                                           onClick,
                                           id,
                                       }) {

    const {token} = theme.useToken()
    const statusId = statusID?.id ?? NONE_STATUS_ID
    const colors = useValidationStateColors(statusId)

    if (!validationStamp) return null

    // Never leave the state unnamed: an unknown status announces its raw id, and
    // no run at all says so in words rather than being silently neutral.
    const hasRun = statusId !== NONE_STATUS_ID
    const statusName = hasRun ? (statusID?.name || statusId) : 'No run'

    const chip = (
        <span
            data-testid={id}
            data-status={statusId}
            role="img"
            aria-label={`${validationStamp.name} — ${statusName}`}
            onClick={onClick}
            className={onClick ? "ot-action" : undefined}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: token.marginXXS,
                padding: `${token.paddingXXS / 2}px ${token.paddingXS}px ${token.paddingXXS / 2}px ${token.paddingXXS / 2}px`,
                borderRadius: token.borderRadiusLG,
                border: `1px solid ${colors.border}`,
                backgroundColor: token.colorBgContainer,
                fontFamily: token.fontFamily,
                fontSize: token.fontSize,
                lineHeight: 1,
                maxWidth: '100%',
            }}
        >
            <EntityIcon
                kind="validationStamp"
                entity={validationStamp}
                size={size}
                // The stamp's identity, not its outcome. A data-type glyph is a
                // better fallback here than initials because a chip is read in a
                // list where two-letter tiles all start to look alike.
                fallback={<ValidationStampGlyph validationStamp={validationStamp} size={size}/>}
            />
            {
                displayText &&
                <Typography.Text
                    ellipsis={true}
                    style={{color: token.colorText, lineHeight: 1}}
                >
                    {validationStamp.name}
                </Typography.Text>
            }
            {
                displayStatus &&
                <StatePill
                    id={id ? `${id}-status` : undefined}
                    text={statusName}
                    colors={colors}
                />
            }
        </span>
    )

    return href ? <Link href={href} style={{color: 'inherit'}}>{chip}</Link> : chip
}
