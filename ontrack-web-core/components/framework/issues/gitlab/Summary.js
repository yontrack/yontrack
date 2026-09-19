import {Space} from "antd";
import GitLabIssueState from "@components/extension/gitlab/GitLabIssueState";
import GitLabLabels from "@components/extension/gitlab/GitLabLabels";
import GitLabMilestone from "@components/extension/gitlab/GitLabMilestone";

/**
 * One GitLab issue, on the issue information page.
 *
 * Loaded by its path - `framework/issues/gitlab/Summary` - from the `gitlab` service id, as GitHub's
 * counterpart is. GitLab's issue endpoint returns the description as Markdown rather than as rendered
 * HTML, and Yontrack does not render Markdown here, so - unlike GitHub's summary - there is no body.
 */
export default function IssueGitLabSummary({rawIssue}) {
    return (
        <Space className="ot-line" direction="vertical">
            <Space wrap>

                State:
                <GitLabIssueState state={rawIssue.state}/>

                {
                    rawIssue.milestoneTitle &&
                    <>
                        Milestone:
                        <GitLabMilestone title={rawIssue.milestoneTitle} url={rawIssue.milestoneUrl}/>
                    </>
                }

                {
                    rawIssue.labels && rawIssue.labels.length > 0 &&
                    <>
                        Labels:
                        <GitLabLabels labels={rawIssue.labels}/>
                    </>
                }

            </Space>
        </Space>
    )
}
