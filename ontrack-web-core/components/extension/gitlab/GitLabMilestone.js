import {Tag, Typography} from "antd";

/**
 * Milestone of a GitLab issue, linked when Yontrack knows its URL.
 *
 * The URL is resolved server side by `GitLabIssueWrapper`: GitLab only sends a milestone's `web_url` on
 * recent versions, and the wrapper builds it from the configuration otherwise.
 */
export default function GitLabMilestone({title, url}) {
    if (!title) return null
    return (
        <Tag>
            {
                url
                    ? <Typography.Link href={url}>{title}</Typography.Link>
                    : <Typography.Text>{title}</Typography.Text>
            }
        </Tag>
    )
}
