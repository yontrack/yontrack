import {useState} from "react";
import {Alert, Button, Form, Input, Modal, Space, Typography, Upload} from "antd";
import {FaUpload} from "react-icons/fa";
import {validationRunEvidenceUploadUri} from "@components/common/Links";
import {uploadErrorText} from "@components/extension/audit-trail/evidenceModel";

/**
 * Optional fields of an upload, sent when filled: where the evidence comes from.
 */
const SOURCE_FIELDS = ['sourceTool', 'sourceVersion', 'sourceUrl']

/**
 * Upload of an evidence to a validation run: a file, and optionally where it comes from.
 *
 * The file is sent as it is to the upload route of the UI, which streams it to the backend: the
 * server computes its SHA-256, checks its size against the limit of the instance, and answers a
 * refusal with a message, which the dialog shows.
 *
 * @param run Validation run
 * @param open Whether the dialog is open
 * @param onClose Called when the dialog is closed without uploading
 * @param onUploaded Called with the attached evidence
 */
export default function EvidenceUploadDialog({run, open, onClose, onUploaded}) {

    const [form] = Form.useForm()
    const [uploading, setUploading] = useState(false)
    const [error, setError] = useState(null)

    const close = () => {
        form.resetFields()
        setError(null)
        onClose()
    }

    const onFinish = async (values) => {
        setError(null)
        setUploading(true)
        try {
            const body = new FormData()
            const file = values.file[0].originFileObj
            body.append('file', file, file.name)
            SOURCE_FIELDS.forEach(field => {
                const value = values[field]?.trim()
                if (value) {
                    body.append(field, value)
                }
            })
            const response = await fetch(validationRunEvidenceUploadUri(run), {
                method: 'POST',
                body,
            })
            const answer = await response.json().catch(() => null)
            if (response.ok) {
                form.resetFields()
                onUploaded(answer)
            } else {
                setError(uploadErrorText(response.status, answer))
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setUploading(false)
        }
    }

    return (
        <Modal
            open={open}
            title="Upload evidence"
            onCancel={close}
            footer={null}
            destroyOnHidden
        >
            <Form
                form={form}
                layout="vertical"
                onFinish={onFinish}
                data-testid="evidence-upload-form"
            >
                <Form.Item
                    name="file"
                    label="File"
                    valuePropName="fileList"
                    getValueFromEvent={(e) => e?.fileList}
                    rules={[{required: true, message: 'A file is required.'}]}
                    extra="Stored as it is: its SHA-256 is computed by the server and recorded in the audit trail of the build."
                >
                    <Upload
                        maxCount={1}
                        // Kept here until the form is submitted
                        beforeUpload={() => false}
                    >
                        <Button icon={<FaUpload aria-hidden="true"/>}>Choose a file</Button>
                    </Upload>
                </Form.Item>
                <Typography.Paragraph type="secondary">
                    Where the evidence comes from — optional.
                </Typography.Paragraph>
                <Form.Item name="sourceTool" label="Source tool">
                    <Input placeholder="trivy"/>
                </Form.Item>
                <Form.Item name="sourceVersion" label="Source version">
                    <Input placeholder="0.50.1"/>
                </Form.Item>
                <Form.Item
                    name="sourceUrl"
                    label="Source URL"
                    rules={[{
                        pattern: /^https?:\/\//i,
                        message: 'The source URL must be an HTTP or HTTPS URL.',
                    }]}
                >
                    <Input placeholder="https://ci.example.com/job/1234"/>
                </Form.Item>
                {
                    error &&
                    <Form.Item>
                        <Alert type="error" showIcon title={error} data-testid="evidence-upload-error"/>
                    </Form.Item>
                }
                <Form.Item style={{marginBottom: 0}}>
                    <Space style={{float: 'right'}}>
                        <Button onClick={close}>Cancel</Button>
                        <Button type="primary" htmlType="submit" loading={uploading}>Upload</Button>
                    </Space>
                </Form.Item>
            </Form>
        </Modal>
    )
}
