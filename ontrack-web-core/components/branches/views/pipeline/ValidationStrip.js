import {theme, Typography} from "antd";
import {useValidationStateColors} from "@components/primitives/validationStateColors";
import {NONE_STATUS_ID} from "@components/validationRuns/ValidationRunStatusConfig";

/**
 * One bar per validation stamp, coloured by the state of its most recent run.
 *
 * An aggregate BY POSITION, which is why the `branchViewVsGroups` grouping preference does not
 * apply here: the strip is already a summary, and grouping a summary by status would summarise a
 * summary. What DOES apply is the selected validation stamp filter - the branch's own, server-stored
 * answer to "we have 15 validations" - which the caller has already applied to `bars`.
 *
 * Bars are unlabelled marks, so the strip as a whole carries the accessible name: "4 of 6
 * validations passed" says the thing a sighted user reads off the colours, and each bar names its
 * own stamp and status in its tooltip.
 */

/**
 * A single bar. Its own component because the colours come from a hook, and a strip has as many
 * bars as the branch has stamps.
 */
function ValidationBar({validationStamp, statusId, height}) {

    const {token} = theme.useToken()
    const colors = useValidationStateColors(statusId)
    const hasRun = statusId !== NONE_STATUS_ID

    return (
        <span
            data-testid={`validation-bar-${validationStamp?.id}`}
            data-status={statusId}
            title={`${validationStamp?.name} — ${hasRun ? statusId : 'No run'}`}
            style={{
                display: 'inline-block',
                width: 6,
                height,
                borderRadius: token.borderRadiusXS,
                // A stamp which never ran is an empty slot, drawn as an outline so it can never be
                // mistaken for an outcome - the same contract the status marks honour.
                backgroundColor: hasRun ? colors.background : 'transparent',
                border: `1px solid ${colors.border}`,
            }}
        />
    )
}

/**
 * @param bars One entry per stamp, from `validationStrip`
 * @param passed How many of them passed
 * @param total How many there are
 * @param height Bar height in px
 */
export default function ValidationStrip({bars, passed, total, height = 16}) {

    const {token} = theme.useToken()

    // No bars rather than a row of empty ones: a build with no validations has nothing to say here,
    // and empty bars would claim stamps it never ran.
    if (!bars || bars.length === 0) return null

    return (
        <span
            data-testid="validation-strip"
            role="img"
            aria-label={`${passed} of ${total} validations passed`}
            style={{display: 'inline-flex', alignItems: 'center', gap: 3}}
        >
            {
                bars.map(bar =>
                    <ValidationBar
                        key={bar.key}
                        validationStamp={bar.validationStamp}
                        statusId={bar.statusId}
                        height={height}
                    />
                )
            }
            <Typography.Text
                type="secondary"
                style={{fontSize: token.fontSizeSM, marginLeft: token.marginXXS}}
                // The count is already in the strip's own label; repeating it to a screen reader
                // would announce the same fact twice.
                aria-hidden="true"
            >
                {passed}/{total}
            </Typography.Text>
        </span>
    )
}
