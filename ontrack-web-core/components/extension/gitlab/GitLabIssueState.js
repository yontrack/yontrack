import {Tag} from "antd";

/**
 * State of a GitLab issue.
 *
 * GitLab says `opened`/`closed` where GitHub says `open`/`closed`; both spellings are accepted so that
 * the component does not turn a future API wording into a silently wrong colour.
 */
export default function GitLabIssueState({state}) {
    if (!state) return null
    const open = state === 'opened' || state === 'open'
    return (
        <Tag color={open ? 'success' : 'warning'}>
            {state}
        </Tag>
    )
}
