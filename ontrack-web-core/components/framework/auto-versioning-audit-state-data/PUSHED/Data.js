import Link from "next/link";
import {Space} from "antd";
import CodeData from "@components/framework/auto-versioning-audit-state-data/support/CodeData";

export default function PushedData({data}) {
    const branch = data.branch
    const commitId = data.commitId
    const commitLink = data.commitLink
    return <Space>
        Pushed to branch <CodeData text={branch}/>.
        {commitLink && commitId
            ? <><Link href={commitLink}>{commitId}</Link></>
            : commitId
                ? <CodeData text={commitId}/>
                : null
        }
    </Space>
}
