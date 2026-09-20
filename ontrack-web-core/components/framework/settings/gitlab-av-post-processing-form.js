import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, Input, InputNumber} from "antd";

export default function GitLabAvPostProcessingForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="config"
                    label="Configuration"
                    extra="Default GitLab configuration to use for the connection."
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="project"
                    label="Project"
                    extra={<>Default full path of the GitLab project containing the pipeline,
                        like <code>group/subgroup/project</code></>}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    name="ref"
                    label="Ref"
                    extra="Branch or tag to run the pipeline on"
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
