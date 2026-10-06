import {Space} from "antd";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import TimestampText from "@components/common/TimestampText";
import {SCORECARD_HISTORY_DAYS} from "@components/extension/scorecard/scorecardModel";

/**
 * One item of a legend: its swatch, and what it stands for.
 */
export function ScorecardLegendItem({swatch, testId, children}) {
    return (
        <SecondaryText
            data-testid={testId}
            style={{fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap'}}
        >
            {swatch}
            {children}
        </SecondaryText>
    )
}

/**
 * The footer of readings: when they were computed and what they are, then the items of a legend.
 *
 * @param latest When the readings were last computed, `null` when never
 * @param notComputed What is said instead of when they were computed, before they ever were; nothing by default
 * @param description What the readings are
 */
export function ScorecardLegendLine({latest, notComputed = null, description, testId, children}) {
    return (
        <div
            data-testid={testId}
            style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap', fontSize: 12}}
        >
            <SecondaryText style={{fontSize: 12}}>
                {latest ? <>Computed <TimestampText value={latest} relative={true}/> · </> : notComputed && `${notComputed} · `}
                {description}
            </SecondaryText>
            <Space size={16} wrap>
                {children}
            </Space>
        </div>
    )
}

/**
 * The footer of the readings of a scorecard: when they were computed, what the sparklines cover,
 * and a legend of the target-met zone and of the dashed target.
 */
export default function ScorecardLegend({latest}) {
    return (
        <ScorecardLegendLine
            testId="scorecard-legend"
            latest={latest}
            description={`daily readings over the last ${SCORECARD_HISTORY_DAYS} days`}
        >
            <ScorecardLegendItem
                swatch={
                    <span
                        aria-hidden="true"
                        style={{
                            width: 14,
                            height: 10,
                            background: 'var(--ot-scorecard-met-zone)',
                            border: '1px solid var(--ot-scorecard-met)',
                        }}
                    />
                }
            >
                target met zone
            </ScorecardLegendItem>
            <ScorecardLegendItem
                swatch={<span aria-hidden="true" style={{width: 16, borderTop: '1px dashed var(--ot-chart-axis)'}}/>}
            >
                target
            </ScorecardLegendItem>
        </ScorecardLegendLine>
    )
}
