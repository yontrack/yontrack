import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Popover, Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import {useQuery} from "@components/services/GraphQL";
import LabelChip from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import TimestampText from "@components/common/TimestampText";
import {projectScorecardUri} from "@components/common/Links";
import {
    EstateDeleteCommand,
    EstateRecomputeCommand,
    EstateUpdateCommand,
    useEstateRecompute,
} from "@components/extension/scorecard/estates/EstateCommands";
import {
    estateMarkerText,
    estateReadingConfigTexts,
    latestEstateComputedAt,
} from "@components/extension/scorecard/estates/estateModel";

export const gqlEstates = gql`
    query Estates {
        estates {
            id
            name
            description
            labels {
                ...labelFragment
            }
            marker {
                kind
                levelName
                environment
                qualifier
            }
            readingConfigs {
                key
                windowDays
                target
                direction
            }
            projects {
                id
                name
                scorecard {
                    sets {
                        estate {
                            id
                        }
                        readings {
                            computedAt
                        }
                    }
                }
            }
        }
    }

    ${gqlLabelFragment}
`

function EstateProjects({estate}) {
    const projects = estate.projects ?? []
    const count = <Typography.Text data-testid={`estate-projects-${estate.name}`}>
        {projects.length === 1 ? '1 project' : `${projects.length} projects`}
    </Typography.Text>
    if (projects.length === 0) return count
    return (
        <Popover
            title="Projects of the estate"
            content={
                <Space orientation="vertical" size={4}>
                    {
                        projects.map(project =>
                            <Link key={project.id} href={projectScorecardUri(project)}>{project.name}</Link>
                        )
                    }
                </Space>
            }
        >
            {count}
        </Popover>
    )
}

function EstateReadings({estate}) {
    const configs = estateReadingConfigTexts(estate.readingConfigs)
    if (configs.length === 0) {
        return <Typography.Text type="secondary">Default windows, no target</Typography.Text>
    }
    return (
        <Space orientation="vertical" size={0} data-testid={`estate-readings-${estate.name}`}>
            {
                configs.map(({key, name, text}) =>
                    <Typography.Text key={key} data-testid={`estate-reading-${estate.name}-${key}`}>
                        {name}: {text}
                    </Typography.Text>
                )
            }
        </Space>
    )
}

/**
 * Last computation of an estate, and its commands, which share the state of its recompute.
 */
function EstateRow({estate, refresh, children}) {
    const {recompute, polling, error} = useEstateRecompute({estate, refresh})
    return children({recompute, polling, error})
}

/**
 * The estates, one row each: what selects their projects, the marker, the windows and targets of
 * their readings, their projects and their last computation; and, for the users who can manage
 * them, the commands to edit, recompute and delete them.
 */
export default function EstatesView({refreshState, refresh, canEdit, dialog}) {

    const {data: estates, loading, error} = useQuery(gqlEstates, {
        deps: [refreshState],
        dataFn: data => data.estates,
        initialData: [],
    })

    const columns = [
        {
            key: 'name',
            title: 'Estate',
            render: (_, estate) =>
                <Space orientation="vertical" size={0}>
                    <Typography.Text strong data-testid={`estate-name-${estate.name}`}>{estate.name}</Typography.Text>
                    {
                        estate.description &&
                        <Typography.Text type="secondary">{estate.description}</Typography.Text>
                    }
                </Space>,
        },
        {
            key: 'labels',
            title: 'Labels',
            render: (_, estate) =>
                <Space size={4} wrap data-testid={`estate-labels-${estate.name}`}>
                    {estate.labels.map(label => <LabelChip key={label.id} label={label}/>)}
                </Space>,
        },
        {
            key: 'marker',
            title: 'Marker',
            render: (_, estate) =>
                <Typography.Text data-testid={`estate-marker-${estate.name}`}>
                    {estateMarkerText(estate.marker)}
                </Typography.Text>,
        },
        {
            key: 'readings',
            title: 'Windows and targets',
            render: (_, estate) => <EstateReadings estate={estate}/>,
        },
        {
            key: 'projects',
            title: 'Projects',
            render: (_, estate) => <EstateProjects estate={estate}/>,
        },
        {
            key: 'computed',
            title: 'Computed',
            render: (_, estate) => {
                const computedAt = latestEstateComputedAt(estate)
                return (
                    <span data-testid={`estate-computed-${estate.name}`}>
                        {
                            computedAt ?
                                <TimestampText value={computedAt} relative={true}/> :
                                <Typography.Text type="secondary">Never</Typography.Text>
                        }
                    </span>
                )
            },
        },
        ...(canEdit ? [{
            key: 'actions',
            title: 'Actions',
            render: (_, estate) =>
                <EstateRow estate={estate} refresh={refresh}>
                    {({recompute, polling, error}) =>
                        <Space orientation="vertical" size={0}>
                            <Space size={0}>
                                <EstateUpdateCommand estate={estate} dialog={dialog}/>
                                <EstateRecomputeCommand estate={estate} recompute={recompute} polling={polling}/>
                                <EstateDeleteCommand estate={estate} onChange={refresh}/>
                            </Space>
                            {
                                error &&
                                <Typography.Text type="danger">{error}</Typography.Text>
                            }
                        </Space>
                    }
                </EstateRow>,
        }] : []),
    ]

    return (
        <Space orientation="vertical" style={{width: '100%'}}>
            {
                error &&
                <Alert type="warning" showIcon title={error}/>
            }
            <Table
                data-testid="estates"
                loading={loading}
                dataSource={estates ?? []}
                columns={columns}
                rowKey="id"
                pagination={false}
                locale={{emptyText: 'No estate'}}
            />
        </Space>
    )
}
