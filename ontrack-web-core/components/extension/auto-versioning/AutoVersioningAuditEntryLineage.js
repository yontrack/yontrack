import {Space, Typography} from "antd";
import Link from "next/link";
import {FaArrowLeft, FaArrowRight, FaRedo} from "react-icons/fa";
import {autoVersioningAuditEntryUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";

const EntryLink = ({uuid}) => (
    <Link href={autoVersioningAuditEntryUri(uuid)}>
        <Typography.Text code>{uuid}</Typography.Text>
    </Link>
)

/**
 * Lines describing the automatic retries & manual reschedules an audit entry is part of.
 *
 * Returns an empty list when the entry is neither a retry, nor retried, nor rescheduled.
 */
export const autoVersioningAuditEntryLineageLines = (lineage) => {
    const lines = []
    if (lineage?.retryOf) {
        lines.push(
            <Space key="retryOf" data-testid="av-lineage-retry-of">
                <FaArrowLeft/>
                <Typography.Text>
                    Automatic retry {lineage.retryAttempt}/{lineage.retryMax} of
                </Typography.Text>
                <EntryLink uuid={lineage.retryOf}/>
            </Space>
        )
    }
    if (lineage?.rescheduledFrom) {
        lines.push(
            <Space key="rescheduledFrom" data-testid="av-lineage-rescheduled-from">
                <FaArrowLeft/>
                <Typography.Text>Manually rescheduled from</Typography.Text>
                <EntryLink uuid={lineage.rescheduledFrom}/>
            </Space>
        )
    }
    if (lineage?.retryUuid) {
        lines.push(
            <Space key="retryUuid" data-testid="av-lineage-retry">
                <FaArrowRight/>
                <Typography.Text>Automatic retry scheduled</Typography.Text>
                {
                    lineage.retryAt && <>
                        <Typography.Text>at</Typography.Text>
                        <TimestampText value={lineage.retryAt} format="YYYY MMM DD, HH:mm"/>
                    </>
                }
                <EntryLink uuid={lineage.retryUuid}/>
            </Space>
        )
    }
    lineage?.rescheduledAs?.forEach(uuid => {
        lines.push(
            <Space key={`rescheduledAs-${uuid}`} data-testid="av-lineage-rescheduled-as">
                <FaRedo/>
                <Typography.Text>Manually rescheduled as</Typography.Text>
                <EntryLink uuid={uuid}/>
            </Space>
        )
    })
    return lines
}
