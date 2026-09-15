import {Space} from "antd";
import Link from "next/link";
import {FaBitbucket} from "react-icons/fa";

export default function Display({property}) {
    const {repository, buildNumber, url} = property.value
    return (
        <Space>
            <FaBitbucket/>
            <Link href={url}>
                {repository}#{buildNumber}
            </Link>
        </Space>
    )
}
