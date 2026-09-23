import {Descriptions, Typography} from "antd";
import {FaExternalLinkAlt} from "react-icons/fa";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {kindName} from "@components/extension/findings/findingsModel";

/**
 * What a finding is: its identity as the scanner gives it, its severity and state in its project,
 * when it was seen, and the link to more information about it.
 */
export default function FindingSummary({finding}) {

    const items = [
        {
            key: 'externalId',
            label: 'External ID',
            children: <Typography.Text code copyable>{finding.externalId}</Typography.Text>,
        },
        {
            key: 'title',
            label: 'Title',
            children: finding.title,
        },
        {
            key: 'severity',
            label: 'Severity',
            children: <FindingSeverityTag severity={finding.maxSeverity}/>,
        },
        {
            key: 'state',
            label: 'State in the project',
            children: <FindingStateTag state={finding.state}/>,
        },
        {
            key: 'scanner',
            label: 'Scanner',
            children: finding.scanner,
        },
        {
            key: 'kind',
            label: 'Kind',
            children: kindName(finding.kind),
        },
        {
            key: 'location',
            label: 'Location',
            children: finding.location ?
                <Typography.Text code style={{wordBreak: 'break-all'}}>{finding.location}</Typography.Text> :
                <Typography.Text type="secondary">None</Typography.Text>,
        },
        {
            key: 'firstSeen',
            label: 'First seen',
            children: <TimestampText value={finding.firstSeen}/>,
        },
        {
            key: 'lastSeen',
            label: 'Last seen',
            children: <TimestampText value={finding.lastSeen}/>,
        },
        ...(finding.resolvedAt ? [{
            key: 'resolvedAt',
            label: 'Resolved',
            children: <TimestampText value={finding.resolvedAt}/>,
        }] : []),
        {
            key: 'url',
            label: 'More information',
            children: finding.url ?
                <Typography.Link
                    href={finding.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    data-testid="finding-url"
                    style={{wordBreak: 'break-all'}}
                >
                    {finding.url} <FaExternalLinkAlt aria-hidden="true"/>
                </Typography.Link> :
                <Typography.Text type="secondary">No link given by the scanner</Typography.Text>,
        },
    ]

    return (
        <Descriptions
            data-testid="finding-summary"
            size="small"
            column={1}
            bordered
            items={items}
        />
    )
}
