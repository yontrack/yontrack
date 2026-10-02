import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import {useQuery} from "@components/services/GraphQL";
import LabelChip from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import {estateScorecardUri} from "@components/common/Links";
import {estateMarkerText} from "@components/extension/scorecard/estates/estateModel";

export const gqlScorecards = gql`
    query Scorecards {
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
            projects {
                id
            }
        }
    }
    ${gqlLabelFragment}
`

/**
 * The estates, each linking to its scorecard: there is no estate of all the projects, whose
 * scorecards are on their own pages.
 */
export default function ScorecardsView() {

    const {data: estates, loading, error} = useQuery(gqlScorecards, {
        dataFn: data => data.estates,
        initialData: [],
    })

    const columns = [
        {
            key: 'name',
            title: 'Estate',
            render: (_, estate) =>
                <Space orientation="vertical" size={0}>
                    <Link href={estateScorecardUri(estate)} data-testid={`scorecards-estate-${estate.name}`}>
                        {estate.name}
                    </Link>
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
                <Space size={4} wrap>
                    {estate.labels.map(label => <LabelChip key={label.id} label={label}/>)}
                </Space>,
        },
        {
            key: 'marker',
            title: 'Marker',
            render: (_, estate) =>
                <Typography.Text data-testid={`scorecards-marker-${estate.name}`}>
                    {estateMarkerText(estate.marker)}
                </Typography.Text>,
        },
        {
            key: 'projects',
            title: 'Projects',
            render: (_, estate) => {
                const count = estate.projects?.length ?? 0
                return (
                    <Typography.Text data-testid={`scorecards-projects-${estate.name}`}>
                        {count === 1 ? '1 project' : `${count} projects`}
                    </Typography.Text>
                )
            },
        },
    ]

    return (
        <Space orientation="vertical" style={{width: '100%'}}>
            {
                error &&
                <Alert type="warning" showIcon title={error}/>
            }
            <Table
                data-testid="scorecards"
                loading={loading}
                dataSource={estates ?? []}
                columns={columns}
                rowKey="id"
                pagination={false}
                locale={{emptyText: 'No estate yet: an estate groups projects by labels to read them together.'}}
            />
        </Space>
    )
}
