import {Space} from "antd";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import TimestampText from "@components/common/TimestampText";
import {SCORECARD_HISTORY_DAYS} from "@components/extension/scorecard/scorecardModel";

/**
 * The footer of the readings of a scorecard: when they were computed, what the sparklines cover,
 * and a legend of the target-met zone and of the dashed target.
 */
export default function ScorecardLegend({latest}) {
    return (
        <div
            data-testid="scorecard-legend"
            style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap', fontSize: 12}}
        >
            <SecondaryText style={{fontSize: 12}}>
                {latest && <>Computed <TimestampText value={latest} relative={true}/> · </>}
                daily readings over the last {SCORECARD_HISTORY_DAYS} days
            </SecondaryText>
            <Space size={16}>
                <SecondaryText style={{fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6}}>
                    <span
                        aria-hidden="true"
                        style={{
                            width: 14,
                            height: 10,
                            background: 'var(--ot-scorecard-met-zone)',
                            border: '1px solid var(--ot-scorecard-met)',
                        }}
                    />
                    target met zone
                </SecondaryText>
                <SecondaryText style={{fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6}}>
                    <span aria-hidden="true" style={{width: 16, borderTop: '1px dashed var(--ot-chart-axis)'}}/>
                    target
                </SecondaryText>
            </Space>
        </div>
    )
}
