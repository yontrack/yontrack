import {Alert, Button, Form, Input, Popconfirm, Space, Typography} from "antd";
import {FaBan, FaCog} from "react-icons/fa";
import Table from "@components/common/table/Table";
import TimestampText, {weekDayFormat} from "@components/common/TimestampText";
import CheckStatus from "@components/common/CheckStatus";
import FormErrors from "@components/form/FormErrors";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {
    useGenerateAgentToken,
    useRevokeAgentToken,
    useRevokeAllAgentTokens
} from "@components/core/admin/agents/AgentsService";

/**
 * Tokens of an agent: generation, with the value shown once, list and revocation.
 */
export default function AgentTokens({agent, refresh}) {

    const [form] = Form.useForm()

    const {generateAgentToken, data, loading: generating, error} = useGenerateAgentToken({onSuccess: refresh})
    const onGenerate = async ({name}) => {
        form.resetFields()
        await generateAgentToken(agent, name)
    }

    const {revokeAgentToken} = useRevokeAgentToken({onSuccess: refresh})
    const {revokeAllAgentTokens, loading: revokingAll} = useRevokeAllAgentTokens({onSuccess: refresh})

    const tokens = agent.tokens ?? []

    return (
        <Space orientation="vertical" className="ot-line">
            <Form layout="inline" form={form} onFinish={onGenerate}>
                <Form.Item
                    name="name"
                    label="Token name"
                    rules={[{required: true, message: 'The token name is required.'}]}
                >
                    <Input placeholder="ci, laptop..."/>
                </Form.Item>
                <Form.Item>
                    <Button
                        type="primary"
                        htmlType="submit"
                        disabled={generating}
                        loading={generating}
                        icon={<FaCog/>}
                    >
                        Generate token
                    </Button>
                </Form.Item>
                {
                    tokens.length > 0 &&
                    <Form.Item>
                        <Popconfirm
                            title="Revoking all the tokens"
                            description={`Revoke all the tokens of ${agent.fullName}? Whatever uses them stops working.`}
                            okText="Revoke all"
                            onConfirm={() => revokeAllAgentTokens(agent)}
                        >
                            <Button
                                danger={true}
                                icon={<FaBan/>}
                                loading={revokingAll}
                            >
                                Revoke all tokens
                            </Button>
                        </Popconfirm>
                    </Form.Item>
                }
            </Form>
            {
                data?.token?.value &&
                <Alert
                    type="success"
                    showIcon={true}
                    title={`Token "${data.token.name}" generated`}
                    description={
                        <Space orientation="vertical">
                            <Typography.Text>
                                Copy it now: its value is shown only once.
                            </Typography.Text>
                            <Typography.Text
                                data-testid="generatedAgentToken"
                                code={true}
                                copyable={{tooltips: ["Copy the token", "Copied"]}}
                            >
                                {data.token.value}
                            </Typography.Text>
                        </Space>
                    }
                />
            }
            <FormErrors errors={error ? [error] : []}/>
            <Table
                data-testid="agent-tokens"
                dataSource={tokens}
                rowKey="name"
                pagination={false}
                locale={{emptyText: "No token for this agent."}}
            >
                <Table.Column
                    key="name"
                    title="Name"
                    dataIndex="name"
                />
                <Table.Column
                    key="creation"
                    title="Creation"
                    dataIndex="creation"
                    render={(value) => <TimestampText value={value} format={weekDayFormat}/>}
                />
                <Table.Column
                    key="lastUsed"
                    title="Last used"
                    dataIndex="lastUsed"
                    render={(value) => value ?
                        <TimestampText value={value} format={weekDayFormat}/> :
                        <Typography.Text type="secondary">Never</Typography.Text>
                    }
                />
                <Table.Column
                    key="validUntil"
                    title="Valid until"
                    dataIndex="validUntil"
                    render={(validUntil, token) =>
                        <Space>
                            {
                                validUntil && <TimestampText value={validUntil} format={weekDayFormat}/>
                            }
                            {
                                validUntil && <CheckStatus value={token.valid} text="Valid" noText="Expired"/>
                            }
                            {
                                !validUntil && <CheckStatus value={true} text="Does not expire"/>
                            }
                        </Space>
                    }
                />
                <Table.Column
                    key="actions"
                    title="Actions"
                    render={(_, token) =>
                        <InlineConfirmCommand
                            title={`Revoke the token ${token.name}`}
                            confirm={`Revoke the "${token.name}" token? Whatever uses it stops working.`}
                            onConfirm={() => revokeAgentToken(agent, token.name)}
                        />
                    }
                />
            </Table>
        </Space>
    )
}
