import {Space} from "antd";
import ProjectRow from "@components/projects/ProjectRow";

export default function ProjectList({projects}) {
    return (
        <>
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                {projects.map(project => <ProjectRow key={project.id} project={project}/>)}
            </Space>
        </>
    )
}