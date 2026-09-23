import {Descriptions, Space, Tag, Typography} from "antd";
import {acceptanceSummary} from "@components/extension/findings/findingsModel";

/**
 * Whether an acceptance holds, in one tag.
 */
export function FindingAcceptanceTag({acceptance, testId}) {
    return (
        <Tag
            color={!acceptance ? 'default' : acceptance.effective ? 'warning' : 'error'}
            title={acceptance?.statement ?? undefined}
            data-testid={testId}
        >
            {acceptanceSummary(acceptance)}
        </Tag>
    )
}

/**
 * The acceptance of a finding, as recorded outside Yontrack and read from the most recent
 * observation of the finding: whether it holds, why the finding is tolerated, until when, and
 * where the decision is recorded.
 */
export default function FindingAcceptance({acceptance}) {
    return (
        <Space orientation="vertical" style={{width: '100%'}}>
            <FindingAcceptanceTag acceptance={acceptance} testId="finding-acceptance-summary"/>
            {
                acceptance &&
                <Descriptions
                    data-testid="finding-acceptance"
                    size="small"
                    column={1}
                    bordered
                    items={[
                        {
                            key: 'statement',
                            label: 'Statement',
                            children: acceptance.statement ??
                                <Typography.Text type="secondary">No statement</Typography.Text>,
                        },
                        {
                            key: 'expiresAt',
                            label: 'Expiry',
                            children: acceptance.expiresAt ??
                                <Typography.Text type="secondary">None</Typography.Text>,
                        },
                        {
                            key: 'source',
                            label: 'Recorded in',
                            children: acceptance.source ?
                                <Typography.Text code>{acceptance.source}</Typography.Text> :
                                <Typography.Text type="secondary">Unknown</Typography.Text>,
                        },
                    ]}
                />
            }
            {
                !acceptance &&
                <Typography.Text type="secondary">
                    The most recent observation of this finding carries no acceptance.
                </Typography.Text>
            }
        </Space>
    )
}
