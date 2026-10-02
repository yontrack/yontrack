import {Tooltip} from "antd";
import {formatReadingValue, rungDescription, rungOf} from "@components/extension/scorecard/scorecardModel";

/**
 * A value of a reading which stands for a rung of the security maturity, explaining the rung on
 * hover and on focus — focusable, the explanation in its label too. Any other value is left as is.
 *
 * @param readingKey Key of the reading
 * @param value Value of the reading
 * @param coverage What covered means, `{expectedKinds, freshnessDays}`, to word the covered rung in
 * its terms; `null` for general terms
 */
export default function RungHelp({readingKey, value, coverage = null, children}) {
    const rung = rungOf(readingKey, value)
    if (rung === null) {
        return children
    }
    const description = rungDescription(rung, coverage)
    return (
        <Tooltip title={description} trigger={['hover', 'focus']}>
            <span
                tabIndex={0}
                aria-label={`${formatReadingValue(readingKey, rung)}: ${description}`}
                style={{display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'help'}}
            >
                {children}
            </span>
        </Tooltip>
    )
}
