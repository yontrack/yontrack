import {Button, Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import TimestampText from "@components/common/TimestampText";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import {rankedFindingProjectsText} from "@components/extension/findings/findingFanOutModel";

/**
 * Findings, one row per external ID, with the number of projects where each is open, accepted and
 * resolved: a click on a row opens the fan-out of its finding.
 *
 * @param rows Rows of the findings, from `rankedFindingRows`
 * @param caption What the list shows
 * @param testId Prefix of the test IDs of the list, of its caption and of its rows
 * @param onSelect Called with the external ID of the finding whose row is clicked
 */
export default function FindingsRankTable({rows, caption, testId, onSelect}) {

    const columns = [
        {
            key: 'finding',
            title: 'Finding',
            render: (_, row) =>
                <Space orientation="vertical" size={0}>
                    {/* A button: the click stays on the page, which keeps the finding in its URL */}
                    <Button
                        type="link"
                        size="small"
                        style={{padding: 0, height: 'auto'}}
                        onClick={(event) => {
                            event.stopPropagation()
                            onSelect(row.externalId)
                        }}
                    >
                        <Typography.Text code>{row.externalId}</Typography.Text>
                    </Button>
                    <Typography.Text>{row.title}</Typography.Text>
                </Space>,
        },
        {
            key: 'severity',
            title: 'Severity',
            render: (_, row) => <FindingSeverityTag severity={row.severity}/>,
        },
        {
            key: 'projects',
            title: 'Projects',
            render: (_, row) =>
                <span data-testid={`${testId}-projects-${row.externalId}`} style={{whiteSpace: 'nowrap'}}>
                    {rankedFindingProjectsText(row)}
                </span>,
        },
        {
            key: 'firstSeen',
            title: 'First seen',
            render: (_, row) => <TimestampText value={row.firstSeen} relative={true}/>,
        },
    ]

    return (
        <Space orientation="vertical" size={8} style={{width: '100%'}}>
            <SecondaryText data-testid={`${testId}-findings-caption`}>{caption}</SecondaryText>
            <Table
                data-testid={`${testId}-findings`}
                size="small"
                rowKey="key"
                columns={columns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 'max-content'}}
                onRow={row => ({
                    'data-testid': `${testId}-finding-${row.externalId}`,
                    onClick: () => onSelect(row.externalId),
                    style: {cursor: 'pointer'},
                })}
            />
        </Space>
    )
}
