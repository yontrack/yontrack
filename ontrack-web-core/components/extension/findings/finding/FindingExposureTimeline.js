import Link from "next/link";
import {Space, theme, Tooltip, Typography} from "antd";
import dayjs from "dayjs";
import {branchUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import PeriodBuild, {UNKNOWN_BUILD} from "@components/extension/findings/finding/PeriodBuild";
import {exposureTimeline, formatExposureDuration} from "@components/extension/findings/findingsModel";

const LANE_HEIGHT = 44
const BAR_TOP = 24
const BAR_HEIGHT = 10
const LABEL_WIDTH = 160

function periodLabel(period) {
    const from = period.startedInBuild ?? UNKNOWN_BUILD
    const duration = formatExposureDuration(period.durationSeconds, period.ongoing)
    const accepted = period.acceptedSpans?.length > 0 ? ', accepted' : ''
    if (period.ongoing) {
        return `Exposed since ${from}${accepted}, ${duration}`
    } else {
        return `Exposed from ${from}, fixed in ${period.endedInBuild ?? UNKNOWN_BUILD}${accepted}, ${duration}`
    }
}

function RunText({run}) {
    return run ? <> (run #{run.runOrder})</> : null
}

function PeriodTooltip({period}) {
    return (
        <Space orientation="vertical" size={0}>
            <span>From <b>{period.startedInBuild ?? UNKNOWN_BUILD}</b><RunText run={period.startedBy}/>, <TimestampText
                value={period.startedAt}/></span>
            {
                period.endedAt ?
                    <span>Fixed in <b>{period.endedInBuild ?? UNKNOWN_BUILD}</b><RunText run={period.endedBy}/>, <TimestampText
                        value={period.endedAt}/></span> :
                    <span>Still exposed</span>
            }
            {
                period.acceptedSpans?.map((acceptedSpan, index) =>
                    <span key={index}>
                        Accepted from <TimestampText value={acceptedSpan.from}/>
                        {acceptedSpan.acceptance?.expiresAt ? `, until ${acceptedSpan.acceptance.expiresAt}` : ''}
                    </span>
                )
            }
            <span>{formatExposureDuration(period.durationSeconds, period.ongoing)}</span>
        </Space>
    )
}

/**
 * The exposure of a finding on a time axis, one lane per branch and stamp: a bar per period, red
 * while exposed, amber while accepted — still counting as exposed — and ending on a green dot when
 * fixed, with the builds at its ends. The table below carries the same data, as text.
 */
export default function FindingExposureTimeline({exposures, now = new Date()}) {

    const {token} = theme.useToken()
    const timeline = exposureTimeline(exposures, now)
    if (!timeline) return null

    return (
        <div data-testid="finding-exposure-timeline" style={{overflowX: 'auto'}}>
            <div style={{minWidth: 640}}>
                {
                    timeline.lanes.map(lane =>
                        <div
                            key={lane.key}
                            data-testid={`finding-exposure-lane-${lane.branch.name}-${lane.validationStamp.name}`}
                            style={{display: 'flex', alignItems: 'center', height: LANE_HEIGHT}}
                        >
                            <div style={{width: LABEL_WIDTH, flexShrink: 0, overflow: 'hidden'}}>
                                <Space orientation="vertical" size={0}>
                                    <Link href={branchUri(lane.branch)}>{lane.branch.name}</Link>
                                    {
                                        lane.showStamp &&
                                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                                            {lane.validationStamp.name}
                                        </Typography.Text>
                                    }
                                </Space>
                            </div>
                            <div style={{position: 'relative', flex: 1, height: LANE_HEIGHT}}>
                                {
                                    lane.bars.map((bar, index) =>
                                        <div key={index}>
                                            <Tooltip title={<PeriodTooltip period={bar.period}/>}>
                                                <div
                                                    role="img"
                                                    aria-label={periodLabel(bar.period)}
                                                    data-testid="finding-exposure-bar"
                                                    style={{
                                                        position: 'absolute',
                                                        top: BAR_TOP,
                                                        left: `${bar.left}%`,
                                                        width: `${bar.width}%`,
                                                        minWidth: 4,
                                                        height: BAR_HEIGHT,
                                                        borderRadius: 2,
                                                        background: token.colorError,
                                                        overflow: 'hidden',
                                                    }}
                                                >
                                                    {
                                                        bar.accepted.map((accepted, spanIndex) =>
                                                            <div
                                                                key={spanIndex}
                                                                data-testid="finding-exposure-accepted"
                                                                style={{
                                                                    position: 'absolute',
                                                                    top: 0,
                                                                    bottom: 0,
                                                                    left: `${bar.width > 0 ? ((accepted.left - bar.left) / bar.width) * 100 : 0}%`,
                                                                    width: `${bar.width > 0 ? (accepted.width / bar.width) * 100 : 100}%`,
                                                                    background: token.colorWarning,
                                                                }}
                                                            />
                                                        )
                                                    }
                                                </div>
                                            </Tooltip>
                                            {
                                                bar.fixed &&
                                                <div
                                                    aria-hidden="true"
                                                    data-testid="finding-exposure-fix"
                                                    style={{
                                                        position: 'absolute',
                                                        top: BAR_TOP - 3,
                                                        left: `calc(${bar.left + bar.width}% - 8px)`,
                                                        width: 16,
                                                        height: 16,
                                                        borderRadius: '50%',
                                                        background: token.colorSuccess,
                                                        border: `2px solid ${token.colorBgContainer}`,
                                                    }}
                                                />
                                            }
                                            <Typography.Text
                                                type="secondary"
                                                style={{
                                                    position: 'absolute',
                                                    top: 0,
                                                    left: `${bar.left}%`,
                                                    fontSize: 12,
                                                    whiteSpace: 'nowrap',
                                                }}
                                            >
                                                <PeriodBuild run={bar.period.startedBy} name={bar.period.startedInBuild}/>
                                                {
                                                    bar.fixed &&
                                                    <> → <PeriodBuild run={bar.period.endedBy}
                                                                      name={bar.period.endedInBuild}/></>
                                                }
                                                {
                                                    bar.accepted.length > 0 && <> · accepted</>
                                                }
                                                {' · '}{formatExposureDuration(bar.period.durationSeconds, bar.period.ongoing)}
                                            </Typography.Text>
                                        </div>
                                    )
                                }
                            </div>
                        </div>
                    )
                }
                <div style={{display: 'flex'}}>
                    <div style={{width: LABEL_WIDTH, flexShrink: 0}}/>
                    <div style={{
                        position: 'relative',
                        flex: 1,
                        height: 20,
                        borderTop: `1px solid ${token.colorBorderSecondary}`,
                    }}>
                        {
                            timeline.ticks.map(tick =>
                                <Typography.Text
                                    key={tick.key}
                                    type="secondary"
                                    style={{
                                        position: 'absolute',
                                        top: 2,
                                        left: `${tick.left}%`,
                                        transform: tick.key === 0 ? 'none' : (tick.left >= 100 ? 'translateX(-100%)' : 'translateX(-50%)'),
                                        fontSize: 11,
                                        whiteSpace: 'nowrap',
                                    }}
                                >
                                    {dayjs(tick.time).format('D MMM')}
                                </Typography.Text>
                            )
                        }
                    </div>
                </div>
                <Space size={16} wrap style={{marginTop: 8, fontSize: 12}}>
                    <Space size={4}>
                        <span aria-hidden="true" style={{display: 'inline-block', width: 12, height: 8, borderRadius: 2, background: token.colorError}}/>
                        <Typography.Text type="secondary">Exposed</Typography.Text>
                    </Space>
                    <Space size={4}>
                        <span aria-hidden="true" style={{display: 'inline-block', width: 12, height: 8, borderRadius: 2, background: token.colorWarning}}/>
                        <Typography.Text type="secondary">Accepted (still counts as exposed)</Typography.Text>
                    </Space>
                    <Space size={4}>
                        <span aria-hidden="true" style={{display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: token.colorSuccess}}/>
                        <Typography.Text type="secondary">Fixed</Typography.Text>
                    </Space>
                </Space>
            </div>
        </div>
    )
}
