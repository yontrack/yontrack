import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, Input, InputNumber} from "antd";

export default function BitbucketCloudAvPostProcessingForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="config"
                    label="Configuration"
                    extra="Default Bitbucket Cloud configuration to use for the connection."
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="workspace"
                    label="Workspace"
                    extra="Default workspace of the repository containing the pipeline"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="repository"
                    label="Repository"
                    extra="Default repository containing the pipeline"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="pipeline"
                    label="Pipeline"
                    extra={<>Name of the custom pipeline containing the post-processing
                        (like <code>yontrack-auto-versioning</code>)</>}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="branch"
                    label="Branch"
                    extra="Branch to run the pipeline on"
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="retries"
                    label="Retries"
                    extra="The amount of times we check for the completion of the post-processing pipeline"
                >
                    <InputNumber min={1} max={500}/>
                </Form.Item>
                <Form.Item
                    name="retriesDelaySeconds"
                    label="Retry interval"
                    extra="The time (in seconds) between two checks for the completion of the post-processing pipeline, never less than 10 seconds"
                >
                    <InputNumber min={10} max={300}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
