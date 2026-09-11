"use client"

/**
 * Answering the input a deployment is waiting for, from a phone.
 *
 * A deployment can sit in `CANDIDATE` because one of its admission rules needs
 * something from a person - a manual approval and its message, today. Until that
 * arrives the deployment cannot run, so a phone that could not answer it could
 * start a deployment and then be unable to finish starting it.
 *
 * **The fields are the desktop's own.** Which controls a rule asks for is the
 * rule's own `DataForm`, the very component the desktop `SlotPipelineInputDialog`
 * draws - reached through `admissionRuleComponents`, which is where the mobile UI
 * keeps its rule-id lookup and which explains why it cannot use the desktop's
 * runtime one.
 *
 * **The value shape is the server's, not ours.** Each rule's form names its
 * fields under the rule config's id, so the form's own values are already
 * `{[configId]: {...}}` and become `[{configId, data}]` untouched. Reshaping
 * them here would mean knowing what each rule's data looks like, which is
 * exactly what the shared mapping exists to avoid.
 *
 * @param {Object} deployment The deployment - its `id`, and the `requiredInputs`
 *   it is waiting for.
 * @param {string} [ruleConfigId] Answer one rule rather than all of them, when
 *   the sheet was opened from a rule's own row.
 * @param {boolean} open Whether the sheet is up.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onSaved Called once the server has taken the input, so the
 *   screen behind can refetch: whether a rule now passes, and whether the
 *   deployment can run, are the server's answers and not the phone's.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Alert, Button, Drawer, Form, Space, Typography} from "antd"
import {callGraphQL} from "@components/services/GraphQL"
import {MobileAdmissionRuleDataForm} from "@components/mobile/deployments/admissionRuleComponents"

export default function MobileDeploymentInputSheet({deployment, ruleConfigId, open, onClose, onSaved}) {
    return (
        <Drawer
            placement="bottom"
            height="auto"
            // Capped in both halves - the wrapper's overflow is visible, so a
            // capped wrapper around uncapped content hangs out of the bottom of
            // it exactly as if there were no cap. See `MobilePromoteSheet`.
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Deployment input"
            open={open}
            onClose={onClose}
            // A half-typed approval message the user decided against must not
            // come back the next time the sheet is opened.
            destroyOnClose
        >
            <MobileDeploymentInputForm
                deployment={deployment}
                ruleConfigId={ruleConfigId}
                onClose={onClose}
                onSaved={onSaved}
            />
        </Drawer>
    )
}

function MobileDeploymentInputForm({deployment, ruleConfigId, onClose, onSaved}) {

    const [form] = Form.useForm()
    const [running, setRunning] = useState(false)
    const [error, setError] = useState(null)

    /*
     * Derived from the deployment the screen already has, not fetched again: the
     * screen's own query asks for `requiredInputs` because it needs to know
     * whether to offer this sheet at all, and a second query for the same field
     * could answer differently from the row the user tapped.
     */
    const inputs = (deployment?.requiredInputs ?? []).filter(input =>
        !ruleConfigId || input.config.id === ruleConfigId
    )

    const onSubmit = async (values) => {
        setRunning(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileDeploymentInput(
                        $pipelineId: String!,
                        $values: [SlotPipelineDataInputValue!]!,
                    ) {
                        updatePipelineData(input: {pipelineId: $pipelineId, values: $values}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    pipelineId: deployment.id,
                    values: Object.keys(values ?? {}).map(configId => ({
                        configId,
                        data: values[configId],
                    })),
                },
            })
            const errors = data?.updatePipelineData?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else {
                onSaved?.()
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
            // One column of full-width controls: at 375px a label beside a
            // control leaves neither enough room.
            layout="vertical"
            size="large"
            onFinish={onSubmit}
            data-testid="mobile-deployment-input-form"
        >
            {
                inputs.map(input => (
                    <div key={input.config.id} data-testid={`mobile-deployment-input-${input.config.id}`}>
                        {
                            /*
                             * The rule's name above its own fields. A deployment
                             * can be waiting on two rules at once, and a phone
                             * screen of unlabelled controls would say nothing
                             * about which answer belongs to which rule.
                             */
                            <Typography.Text strong>{input.config.name}</Typography.Text>
                        }
                        {
                            input.config.description &&
                            <Typography.Paragraph type="secondary" className="ot-mobile-caption">
                                {input.config.description}
                            </Typography.Paragraph>
                        }
                        <MobileAdmissionRuleDataForm config={input.config}/>
                    </div>
                ))
            }

            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    message={error}
                    data-testid="mobile-deployment-input-error"
                    style={{marginBottom: 16}}
                />
            }

            <Space direction="vertical" size="small" style={{width: '100%'}}>
                <Button
                    block
                    type="primary"
                    htmlType="submit"
                    loading={running}
                    data-testid="mobile-deployment-input-submit"
                >
                    Save
                </Button>
                <Button block onClick={onClose} data-testid="mobile-deployment-input-cancel">
                    Cancel
                </Button>
            </Space>
        </Form>
    )
}
