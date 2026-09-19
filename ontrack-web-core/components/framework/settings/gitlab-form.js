import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber, Switch} from "antd";
import DurationPicker from "@components/common/DurationPicker";

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
                <Form.Item
                    name="squash"
                    label="Squash"
                    extra="Squash the commits of an auto-versioning merge request when it is merged. GitLab's project settings can force this either way, whatever is asked for here."
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    name="removeSourceBranch"
                    label="Remove source branch"
                    extra="Delete the source branch when an auto-versioning merge request is merged"
                >
                    <Switch/>
                </Form.Item>
                <Form.Item
                    name="autoMergeTimeout"
                    label="Auto merge timeout"
                    extra="Maximum duration to wait for an auto-versioning merge request to become mergeable"
                >
                    <DurationPicker inMilliseconds={true} maxUnit="hour"/>
                </Form.Item>
                <Form.Item
                    name="autoMergeInterval"
                    label="Auto merge interval"
                    extra="Duration between two checks of the detailed merge status of an auto-versioning merge request. Each check costs one API request."
                >
                    <DurationPicker inMilliseconds={true} maxUnit="hour"/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
