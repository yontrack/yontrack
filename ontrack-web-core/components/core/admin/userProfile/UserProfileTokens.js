import {Button, Form, Input, Space, Table, Typography} from "antd";
import {FaCog} from "react-icons/fa";
import CheckStatus from "@components/common/CheckStatus";
import TimestampText, {weekDayFormat} from "@components/common/TimestampText";
import FormErrors from "@components/form/FormErrors";
import InlineConfirmCommand from "@components/common/InlineConfirmCommand";
import {useRefresh} from "@components/common/RefreshUtils";
import {useGenerateToken, useRevokeToken, useTokens} from "@components/core/admin/userProfile/TokensService";

const {Column} = Table

export default function UserProfileTokens() {

    const [refreshState, refresh] = useRefresh()

    const {tokens, loading} = useTokens({refreshState})

    const [generateTokenForm] = Form.useForm()

    const {generateToken, data, loading: generatingToken, error} = useGenerateToken({refresh})
    const onGenerateToken = async ({name}) => {
        if (name) {
            generateTokenForm.resetFields()
            await generateToken({name})
        }
    }

    const {revokeToken} = useRevokeToken({refresh})
    const onRevokeToken = async (token) => {
        await revokeToken({name: token.name})
    }

    return (
        <>
            <Space orientation="vertical" className="ot-line">
                <Form layout="inline" form={generateTokenForm} onFinish={onGenerateToken}>
                    <Form.Item
                        name="name"
                        rules={[{required: true, message: 'Token name is required'}]}
                    >
                        <Input placeholder="Token name"/>
                    </Form.Item>
                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            disabled={generatingToken}
                            loading={generatingToken}
                            icon={<FaCog/>}
                        >
                            Generate token
                        </Button>
                    </Form.Item>
                    {
                        data?.token?.value &&
                        <Form.Item>
                            <Space>
                                <Typography.Text data-testid="generatedToken" code={true} copyable={true}>
                                    {data.token.value}
                                </Typography.Text>
                            </Space>
                        </Form.Item>
                    }
                </Form>
                <FormErrors errors={error ? [error] : []}/>
                <Table
                    dataSource={tokens}
                    pagination={false}
                    loading={loading}
                >
                    <Column
                        key="name"
                        title="Name"
                        dataIndex="name"
                    />
                    <Column
                        key="scope"
                        title="Scope"
                        dataIndex="scope"
                    />
                    <Column
                        key="creation"
                        title="Creation"
                        dataIndex="creation"
                        render={(value) => <TimestampText value={value} format={weekDayFormat}/>}
                    />
                    <Column
                        key="lastUsed"
                        title="Last used"
                        dataIndex="lastUsed"
                        render={(value) => <TimestampText value={value} format={weekDayFormat}/>}
                    />
                    <Column
                        key="validUntil"
                        title="Valid until"
                        dataIndex="validUntil"
                        render={(validUntil, token) => {
                            return <Space>
                                {
                                    validUntil && <TimestampText value={validUntil} format={weekDayFormat}/>
                                }
                                {
                                    validUntil && <CheckStatus
                                        value={token.valid}
                                        text="Valid"
                                        noText="Expired"
                                    />
                                }
                                {
                                    !validUntil && <CheckStatus value={true} text="Does not expire"/>
                                }
                            </Space>
                        }}
                    />
                    <Column
                        key="actions"
                        title=""
                        render={(_, token) =>
                            token.transient ||
                            <InlineConfirmCommand
                                title="Revoking the token"
                                confirm={`Are you sure to revoke the "${token.name}" token?`}
                                onConfirm={() => onRevokeToken(token)}
                            />
                        }
                    />
                </Table>
            </Space>
        </>
    )
}