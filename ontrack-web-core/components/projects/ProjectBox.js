import {Space, Typography} from "antd";
import Decorations from "@components/framework/decorations/Decorations";
import ProjectFavourite from "@components/projects/ProjectFavourite";
import ProjectLink from "@components/projects/ProjectLink";
import LabelChip from "@components/labels/LabelChip";

/**
 * One project in a list: its name, its favourite star, its decorations and its labels.
 *
 * Every project list of the UI - the All projects widget, the Favourites, the label page - is
 * built on this box, either directly or through `ProjectRow`, so the labels are displayed by
 * all of them. They are read by `gqlProjectContentFragment`.
 */
export default function ProjectBox({project, displayFavourite = true, displayDecorations = true, displayLabels = true}) {
    return (
        <>
            <Space>
                {displayFavourite ? <ProjectFavourite project={project}/> : undefined}
                <ProjectLink
                    project={project}
                    text={<Typography.Text strong type={
                        project.disabled ? "secondary" : undefined
                    }>{project.name}</Typography.Text>}
                />
                {
                    displayDecorations && <Decorations entity={project}/>
                }
                {
                    displayLabels && project.labels && project.labels.length > 0 &&
                    <Space size={4} data-testid={`project-labels-${project.name}`}>
                        {
                            project.labels.map(label => <LabelChip key={label.id} label={label}/>)
                        }
                    </Space>
                }
            </Space>
        </>
    )
}
