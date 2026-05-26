import {Button, Checkbox, Form, Input, Popover, Space, Spin, Tooltip, Typography} from "antd";
import StandardTable from "@components/common/table/StandardTable";
import {gql} from "graphql-request";
import Link from "next/link";
import {autoVersioningAuditEntryUri} from "@components/common/Links";
import AutoVersioningAuditEntryTarget from "@components/extension/auto-versioning/AutoVersioningAuditEntryTarget";
import ProjectLinkByName from "@components/projects/ProjectLinkByName";
import AutoVersioningApproval from "@components/extension/auto-versioning/AutoVersioningApproval";
import {FaColumns, FaPlay, FaSquare} from "react-icons/fa";
import AutoVersioningAuditEntryState from "@components/extension/auto-versioning/AutoVersioningAuditEntryState";
import AutoVersioningAuditEntryPR from "@components/extension/auto-versioning/AutoVersioningAuditEntryPR";
import TimestampText from "@components/common/TimestampText";
import Duration from "@components/common/Duration";
import SelectProject from "@components/projects/SelectProject";
import SelectBoolean from "@components/common/SelectBoolean";
import SelectAutoVersioningAuditState from "@components/extension/auto-versioning/SelectAutoVersioningAuditState";
import AutoVersioningSchedule from "@components/extension/auto-versioning/AutoVersioningSchedule";
import {FaForwardStep} from "react-icons/fa6";
import {useState} from "react";
import AutoVersioningLoadPRStatusesButton
    from "@components/extension/auto-versioning/AutoVersioningLoadPRStatusesButton";
import {
    getAutoVersioningAuditColumnVisibility,
    setAutoVersioningAuditColumnVisibility
} from "@components/storage/local";

export default function AutoVersioningAuditView() {

    const [loadPullRequests, setLoadPullRequests] = useState(false)
    const [loadPullRequestsCount, setLoadPullRequestsCount] = useState(0)

    const loadPRStatuses = () => {
        setLoadPullRequests(true)
        setLoadPullRequestsCount(value => value + 1)
    }

    const ALL_COLUMN_DEFS = [
        {key: 'uuid', label: 'UUID'},
        {key: 'target', label: 'Target'},
        {key: 'source', label: 'Source project'},
        {key: 'promotion', label: 'Promotion'},
        {key: 'qualifier', label: 'Qualifier'},
        {key: 'version', label: 'Version'},
        {key: 'post-processing', label: 'Post-processing'},
        {key: 'approval', label: 'Approval'},
        {key: 'schedule', label: 'Schedule'},
        {key: 'routing', label: 'Queuing'},
        {key: 'running', label: 'Running'},
        {key: 'state', label: 'State'},
        {key: 'pr', label: 'PR'},
        {key: 'timestamp', label: 'Timestamp'},
        {key: 'duration', label: 'Duration'},
    ]

    const [visibleColumns, setVisibleColumns] = useState(() => {
        const saved = getAutoVersioningAuditColumnVisibility()
        return saved ?? ALL_COLUMN_DEFS.map(c => c.key)
    })

    const toggleColumn = (key) => {
        setVisibleColumns(prev => {
            const next = prev.includes(key) ? prev.filter(k => k !== key) : [...prev, key]
            setAutoVersioningAuditColumnVisibility(next)
            return next
        })
    }

    const allColumnsKeys = ALL_COLUMN_DEFS.map(c => c.key)
    const allVisible = allColumnsKeys.every(k => visibleColumns.includes(k))

    const showAllColumns = () => {
        setVisibleColumns(allColumnsKeys)
        setAutoVersioningAuditColumnVisibility(allColumnsKeys)
    }

    const columnPickerButton = (
        <Popover
            key="column-picker"
            title="Columns"
            trigger="click"
            content={
                <Space direction="vertical">
                    <Button size="small" disabled={allVisible} onClick={showAllColumns}>
                        Show all
                    </Button>
                    {ALL_COLUMN_DEFS.map(({key, label}) => (
                        <Checkbox
                            key={key}
                            checked={visibleColumns.includes(key)}
                            onChange={() => toggleColumn(key)}
                        >
                            {label}
                        </Checkbox>
                    ))}
                </Space>
            }
        >
            <Button icon={<FaColumns/>}>Columns</Button>
        </Popover>
    )

    return (
        <>
            <Space className="ot-line" direction="vertical">
                <StandardTable
                    id="auto-versioning-audit-table"
                    rowKey={entry => entry.order.uuid}
                    filterForm={[
                        <Form.Item
                            key="targetProject"
                            name="targetProject"
                            label="Target project"
                        >
                            <SelectProject/>
                        </Form.Item>,
                        <Form.Item
                            key="targetBranch"
                            name="targetBranch"
                            label="Target branch"
                        >
                            <Input/>
                        </Form.Item>,
                        <Form.Item
                            key="sourceProject"
                            name="sourceProject"
                            label="Source project"
                        >
                            <SelectProject/>
                        </Form.Item>,
                        <Form.Item
                            key="version"
                            name="version"
                            label="Version"
                        >
                            <Input/>
                        </Form.Item>,
                        <Form.Item
                            key="routing"
                            name="routing"
                            label="Routing"
                        >
                            <Input/>
                        </Form.Item>,
                        <Form.Item
                            key="queue"
                            name="queue"
                            label="Queue"
                        >
                            <Input/>
                        </Form.Item>,
                        <Form.Item
                            key="running"
                            name="running"
                            label="Running"
                        >
                            <SelectBoolean/>
                        </Form.Item>,
                        <Form.Item
                            key="state"
                            name="state"
                            label="State"
                        >
                            <SelectAutoVersioningAuditState/>
                        </Form.Item>,
                    ]}
                    filterExtraButtons={[
                        <AutoVersioningLoadPRStatusesButton key="load-pr-statuses" onClick={loadPRStatuses}/>,
                        columnPickerButton,
                    ]}
                    query={
                        gql`
                            query AutoVersioningAudit(
                                $offset: Int!,
                                $size: Int!,
                                $targetProject: String,
                                $targetBranch: String,
                                $sourceProject: String,
                                $version: String,
                                $state: String,
                                $running: Boolean,
                                $routing: String,
                                $queue: String,
                                $loadPullRequests: Boolean = false,
                            ) {
                                autoVersioningAuditEntries(
                                    offset: $offset,
                                    size: $size,
                                    filter: {
                                        project: $targetProject,
                                        branch: $targetBranch,
                                        source: $sourceProject,
                                        version: $version,
                                        state: $state,
                                        running: $running,
                                        routing: $routing,
                                        queue: $queue,
                                    }
                                ) {
                                    pageInfo {
                                        nextPage {
                                            offset
                                            size
                                        }
                                    }
                                    pageItems {
                                        mostRecentState {
                                            creation {
                                                time
                                            }
                                            state
                                            data
                                        }
                                        duration
                                        running
                                        audit {
                                            creation {
                                                time
                                            }
                                            state
                                            data
                                        }
                                        pullRequest @include(if: $loadPullRequests) {
                                            id
                                            name
                                            link
                                            status
                                        }
                                        routing
                                        queue
                                        order {
                                            uuid
                                            sourceProject
                                            sourcePromotion
                                            branch {
                                                id
                                                name
                                                project {
                                                    id
                                                    name
                                                }
                                            }
                                            qualifier
                                            repositoryHtmlURL
                                            targetPath
                                            targetRegex
                                            targetProperty
                                            targetPropertyRegex
                                            targetPropertyType
                                            targetVersion
                                            autoApproval
                                            autoApprovalMode
                                            upgradeBranchPattern
                                            postProcessing
                                            postProcessingConfig
                                            validationStamp
                                            schedule
                                        }
                                    }
                                }
                            }
                        `
                    }
                    queryNode="autoVersioningAuditEntries"
                    autoRefresh={true}
                    variables={{loadPullRequests}}
                    reloadCount={loadPullRequestsCount}
                    filter={{}}
                    scroll={{x: 'max-content'}}
                    columns={[
                        {
                            key: 'uuid',
                            title: 'UUID',
                            render: (_, entry) => <Tooltip
                                title="Displays the details of this entry into a separate page.">
                                <Link href={autoVersioningAuditEntryUri(entry.order.uuid)}>{entry.order.uuid}</Link>
                            </Tooltip>
                        },
                        {
                            key: 'target',
                            title: 'Target',
                            render: (_, entry) => <AutoVersioningAuditEntryTarget entry={entry}/>
                        },
                        {
                            key: 'source',
                            title: "Source project",
                            render: (_, entry) => <ProjectLinkByName name={entry.order.sourceProject}/>
                        },
                        {
                            key: 'promotion',
                            title: "Promotion",
                            render: (_, entry) => <Typography.Text>{entry.order.sourcePromotion}</Typography.Text>,
                        },
                        {
                            key: 'qualifier',
                            title: "Qualifier",
                            render: (_, entry) => <Typography.Text>{entry.order.qualifier}</Typography.Text>,
                        },
                        {
                            key: 'version',
                            title: "Version",
                            render: (_, entry) => <Typography.Text>{entry.order.targetVersion}</Typography.Text>,
                        },
                        {
                            key: 'post-processing',
                            title: "Post-processing",
                            render: (_, entry) => <>
                                {
                                    entry.order.postProcessing &&
                                    <Typography.Text>{entry.order.postProcessing}</Typography.Text>
                                }
                                {
                                    !entry.order.postProcessing &&
                                    <Typography.Text type="secondary" italic>None</Typography.Text>
                                }
                            </>,
                        },
                        {
                            key: 'approval',
                            title: "Approval",
                            render: (_, entry) => <AutoVersioningApproval
                                autoApproval={entry.order.autoApproval}
                                autoApprovalMode={entry.order.autoApprovalMode}
                            />,
                        },
                        {
                            key: 'schedule',
                            title: "Schedule",
                            render: (_, entry) => <AutoVersioningSchedule schedule={entry.order.schedule}/>,
                        },
                        {
                            key: 'routing',
                            title: "Queuing",
                            render: (_, entry) => {
                                if (entry.queue) {
                                    return <Space size="small" title={`Queue: ${entry.queue}`}>
                                        <FaPlay/>
                                        <Typography.Text ellipsis={true} copyable>{entry.queue}</Typography.Text>
                                    </Space>
                                } else if (entry.routing) {
                                    return <Space size="small" title={`Routed: ${entry.routing}`}>
                                        <FaForwardStep/>
                                        <Typography.Text ellipsis={true} copyable>{entry.routing}</Typography.Text>
                                    </Space>
                                } else {
                                    return <Typography.Text type="secondary" italic>None</Typography.Text>
                                }
                            }
                        },
                        {
                            key: 'running',
                            title: "Running",
                            width: 110,
                            render: (_, entry) =>
                                <>
                                    {
                                        entry.running &&
                                        <Space>
                                            <Spin size="small"/>
                                            Running
                                        </Space>
                                    }
                                    {
                                        !entry.running &&
                                        <Typography.Text disabled>
                                            <Space>
                                                <FaSquare/>
                                                Finished
                                            </Space>
                                        </Typography.Text>
                                    }
                                </>,
                        },
                        {
                            key: 'state',
                            title: "State",
                            width: 130,
                            render: (_, entry) => <AutoVersioningAuditEntryState status={entry.mostRecentState}/>,
                        },
                        {
                            key: 'pr',
                            title: "PR",
                            render: (_, entry) => <AutoVersioningAuditEntryPR entry={entry} displayStatus={true}/>,
                        },
                        {
                            key: 'timestamp',
                            title: "Timestamp",
                            render: (_, entry) => <TimestampText
                                value={entry.mostRecentState.creation.time}
                                format="YYYY MMM DD, HH:mm:ss"
                            />,
                        },
                        {
                            key: 'duration',
                            title: "Duration",
                            render: (_, entry) =>
                                <Duration
                                    displaySeconds={true}
                                    seconds={Math.floor(entry.duration / 1000)}
                                />,
                        }
                    ].filter(col => visibleColumns.includes(col.key))}
                />
            </Space>
        </>
    )
}