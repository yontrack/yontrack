import {useContext} from "react";
import {FaTags} from "react-icons/fa";
import {Command} from "@components/common/Commands";
import {EventsContext} from "@components/common/EventsContext";
import ProjectLabelsDialog, {useProjectLabelsDialog} from "@components/projects/ProjectLabelsDialog";

/**
 * Command assigning labels to a project. It is displayed only when the `labels`
 * authorization is granted on the project.
 */
export default function ProjectLabelsCommand({project}) {

    const eventsContext = useContext(EventsContext)

    const dialog = useProjectLabelsDialog({
        onSuccess: () => {
            eventsContext.fireEvent("project.updated", {id: Number(project.id)})
        },
    })

    return (
        <>
            <Command
                icon={<FaTags/>}
                text="Labels"
                testId="project-labels-command"
                action={() => dialog.start({project})}
            />
            <ProjectLabelsDialog dialog={dialog}/>
        </>
    )
}
