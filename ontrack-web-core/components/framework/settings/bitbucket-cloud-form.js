import SettingsForm from "@components/core/admin/settings/SettingsForm";
import {Form, InputNumber, Select, Switch} from "antd";
import DurationPicker from "@components/common/DurationPicker";

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
                <Form.Item
                    name="mergeStrategy"
                    label="Merge strategy"
                    extra="Strategy used to merge the auto-versioning pull requests"
                >
                    <Select
                        options={[
                            {value: 'merge_commit', label: 'Merge commit'},
                            {value: 'squash', label: 'Squash'},
                            {value: 'fast_forward', label: 'Fast forward'},
                        ]}
                    />
                </Form.Item>
                <Form.Item
                    name="autoMergeTimeout"
                    label="Auto merge timeout"
                    extra="Maximum duration to wait for an auto-versioning pull request to be mergeable"
                >
                    <DurationPicker inMilliseconds={true} maxUnit="hour"/>
                </Form.Item>
                <Form.Item
                    name="autoMergeInterval"
                    label="Auto merge interval"
                    extra="Duration between two attempts to merge an auto-versioning pull request. Each attempt costs up to three API requests."
                >
                    <DurationPicker inMilliseconds={true} maxUnit="hour"/>
                </Form.Item>
                <Form.Item
                    name="autoDeleteBranch"
                    label="Auto delete branch"
                    extra="Deleting the source branch when an auto-versioning pull request is merged"
                >
                    <Switch/>
                </Form.Item>
            </SettingsForm>
        </>
    )
}
