import {Col, Descriptions, Flex, Popover, Row, Space, Timeline, Typography} from "antd";
import AutoVersioningAuditEntryTarget from "@components/extension/auto-versioning/AutoVersioningAuditEntryTarget";
import AutoVersioningAuditEntryState from "@components/extension/auto-versioning/AutoVersioningAuditEntryState";
import TimestampText from "@components/common/TimestampText";
import {FaInfoCircle} from "react-icons/fa";
import AutoVersioningAuditEntryStateData from "@components/extension/auto-versioning/AutoVersioningAuditEntryStateData";
import ProjectLinkByName from "@components/projects/ProjectLinkByName";
import AutoVersioningAuditProjectSourceLink
    from "@components/extension/auto-versioning/AutoVersioningAuditProjectSourceLink";
import {AutoRefreshButton} from "@components/common/AutoRefresh";
import AutoVersioningAuditEntryPR from "@components/extension/auto-versioning/AutoVersioningAuditEntryPR";
import AutoVersioningApproval from "@components/extension/auto-versioning/AutoVersioningApproval";
import Link from "next/link";
import DefaultPromotionRunLink from "@components/promotionRuns/DefaultPromotionRunLink";
import AutoVersioningAdditionalPaths from "@components/extension/auto-versioning/AutoVersioningAdditionalPaths";
import AutoVersioningAuditEntryReschedule
    from "@components/extension/auto-versioning/AutoVersioningAuditEntryReschedule";

export default function AutoVersioningAuditEntry({entry}) {

    const items = [
        {
            key: 'target',
            label: "Target",
            children: <AutoVersioningAuditEntryTarget entry={entry} auditLink={true}/>,
        },
        {
            key: 'source',
            label: "Source",
            children: <Space>
                <ProjectLinkByName name={entry.order.sourceProject}/>
                <AutoVersioningAuditProjectSourceLink name={entry.order.sourceProject}/>
            </Space>,
        },
        {
            key: 'pr',
            label: "Pull request",
            children: <AutoVersioningAuditEntryPR entry={entry} displayStatus={true}/>,
        },
        {
            key: 'promotion',
            label: "Promotion",
            children: entry.order.sourcePromotion,
        },
        {
            key: 'qualifier',
            label: "Qualifier",
            children: entry.order.qualifier,
        },
        {
            key: 'version',
            label: "Version",
            children: entry.order.targetVersion,
        },
        {
            key: 'post-processing',
            label: "Post processing",
            children: entry.order.postProcessing,
        },
        {
            key: 'post-processing-config',
            label: "Post processing config",
            children: <Typography.Paragraph code>
                {JSON.stringify(entry.order.postProcessingConfig)}
            </Typography.Paragraph>,
        },
        {
            key: 'schedule',
            label: "Schedule",
            children: entry.order.schedule,
        },
        {
            key: 'routing',
            label: "Routing key",
            children: entry.routing,
        },
        {
            key: 'queue',
            label: "Queue name",
            children: entry.queue,
        },
        {
            key: 'auto-approval',
            label: "Auto approval",
            children:
                <AutoVersioningApproval
                    autoApproval={entry.order.autoApproval}
                    autoApprovalMode={entry.order.autoApprovalMode}
                />,
        },
        {
            key: 'targetPath',
            label: "Target path(s)",
            children: <Typography.Text code>
                {entry.order.targetPath}
            </Typography.Text>,
        },
        {
            key: 'targetPropertyType',
            label: "Target property type",
            children: <Typography.Text code>{entry.order.targetPropertyType}</Typography.Text>,
        },
        {
            key: 'targetProperty',
            label: "Target property",
            children: <Typography.Text code>{entry.order.targetProperty}</Typography.Text>,
        },
        {
            key: 'targetPropertyRegex',
            label: "Target property regex",
            children: <Typography.Text code>{entry.order.targetPropertyRegex}</Typography.Text>,
        },
        {
            key: 'targetRegex',
            label: "Target regex",
            children: <Typography.Text code>{entry.order.targetRegex}</Typography.Text>,
        },
        {
            key: 'additionalPaths',
            label: "Additional paths",
            children: <AutoVersioningAdditionalPaths additionalPaths={entry.order.additionalPaths}/>,
        },
        {
            key: 'upgradeBranchPattern',
            label: "Upgrade branch pattern",
            children: <Typography.Text code>{entry.order.upgradeBranchPattern}</Typography.Text>,
        },
        {
            key: 'upgradeBranch',
            label: "Upgrade branch",
            children: <Typography.Text code>{entry.upgradeBranch}</Typography.Text>,
        },
        {
            key: 'validationStamp',
            label: "Validation stamp",
            children: <Typography.Text code>{entry.order.validationStamp}</Typography.Text>,
        },
    ]

    const history = entry.audit.map((item, index) => (
        {
            content: <AutoVersioningAuditEntryState status={item} id={`audit-state-${index}`}/>,
            title: <Space>
                <TimestampText
                    value={item.creation.time}
                    format="YYYY MMM DD, HH:mm:ss"
                />
                <Popover
                    content={
                        <AutoVersioningAuditEntryStateData order={entry.order} state={item.state} data={item.data}/>
                    }
                >
                    <FaInfoCircle
                        color="lightblue"
                        className="ot-action"
                    />
                </Popover>
            </Space>,
        }
    ))

    // Trail
    if (entry.promotionRun) {
        history.push({
            content: <Space>
                <Popover
                    content="Promotion which led to the auto-versioning"
                >
                    <FaInfoCircle
                        color="lightblue"
                        className="ot-action"
                    />
                </Popover>
                <Link href={'/'}>Promotion</Link>
            </Space>,
            title: <DefaultPromotionRunLink promotionRun={entry.promotionRun}/>
        })
    }

    return (
        <>
            <Row>
                <Col span={14}>
                    <Space orientation="vertical" className="ot-line">
                        <Timeline
                            style={{
                                paddingTop: '3em',
                            }}
                            items={history}
                            mode="end"
                        />
                        <Flex justify="center">
                            <Space>
                                <AutoRefreshButton/>
                                <AutoVersioningAuditEntryReschedule entry={entry}/>
                            </Space>
                        </Flex>
                    </Space>
                </Col>
                <Col span={10}>
                    <Descriptions
                        column={1}
                        size="small"
                        title={
                            <Space>
                                <Typography.Text>Auto-versioning audit entry</Typography.Text>
                                <Typography.Text code>{entry.order.uuid}</Typography.Text>
                            </Space>
                        }
                        items={items}
                    />
                </Col>
            </Row>
        </>
    )
}