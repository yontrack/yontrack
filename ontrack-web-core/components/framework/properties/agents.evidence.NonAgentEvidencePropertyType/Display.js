import {Space, Typography} from "antd";
import LicenceNotice from "./LicenceNotice";

/**
 * *Evidence from non-agents only* on a validation stamp: agents may neither create a run on it nor
 * change the status of one of its runs.
 */
export default function Display({property}) {
    return (
        <Space orientation="vertical" size={4}>
            {
                property.value?.enabled !== false ?
                    <Typography.Text data-testid="non-agent-evidence">
                        Evidence must come from a non-agent actor: agents may neither validate builds on this
                        stamp nor change the status of its runs.
                    </Typography.Text> :
                    <Typography.Text type="secondary" data-testid="non-agent-evidence-off">
                        Agents may record evidence on this stamp.
                    </Typography.Text>
            }
            <LicenceNotice/>
        </Space>
    )
}
