import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber} from "antd";

export default function GitLabForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="maxCommits"
                    label="Max commits"
                    extra="Maximum number of commits to return for a change log. GitLab's comparison endpoint returns the whole range in one answer, and gives up on its own past a few thousand commits."
                >
                    <InputNumber min={1} max={10000}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
