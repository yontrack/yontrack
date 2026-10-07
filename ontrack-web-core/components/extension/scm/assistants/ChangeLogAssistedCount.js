import {Space, Typography} from "antd";
import {FaRobot} from "react-icons/fa";
import {assistedCountText, countAssistedCommits} from "@components/extension/scm/assistants/assistants";

/**
 * "N of M commits assisted", in the header of a change log.
 *
 * Nothing is rendered when no commit is assisted.
 *
 * @param commits Commits of the change log, each one as `{commit: {assistants}}`
 */
export default function ChangeLogAssistedCount({commits}) {
    const total = commits?.length ?? 0
    const assisted = countAssistedCommits(commits)
    if (assisted === 0) {
        return null
    }
    return (
        <Space size={8} data-testid="change-log-assisted-count">
            <FaRobot aria-hidden="true"/>
            <Typography.Text>{assistedCountText(assisted, total)}</Typography.Text>
        </Space>
    )
}
