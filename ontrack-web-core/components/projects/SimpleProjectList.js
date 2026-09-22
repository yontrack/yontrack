import {Empty, Space, Typography} from "antd";
import RowTag from "@components/common/RowTag";
import ProjectBox from "@components/projects/ProjectBox";

export default function SimpleProjectList({projects, emptyText, before, after}) {
    return (
        <>
            {
                projects && projects.length > 0 &&
                <Space orientation="horizontal" size={16} wrap>
                    {
                        before
                    }
                    {
                        projects.map(project => <RowTag key={project.id}>
                                <ProjectBox project={project}/>
                            </RowTag>
                        )
                    }
                    {
                        after
                    }
                </Space>
            }
            {
                (!projects || projects.length === 0) && <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={<Typography.Text>{emptyText}</Typography.Text>}
                />
            }
        </>
    )
}