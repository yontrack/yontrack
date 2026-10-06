import SearchResultComponent from "@components/framework/search/SearchResultComponent";
import {Space, Typography} from "antd";
import Link from "next/link";
import ProjectLinkByName from "@components/projects/ProjectLinkByName";

export default function Result({data}) {
    // Current name of the project, resolved when searching
    const projectName = data.project.name
    return <SearchResultComponent
        title={
            <>
                <Space>
                    <Link href={`/extension/scm/${projectName}/issue-info/${data.item.key}`}>
                        <Typography.Text code>{data.item.displayKey}</Typography.Text>
                    </Link>
                    <Typography.Text>
                        (<ProjectLinkByName name={projectName}/>)
                    </Typography.Text>
                </Space>
            </>
        }
        description=""
    />
}