import {Space, theme, Tooltip, Typography} from "antd";
import TimestampText from "@components/common/TimestampText";
import {exposedForText, exposureBarRatio} from "@components/extension/findings/findingsModel";

const BAR_WIDTH = 64
const BAR_HEIGHT = 6

/**
 * Where and since when a finding has been exposed for that long: branch, stamp, start, and whether
 * this exposure is accepted or reopened.
 */
function ExposedForTooltip({exposedFor}) {
    return (
        <Space orientation="vertical" size={0}>
            <span>{exposedFor.branch?.name} · {exposedFor.validationStamp?.name}</span>
            <span>Since <TimestampText value={exposedFor.since}/></span>
            {exposedFor.accepted && <span>Accepted</span>}
            {exposedFor.reopened && <span>Reopened</span>}
        </Space>
    )
}

/**
 * How long a finding has been exposed, in the table of the findings of a project: an age bar on a
 * log scale from an hour to a year, amber when the exposure is accepted, then the duration of the
 * longest ongoing period. A fixed finding says how long its last fix took, without bar.
 *
 * The bar is decorative: the text carries the value.
 */
export default function FindingExposedFor({finding}) {
    const {token} = theme.useToken()
    const exposedFor = finding.exposedFor
    const text = exposedForText(exposedFor)
    if (!text) return null

    if (!exposedFor.ongoing) {
        return <Typography.Text type="secondary" data-testid={`finding-exposed-for-${finding.id}`}>{text}</Typography.Text>
    }

    return (
        <Tooltip title={<ExposedForTooltip exposedFor={exposedFor}/>}>
            <span
                data-testid={`finding-exposed-for-${finding.id}`}
                style={{display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap'}}
            >
                <span
                    aria-hidden="true"
                    style={{
                        display: 'inline-block',
                        position: 'relative',
                        width: BAR_WIDTH,
                        height: BAR_HEIGHT,
                        borderRadius: BAR_HEIGHT / 2,
                        background: token.colorFillSecondary,
                    }}
                >
                    <span
                        data-testid="finding-exposed-for-bar"
                        style={{
                            position: 'absolute',
                            left: 0,
                            top: 0,
                            height: BAR_HEIGHT,
                            width: `${exposureBarRatio(exposedFor.ongoingSeconds) * 100}%`,
                            borderRadius: BAR_HEIGHT / 2,
                            background: exposedFor.accepted ? token.colorWarning : token.colorTextTertiary,
                        }}
                    />
                </span>
                <span>{text}</span>
            </span>
        </Tooltip>
    )
}
