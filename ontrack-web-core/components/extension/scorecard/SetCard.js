import {useId} from "react";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {
    estateMarkerHeadline,
    hasTargets,
    headlineTone,
    setTargetCount,
    setTitle,
    targetHeadline,
} from "@components/extension/scorecard/scorecardModel";
import TargetRing, {NoTargetCircle} from "@components/extension/scorecard/TargetRing";
import ScorecardInfo from "@components/extension/scorecard/ScorecardInfo";
import SetExplanation from "@components/extension/scorecard/SetExplanation";

const TONE_COLORS = {
    met: 'var(--ot-scorecard-met-text)',
    missed: 'var(--ot-scorecard-missed-text)',
}

/**
 * The card of a set of readings, which selects it.
 *
 * - An estate shows the ring of its target count, with its name, the headline "N of M targets
 *   met", and the marker it reads up to. An estate with no judged reading has no ring: its targets
 *   are "not judged", or it has "no targets".
 * - The Project set is never judged: a neutral circle stands for the ring.
 *
 * The whole card is a real `<button>`, `aria-pressed` when its set is selected, named by the title,
 * the headline and the marker. The ⓘ explaining the set is a button too, and a button cannot hold
 * another one: the selecting button is laid over the card, under its content, which lets the clicks
 * through - all but those on the ⓘ, which stays beside the title.
 */
export default function SetCard({set, selected, onSelect, testId}) {

    const id = useId()
    const estate = set.estate
    const title = setTitle(set)
    const counted = setTargetCount(set)
    const judged = counted.count > 0
    const {headline, secondary} = targetHeadline(counted)
    const tone = judged ? headlineTone(counted) : 'normal'
    // An estate whose targets were none judged is not an estate with no target
    const unjudgedTargets = !!estate && hasTargets(set)

    return (
        <div
            data-testid={testId}
            data-selected={selected}
            style={{
                position: 'relative',
                height: '100%',
                boxSizing: 'border-box',
                borderRadius: 12,
                border: selected ?
                    '2px solid var(--ot-chart-series-purple)' :
                    '1px solid var(--ot-border-subtle)',
                background: selected ? 'var(--ot-scorecard-selected-bg)' : 'var(--ot-bg-elevated)',
                color: 'var(--ot-text)',
                // The border grows by a pixel when selected: the padding gives it back, so that the
                // content does not move
                padding: selected ? '13px 15px' : '14px 16px',
                display: 'flex',
                gap: 16,
                alignItems: 'center',
            }}
        >
            <button
                type="button"
                aria-pressed={selected}
                aria-labelledby={`${id}-title ${id}-headline ${id}-marker`}
                onClick={() => onSelect(set)}
                style={{
                    position: 'absolute',
                    inset: 0,
                    width: '100%',
                    height: '100%',
                    padding: 0,
                    background: 'transparent',
                    border: 'none',
                    borderRadius: 11,
                    cursor: 'pointer',
                }}
            />
            <span style={{position: 'relative', pointerEvents: 'none', display: 'flex'}}>
                {
                    judged ?
                        <TargetRing
                            met={counted.met}
                            count={counted.count}
                            label={`${headline} in ${estate.name}`}
                            testId={testId ? `${testId}-ring` : undefined}
                        >
                            <span style={{fontSize: 21, fontWeight: 700}}>{counted.met}/{counted.count}</span>
                        </TargetRing> :
                        <NoTargetCircle text={unjudgedTargets ? 'not judged' : 'no targets'}/>
                }
            </span>
            <span style={{position: 'relative', display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0, pointerEvents: 'none'}}>
                <span style={{display: 'inline-flex', alignItems: 'center', gap: 2}}>
                    <span id={`${id}-title`} style={{fontSize: 15, fontWeight: 600}}>{estate ? estate.name : title}</span>
                    <span style={{pointerEvents: 'auto'}}>
                        <ScorecardInfo
                            label={`About the ${title} set`}
                            title={title}
                            testId={`scorecard-set-info-${set.name}`}
                            content={<SetExplanation set={set}/>}
                        />
                    </span>
                </span>
                <span id={`${id}-headline`} data-testid={testId ? `${testId}-headline` : undefined} style={{fontSize: 13}}>
                    {
                        estate ?
                            <>
                                <span
                                    data-testid={testId ? `${testId}-headline-text` : undefined}
                                    style={{fontWeight: 500, color: TONE_COLORS[tone]}}
                                >
                                    {judged ? headline : (unjudgedTargets ? 'No target judged' : 'No targets')}
                                </span>
                                {secondary && <SecondaryText> · {secondary}</SecondaryText>}
                            </> :
                            <span style={{fontWeight: 500}}>Readings only, never judged</span>
                    }
                </span>
                <SecondaryText id={`${id}-marker`} style={{fontSize: 12}}>
                    {estate ? estateMarkerHeadline(estate.marker) : "Read up to each branch's last promotion"}
                </SecondaryText>
            </span>
        </div>
    )
}
