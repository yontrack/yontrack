"use client"

/**
 * Overriding an admission rule which blocks a deployment, from a phone.
 *
 * The desktop equivalent is `SlotPipelineOverrideRuleDialog`: a warning, a
 * required justification, and the `overridePipelineRule` mutation. The mutation
 * and the requirement are the same here; the modal is not, for the reason every
 * mobile surface is not a desktop one.
 *
 * **The message is required and the warning is kept.** An override bypasses a
 * control somebody configured on purpose and is recorded against the user who
 * made it, so the phone must say so as plainly as the desktop does - a smaller
 * screen is a reason to be shorter, not a reason to be quieter about the one
 * irreversible thing on it.
 *
 * @param {Object} deployment The deployment being acted on - its `id`.
 * @param {Object} rule The blocking rule, as `SlotPipelineAdmissionRuleStatus` -
 *   its `admissionRuleConfig.id` is what the mutation overrides.
 * @param {boolean} open Whether the sheet is up.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onOverridden Called once the server has recorded it, so the
 *   screen behind can refetch: whether the deployment can now run is the
 *   server's answer.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Alert, Button, Drawer, Form, Input, Space} from "antd"
import {callGraphQL} from "@components/services/GraphQL"
import {MobileAdmissionRuleSummary} from "@components/mobile/deployments/admissionRuleComponents"

export default function MobileDeploymentOverrideSheet({deployment, rule, open, onClose, onOverridden}) {
    return (
        <Drawer
            placement="bottom"
            height="auto"
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Override the rule"
            open={open}
            onClose={onClose}
            // A justification typed for a rule the user then decided not to
            // override must not be offered again for a different rule.
            destroyOnClose
        >
            {
                rule &&
                <MobileDeploymentOverrideForm
                    deployment={deployment}
                    rule={rule}
                    onClose={onClose}
                    onOverridden={onOverridden}
                />
            }
        </Drawer>
    )
}

function MobileDeploymentOverrideForm({deployment, rule, onClose, onOverridden}) {

    const [form] = Form.useForm()
    const [running, setRunning] = useState(false)
    const [error, setError] = useState(null)

    const onSubmit = async (values) => {
        setRunning(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileOverrideDeploymentRule(
                        $pipelineId: String!,
                        $admissionRuleConfigId: String!,
                        $message: String!,
                    ) {
                        overridePipelineRule(input: {
                            pipelineId: $pipelineId,
                            admissionRuleConfigId: $admissionRuleConfigId,
                            message: $message,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    pipelineId: deployment.id,
                    admissionRuleConfigId: rule.admissionRuleConfig.id,
                    message: values.message,
                },
            })
            const errors = data?.overridePipelineRule?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else {
                onOverridden?.()
                onClose?.()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setRunning(false)
        }
    }

    return (
        <Form
            form={form}
            layout="vertical"
            size="large"
            onFinish={onSubmit}
        >
            <Alert
                type="warning"
                showIcon
                message="Overriding this rule may bypass some controls. The override is recorded against your name."
                style={{marginBottom: 16}}
                data-testid="mobile-deployment-override-warning"
            />

            {/* Which rule, phrased the way the list behind phrases it. */}
            <Form.Item label="Rule">
                <MobileAdmissionRuleSummary rule={rule.admissionRuleConfig}/>
            </Form.Item>

            <Form.Item
                name="message"
                label="Reason"
                extra="Why this rule is being bypassed."
                rules={[{required: true, message: 'Reason is required.'}]}
            >
                <Input.TextArea rows={2} data-testid="mobile-deployment-override-message"/>
            </Form.Item>

            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    message={error}
                    data-testid="mobile-deployment-override-error"
                    style={{marginBottom: 16}}
                />
            }

            <Space direction="vertical" size="small" style={{width: '100%'}}>
                <Button
                    block
                    type="primary"
                    danger
                    htmlType="submit"
                    loading={running}
                    data-testid="mobile-deployment-override-submit"
                >
                    Override
                </Button>
                <Button block onClick={onClose} data-testid="mobile-deployment-override-cancel">
                    Cancel
                </Button>
            </Space>
        </Form>
    )
}
