import {Empty, Space, Table} from "antd";
import BranchLink from "@components/branches/BranchLink";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";
import ProjectBuildSearchSelect from "@components/projects/search/ProjectBuildSearchSelect";
import ProjectBuildSearchUnselect from "@components/projects/search/ProjectBuildSearchUnselect";
import ProjectBuildSearchChangeLogButton from "@components/projects/search/ProjectBuildSearchChangeLogButton";
import {useContext} from "react";
import {UserContext} from "@components/providers/UserProvider";
import BuildDeploymentChips from "@components/extension/environments/journey/BuildDeploymentChips";
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer";
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer";
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog";
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import {useRouter} from "next/router";

export default function ProjectBuildSearchTable({
                                                    builds,
                                                    selectedBuilds,
                                                    buildSelectable,
                                                    onBuildSelected,
                                                    onBuildUnselected,
                                                    loading
                                                }) {

    const user = useContext(UserContext)

    /*
     * One drawer for the whole table, opened by any row's chip. Per row it would be one drawer
     * per build, and `?slot=<id>` would open all of them at once.
     */
    const slotDrawer = useSlotDrawer()

    /*
     * The drawer offers Deploy on the builds a slot is waiting for, so the dialog comes with it.
     * Without it that button would be drawn and do nothing.
     */
    const router = useRouter()
    const deployDialog = useDeployDialog({
        onSuccess: (pipelineId) => {
            if (pipelineId) router.push(slotPipelineUri(pipelineId))
        },
    })

    return (
        <>
            <Table
                size="small"
                dataSource={builds}
                loading={loading}
                pagination={false}
                sticky={{
                    offsetHeader: 64,
                }}
                locale={{
                    emptyText: <>
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={
                                <>
                                    Click on the search button to look for builds
                                </>
                            }
                        />
                    </>
                }}
                summary={() => (
                    <Table.Summary fixed="top">
                        {
                            selectedBuilds.map(build => (
                                <Table.Summary.Row key={build.id} style={{
                                    backgroundColor: 'var(--ot-bg-info-subtle)',
                                }}>
                                    <Table.Summary.Cell index={0} colSpan={1}>
                                        <BranchLink branch={build.branch}/>
                                    </Table.Summary.Cell>
                                    <Table.Summary.Cell index={1} colSpan={1}>
                                        <BuildLink build={build}/>
                                    </Table.Summary.Cell>
                                    <Table.Summary.Cell index={2} colSpan={1}>
                                        <PromotionRuns promotionRuns={build.promotionRuns}/>
                                    </Table.Summary.Cell>
                                    {
                                        user.authorizations.environment?.view &&
                                        <Table.Summary.Cell index={3} colSpan={1}>
                                            {/* A selected build stays in the table below as
                                                well, so this copy takes a test id of its own */}
                                            <BuildDeploymentChips
                                                build={build}
                                                onSlotClick={slotDrawer.openSlot}
                                                testId={`build-deployments-selected-${build.id}`}
                                            />
                                        </Table.Summary.Cell>
                                    }
                                    <Table.Summary.Cell index={user.authorizations.environment?.view ? 4 : 3}
                                                        colSpan={1}>
                                        <ProjectBuildSearchUnselect
                                            build={build}
                                            onBuildUnselected={onBuildUnselected}
                                        />
                                    </Table.Summary.Cell>
                                </Table.Summary.Row>
                            ))
                        }
                    </Table.Summary>
                )}
            >
                <Table.Column
                    key="branch"
                    title="Branch"
                    render={(_, build) => <BranchLink branch={build.branch}/>}
                />
                <Table.Column
                    key="build"
                    title="Build"
                    render={(_, build) => <BuildLink build={build}/>}
                />
                <Table.Column
                    key="promotions"
                    title="Promotions"
                    render={(_, build) => <PromotionRuns promotionRuns={build.promotionRuns}/>}
                />
                {
                    user.authorizations.environment?.view &&
                    <Table.Column
                        key="environments"
                        title="Deployments"
                        render={(_, build) =>
                            <BuildDeploymentChips build={build} onSlotClick={slotDrawer.openSlot}/>}
                    />
                }
                <Table.Column
                    key="actions"
                    title={
                        <>
                            <ProjectBuildSearchChangeLogButton
                                selectedBuilds={selectedBuilds}
                            />
                        </>
                    }
                    render={(_, build) =>
                        <Space>
                            <ProjectBuildSearchSelect
                                build={build}
                                buildSelectable={buildSelectable}
                                onBuildSelected={onBuildSelected}
                            />
                        </Space>
                    }
                />
            </Table>
            <SlotDrawer
                slotId={slotDrawer.slotId}
                open={slotDrawer.open}
                onClose={slotDrawer.close}
                onDeploy={(slot, build) => deployDialog.start({slot, build})}
            />
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}