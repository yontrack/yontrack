import {Typography} from "antd";
import CodeData from "@components/framework/auto-versioning-audit-state-data/support/CodeData";

export default function PushingData({data}) {
    return <Typography.Text>
        Pushing to branch <CodeData text={data.branch}/>.
    </Typography.Text>
}
