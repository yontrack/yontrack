import {useState} from "react"
import {Button, Input, Modal, Space, Typography} from "antd"
import {FaTimesCircle} from "react-icons/fa"

/**
 * "Mark as failed" - the other way out of a running deployment, beside "Finish".
 *
 * A deployment which was started and did not make it is `FAILED`: terminal, and not changing what
 * the slot runs. It is usually reported by the CI which ran the deployment; this is the same
 * action for a person, typically when that CI died before it could say so.
 *
 * It asks for confirmation because it cannot be undone, and offers a message there because the
 * message is what the deployment's timeline will say about *why* - optional, as it is on the API.
 * The button is never disabled by the deployment's checks: a running deployment whose workflow is
 * stuck is exactly the one somebody may want to declare failed.
 *
 * @param {boolean} acting Whether an action is in flight
 * @param {function} onFail Called with the message, or `null` when none was written
 * @param {string} testId Test id of the button; the dialog's controls derive theirs from it
 */
export default function FailDeploymentButton({acting, onFail, testId = 'deployment-fail'}) {

    const [open, setOpen] = useState(false)
    const [message, setMessage] = useState('')

    const start = () => {
        setMessage('')
        setOpen(true)
    }

    const confirm = () => {
        setOpen(false)
        const trimmed = message.trim()
        onFail(trimmed ? trimmed : null)
    }

    return (
        <>
            <Button
                danger
                icon={<FaTimesCircle/>}
                loading={acting}
                data-testid={testId}
                onClick={start}
            >
                Mark as failed
            </Button>
            <Modal
                open={open}
                title="Mark the deployment as failed"
                okText="Mark as failed"
                okButtonProps={{danger: true, 'data-testid': `${testId}-confirm`}}
                onOk={confirm}
                onCancel={() => setOpen(false)}
                destroyOnHidden
            >
                <Space orientation="vertical" className="ot-line">
                    <Typography.Text type="secondary">
                        A failed deployment is final. The build deployed before it stays the deployed one.
                    </Typography.Text>
                    <Input.TextArea
                        aria-label="Failure message"
                        placeholder="Why did it fail? (optional)"
                        value={message}
                        onChange={event => setMessage(event.target.value)}
                        data-testid={`${testId}-message`}
                        autoSize={{minRows: 2, maxRows: 6}}
                    />
                </Space>
            </Modal>
        </>
    )
}
