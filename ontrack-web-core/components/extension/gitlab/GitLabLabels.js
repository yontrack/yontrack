import {Space, Tag} from "antd";

/**
 * Labels of a GitLab issue.
 *
 * GitLab sends them as plain strings - their colours only come back when the issue is asked for with
 * `with_labels_details`, which Yontrack does not - so they are rendered as neutral tags.
 */
export default function GitLabLabels({labels}) {
    if (!labels || labels.length === 0) return null
    return (
        <Space size={4} wrap>
            {
                labels.map(label => <Tag key={label}>{label}</Tag>)
            }
        </Space>
    )
}
