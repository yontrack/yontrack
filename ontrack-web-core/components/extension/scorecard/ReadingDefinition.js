import {Typography} from "antd";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {
    readingDescription,
    readingUnit,
    RUNG,
    rungOf,
    SECURITY_MATURITY_INTRO,
    securityMaturityRungs,
} from "@components/extension/scorecard/scorecardModel";

/**
 * What a reading measures: its description, in the wording of the kind of marker it is read up to;
 * for the security maturity, its rungs, one per line, the one of the value and the one of the target
 * marked in words.
 *
 * @param readingKey Key of the reading
 * @param markerKind Kind of marker the reading is read up to, `PROMOTION` when not given
 * @param coverage What covered means, `{expectedKinds, freshnessDays}`, `null` for general terms
 * @param current Value of the reading, to mark its rung; `null` for none
 * @param target Target of the reading, to mark its rung; `null` for none
 * @param secondary Whether the text is secondary, as under the title of a tile
 */
export default function ReadingDefinition({
                                              readingKey,
                                              markerKind,
                                              coverage = null,
                                              current = null,
                                              target = null,
                                              secondary = false,
                                              testId,
                                          }) {
    const Text = secondary ? SecondaryText : Typography.Text
    const fontSize = secondary ? 12 : undefined
    if (readingUnit(readingKey) !== RUNG) {
        const description = readingDescription(readingKey, markerKind)
        return description && <Text style={{fontSize}} data-testid={testId}>{description}</Text>
    }
    const rungs = securityMaturityRungs({
        coverage,
        current: rungOf(readingKey, current),
        target: rungOf(readingKey, target),
    })
    return (
        <div data-testid={testId} style={{display: 'flex', flexDirection: 'column', gap: 6}}>
            <Text style={{fontSize}}>{SECURITY_MATURITY_INTRO}</Text>
            <ul style={{listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 4}}>
                {
                    rungs.map(({value, name, description, marks}) =>
                        <li key={value} data-rung={value} style={{fontSize}}>
                            <Typography.Text strong style={{fontSize}}>{value} · {name}</Typography.Text>
                            {
                                marks.length > 0 &&
                                <Typography.Text strong style={{fontSize, marginInlineStart: 6}}>
                                    ← {marks.join(', ')}
                                </Typography.Text>
                            }
                            <br/>
                            <SecondaryText style={{fontSize: 12}}>{description}</SecondaryText>
                        </li>
                    )
                }
            </ul>
        </div>
    )
}
