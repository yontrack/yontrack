import {Space} from "antd"
import {useQuery} from "@components/services/GraphQL"
import {useFreshness} from "@components/extension/environments/shared/Freshness"
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer"
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import EnvironmentMatrix from "@components/extension/environments/matrix/EnvironmentMatrix"
import ProjectSlotGraph from "@components/extension/environments/project/ProjectSlotGraph"
import ProjectEnvironmentsToolbar from "@components/extension/environments/project/ProjectEnvironmentsToolbar"
import {useProjectEnvironmentsView} from "@components/extension/environments/project/useProjectEnvironmentsView"
import {
    projectMatrixFilter,
    qualifierOptions,
    qualifiersFromEnvironments,
    VIEW_MATRIX,
} from "@components/extension/environments/project/projectEnvironmentsModel"
import {gqlProjectQualifiers} from "@components/extension/environments/project/projectEnvironmentsGraphQL"

/**
 * One project's environments: the slot graph, or the home matrix pinned to this project.
 *
 * What this screen *was* - three panels where a build and a slot both had to be picked before
 * anything could happen - is gone (#1795). Choosing a build to deploy is the deploy dialog's job and
 * the drawer's, and both are shared with every other screen of the feature, so the page is left with
 * the one thing that is genuinely project-scoped: which slots this project has and which admits from
 * which.
 *
 * Two views, one address. The graph answers "what depends on what"; the matrix answers "what is
 * where", nests the project's qualifiers as rows, and is the very same component the home page
 * draws - a reader switching between the two is not learning a second table.
 *
 * @param {Object} project The project, with `id` and `name`
 */
export default function ProjectEnvironments({project}) {

    const {view, qualifier, apply, ready} = useProjectEnvironmentsView()

    /*
     * One freshness for the whole screen rather than one per view: "Updated 12 s ago" is a fact
     * about this page, and a reader switching from Graph to Matrix should not see the age of the
     * data jump back and forth between two independent timers.
     */
    const freshness = useFreshness()

    const {data: qualifiers} = useQuery(gqlProjectQualifiers, {
        variables: {projectName: project?.name},
        condition: !!project?.name,
        deps: [project?.name],
        initialData: [],
        dataFn: data => qualifierOptions(qualifiersFromEnvironments(data.environments)),
    })

    const slotDrawer = useSlotDrawer()
    const deployDialog = useDeployDialog()

    const matrix = view === VIEW_MATRIX

    return (
        <>
            <Space direction="vertical" className="ot-line" size="middle">
                <ProjectEnvironmentsToolbar
                    view={view}
                    qualifier={qualifier}
                    qualifiers={qualifiers}
                    onChange={apply}
                    freshness={freshness}
                />
                {
                    // The view comes from the URL, which Next fills on a second render: drawing
                    // before it is known would show the graph for a tick and then jump to the
                    // matrix somebody actually asked for.
                    ready && (
                        matrix ?
                            /*
                             * Pinned and unpaged: one project is one row, and a toolbar offering to
                             * search for another project on a page that is *about* this one would
                             * be a way out of the screen dressed up as a filter. The matrix keeps
                             * its own drawer and deploy dialog, which is why this screen only draws
                             * its own in the graph view.
                             */
                            <EnvironmentMatrix
                                filter={projectMatrixFilter(project)}
                                paged={false}
                                emptyStates={false}
                                freshness={freshness}
                            /> :
                            <ProjectSlotGraph
                                project={project}
                                qualifier={qualifier}
                                onSlotClick={slotDrawer.openSlot}
                                refreshCount={freshness.refreshCount}
                            />
                    )
                }
            </Space>
            {
                !matrix &&
                <>
                    <SlotDrawer
                        slotId={slotDrawer.slotId}
                        open={slotDrawer.open}
                        onClose={slotDrawer.close}
                        onDeploy={(slot, build) => deployDialog.start({slot, build})}
                    />
                    <DeployDialog dialog={deployDialog}/>
                </>
            }
        </>
    )
}
