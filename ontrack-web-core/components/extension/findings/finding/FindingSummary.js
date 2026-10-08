import Link from "next/link";
import {Descriptions, Typography} from "antd";
import {FaExternalLinkAlt} from "react-icons/fa";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {kindName} from "@components/extension/findings/findingsModel";
import {branchUri} from "@components/common/Links";
import PeriodBuild from "@components/extension/findings/finding/PeriodBuild";

/**
 * When a finding was seen at a given moment, and, when known, where: its branch and its build.
 *
 * @param time Time, when no sighting is known
 * @param sighting `{time, branch, validationRun, build}`, or nothing
 */
function Sighting({time, sighting}) {
    if (!sighting) {
        return <TimestampText value={time}/>
    }
    return (
        <span data-testid="finding-sighting">
            <TimestampText value={sighting.time}/> on <Link href={branchUri(sighting.branch)}>{sighting.branch.name}</Link>,
            build <PeriodBuild run={sighting.validationRun} name={sighting.build}/>
        </span>
    )
}

/**
 * What a finding is: its identity as the scanner gives it, its severity and state in its project,
 * when and where it was first seen and resolved, and the link to more information about it.
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
            children: <Sighting time={finding.firstSeen} sighting={finding.firstSeenIn}/>,
        },
        {
            key: 'lastSeen',
            label: 'Last seen',
            children: <TimestampText value={finding.lastSeen}/>,
        },
        ...(finding.resolvedAt ? [{
            key: 'resolvedAt',
            label: 'Resolved',
            children: <Sighting time={finding.resolvedAt} sighting={finding.resolvedIn}/>,
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
