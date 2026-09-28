import {Space, Tag, Tooltip, Typography} from "antd";
import {FaCheck, FaQuestionCircle, FaTimes} from "react-icons/fa";
import {
    formatReadingValue,
    readingJudgement,
    sampleCount,
    sampleCountText,
    targetText,
    unknownReasonText,
} from "@components/extension/scorecard/scorecardModel";

/**
 * One reading: its value against its target, with the number of samples it rests on.
 *
 * - An unknown reading is a distinct "Unknown" tag, its reason on hover (and focus).
 * - A time to restore with no failure in the window is neutral — "No failure in window" — and
 *   never reads 0.
 * - Met and missed say so in words and with an icon, not only with a colour.
 */
export default function ReadingValue({reading, strong = true, testId}) {

    const judgement = readingJudgement(reading)
    const count = sampleCountText(sampleCount(reading))
    const target = targetText(reading)

    return (
        <Space size={8} wrap data-testid={testId} data-judgement={judgement}>
            {
                judgement === 'UNKNOWN' &&
                <Tooltip title={unknownReasonText(reading.unknownReason)}>
                    <Tag
                        icon={<FaQuestionCircle/>}
                        tabIndex={0}
                        aria-label={`Unknown: ${unknownReasonText(reading.unknownReason)}`}
                        data-testid={testId ? `${testId}-unknown` : undefined}
                    >
                        Unknown
                    </Tag>
                </Tooltip>
            }
            {
                judgement === 'NO_FAILURE' &&
                <Typography.Text data-testid={testId ? `${testId}-no-failure` : undefined}>
                    {unknownReasonText('NO_FAILURE')}
                </Typography.Text>
            }
            {
                ['MET', 'MISSED', 'SHOWN'].includes(judgement) &&
                <Typography.Text strong={strong} data-testid={testId ? `${testId}-value` : undefined}>
                    {formatReadingValue(reading.key, reading.value)}
                </Typography.Text>
            }
            {
                judgement === 'MET' &&
                <Tag color="success" icon={<FaCheck/>}>
                    Met {target}
                </Tag>
            }
            {
                judgement === 'MISSED' &&
                <Tag color="error" icon={<FaTimes/>}>
                    Missed {target}
                </Tag>
            }
            {
                count && judgement !== 'NO_FAILURE' &&
                <Typography.Text
                    type="secondary"
                    style={{fontSize: '85%'}}
                    data-testid={testId ? `${testId}-count` : undefined}
                >
                    {count}
                </Typography.Text>
            }
        </Space>
    )
}
