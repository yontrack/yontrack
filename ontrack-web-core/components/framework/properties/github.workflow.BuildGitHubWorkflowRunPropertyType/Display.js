import {Space, Spin, Typography} from "antd";
import Link from "next/link";
import {FaGithub} from "react-icons/fa";

export default function Display({property}) {

    return (
        <>
            <Space orientation="vertical">
                {
                    property.value.workflows.map(workflow => (
                        <>
                            <Space key={workflow.runId}>
                                <FaGithub/>
                                <Link href={workflow.url}>
                                    {workflow.name}#{workflow.runNumber}
                                </Link>
                                {workflow.event && <Typography.Text code>{workflow.event}</Typography.Text>}
                                {
                                    workflow.running && <>
                                        <Spin size="small"/>
                                        Running
                                    </>
                                }
                            </Space>
                        </>
                    ))
                }
            </Space>
        </>
    )
}