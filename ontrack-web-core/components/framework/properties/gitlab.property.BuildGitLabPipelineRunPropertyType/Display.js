import {Space} from "antd";
import Link from "next/link";
import {FaGitlab} from "react-icons/fa";

export default function Display({property}) {
    const {projectPath, pipelineIid, url} = property.value
    return (
        <Space>
            <FaGitlab/>
            <Link href={url}>
                {projectPath}#{pipelineIid}
            </Link>
        </Space>
    )
}
