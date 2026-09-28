import {Card, Descriptions, Space, Typography} from "antd";
import TimestampText from "@components/common/TimestampText";
import ReadingValue from "@components/extension/scorecard/ReadingValue";
import ReadingSparkline from "@components/extension/scorecard/ReadingSparkline";
import {
    markerText,
    readingDetailItems,
    readingName,
    readingUsesMarker,
    scopeText,
    targetText,
    windowDays,
} from "@components/extension/scorecard/scorecardModel";

const timestampFormat = "YYYY MMM DD, HH:mm"

/**
 * One reading of the scorecard page: its value and trend, and what explains it — the window, the
 * branches which fed it, the marker it was read up to, the target, and its details.
 */
export default function ReadingCard({reading, testId}) {

    const days = windowDays(reading)
    const target = targetText(reading)

    const items = [
        {
            key: 'window',
            label: 'Window',
            children: <Space size={4} wrap>
                {days !== null && <Typography.Text>{days} days,</Typography.Text>}
                <TimestampText value={reading.windowStart} format={timestampFormat}/>
                <Typography.Text>→</Typography.Text>
                <TimestampText value={reading.windowEnd} format={timestampFormat}/>
            </Space>,
        },
        {
            key: 'branches',
            label: 'Branches',
            children: scopeText(reading.details),
        },
        ...(readingUsesMarker(reading.key) ? [{
            key: 'marker',
            label: 'Marker',
            children: markerText(reading.details),
        }] : []),
        ...(target ? [{
            key: 'target',
            label: 'Target',
            children: target,
        }] : []),
        ...readingDetailItems(reading).map(({key, label, text, timestamp}) => ({
            key,
            label,
            children: timestamp ? <TimestampText value={timestamp} format={timestampFormat}/> : text,
        })),
        {
            key: 'computedAt',
            label: 'Computed',
            children: <TimestampText value={reading.computedAt} format={timestampFormat}/>,
        },
    ]

    return (
        <Card
            size="small"
            title={readingName(reading.key)}
            data-testid={testId}
            style={{height: '100%'}}
        >
            <Space orientation="vertical" size={12} style={{width: '100%'}}>
                <ReadingValue reading={reading} testId={testId ? `${testId}-reading` : undefined}/>
                <ReadingSparkline reading={reading}/>
                <Descriptions
                    size="small"
                    column={1}
                    items={items}
                    data-testid={testId ? `${testId}-details` : undefined}
                />
            </Space>
        </Card>
    )
}
