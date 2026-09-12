"use client"

/**
 * Cancelling a deployment, from a phone.
 *
 * **The reason is required, as on the desktop.** That reason is the only record
 * of why an environment's deployment was killed, and the desktop reads it back
 * beside a `CANCELLED` pipeline (`lastChange { message }`); a phone cancelling
 * without one would open a mobile-only data gap, which is worse than the friction
 * of a soft keyboard.
 *
 * The shape is the override sheet's, down to its "Reason is required." - the two
 * are the same question asked about two different things, and a user who has met
 * one should recognise the other.
 *
 * @param {Object} deployment The deployment being cancelled - its `id`.
 * @param {boolean} open Whether the sheet is up.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onCancelled Called once the server has recorded it, so the
 *   screen behind can refetch.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Alert, Button, Drawer, Form, Input, Space} from "antd"
import {callGraphQL} from "@components/services/GraphQL"

export default function MobileDeploymentCancelSheet({deployment, open, onClose, onCancelled}) {
    return (
        <Drawer
            placement="bottom"
            height="auto"
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Cancel the deployment"
            open={open}
            onClose={onClose}
            // A reason typed for a deployment the user then decided to keep must
            // not be offered again the next time the sheet comes up.
            destroyOnClose
        >
            {
                deployment &&
                <MobileDeploymentCancelForm
                    deployment={deployment}
                    onClose={onClose}
                    onCancelled={onCancelled}
                />
            }
        </Drawer>
    )
}

function MobileDeploymentCancelForm({deployment, onClose, onCancelled}) {

    const [form] = Form.useForm()
    const [running, setRunning] = useState(false)
    const [error, setError] = useState(null)

    const onSubmit = async (values) => {
        setRunning(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileCancelDeployment($pipelineId: String!, $reason: String!) {
                        cancelSlotPipeline(input: {pipelineId: $pipelineId, reason: $reason}) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    pipelineId: deployment.id,
                    reason: values.reason,
                },
            })
            const errors = data?.cancelSlotPipeline?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else {
                onCancelled?.()
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
                message="Cancelling a deployment cannot be undone. The reason is kept with it."
                style={{marginBottom: 16}}
                data-testid="mobile-deployment-cancel-warning"
            />

            <Form.Item
                name="reason"
                label="Reason"
                extra="Why this deployment is being cancelled."
                rules={[{required: true, message: 'Reason is required.'}]}
            >
                <Input.TextArea rows={2} data-testid="mobile-deployment-cancel-reason"/>
            </Form.Item>

            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    message={error}
                    data-testid="mobile-deployment-cancel-error"
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
                    data-testid="mobile-deployment-cancel-submit"
                >
                    Cancel the deployment
                </Button>
                {/*
                  "Keep it" and not "Cancel", which in this sheet would mean the
                  opposite of the button above it.
                */}
                <Button block onClick={onClose} data-testid="mobile-deployment-cancel-close">
                    Keep it
                </Button>
            </Space>
        </Form>
    )
}
