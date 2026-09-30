import {ringSegments} from "@components/extension/scorecard/scorecardModel";

// Gap between two segments, as a fraction of the circle: about five degrees
const GAP = 0.014

/**
 * The ring of a target count: one segment per judged reading, the met ones first, then the missed
 * ones, a small gap between two. The centre holds `children`, typically `met/count`.
 *
 * The ring is one image whose accessible name is `label` - the headline of the count - and its
 * segments and centre are hidden from assistive technologies, which would otherwise read the count
 * twice.
 */
export default function TargetRing({met, count, size = 84, thickness = 11, label, children, testId}) {

    const radius = (size - thickness) / 2
    const circumference = 2 * Math.PI * radius
    const segments = ringSegments({met, count}, GAP)

    return (
        <div
            role="img"
            aria-label={label}
            data-testid={testId}
            style={{position: 'relative', width: size, height: size, flexShrink: 0}}
        >
            <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
                <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
                    {
                        segments.map((segment, index) =>
                            <circle
                                key={index}
                                data-met={segment.met}
                                cx={size / 2}
                                cy={size / 2}
                                r={radius}
                                fill="none"
                                stroke={segment.met ? 'var(--ot-scorecard-met)' : 'var(--ot-scorecard-missed)'}
                                strokeWidth={thickness}
                                strokeDasharray={`${segment.length * circumference} ${circumference}`}
                                strokeDashoffset={-segment.start * circumference}
                            />
                        )
                    }
                </g>
            </svg>
            <div
                aria-hidden="true"
                style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontVariantNumeric: 'tabular-nums',
                }}
            >
                {children}
            </div>
        </div>
    )
}

/**
 * The neutral circle standing for a set with no judged reading, which has no ring: "no targets" by
 * default, or "not judged" for targets none of which could be judged.
 */
export function NoTargetCircle({size = 84, thickness = 10, text = 'no targets', testId}) {
    return (
        <div
            data-testid={testId}
            style={{
                width: size,
                height: size,
                flexShrink: 0,
                boxSizing: 'border-box',
                borderRadius: '50%',
                border: `${thickness}px solid var(--ot-scorecard-neutral-bg)`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 12,
                fontWeight: 600,
                textAlign: 'center',
            }}
        >
            {text}
        </div>
    )
}
