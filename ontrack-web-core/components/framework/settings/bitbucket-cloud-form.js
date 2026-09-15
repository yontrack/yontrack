import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber} from "antd";

export default function BitbucketCloudForm({id, ...values}) {
    return (
        <>
            <SettingsForm id={id} values={values}>
                <Form.Item
                    name="maxCommits"
                    label="Max commits"
                    extra="Maximum number of commits to return for a change log. Bitbucket Cloud allows 1,000 API requests per hour per token, and returns at most 100 commits per request."
                >
                    <InputNumber min={1} max={10000}/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
