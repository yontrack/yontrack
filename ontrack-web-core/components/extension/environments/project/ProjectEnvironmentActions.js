import {
    useProjectEnvironmentsContext
} from "@components/extension/environments/project/ProjectEnvironmentsContextProvider";
import {Card, Empty, Flex, Space, Typography} from "antd";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";
import EnvironmentTitle from "@components/extension/environments/EnvironmentTitle";
import {FaArrowDown} from "react-icons/fa";
import SlotLink from "@components/extension/environments/SlotLink";
import LoadingContainer from "@components/common/LoadingContainer";
import SlotPipelineCreateButton from "@components/extension/environments/SlotPipelineCreateButton";
import SlotPipelineSteps from "@components/extension/environments/SlotPipelineSteps";
import SlotPipelineLink from "@components/extension/environments/SlotPipelineLink";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";

export default function ProjectEnvironmentActions() {

    const {
        selectedBuild,
        selectedSlot,
        currentDeployment,
        currentDeploymentLoading,
        onDeploymentAction
    } = useProjectEnvironmentsContext()

    return (
        <>
            <AutoRefreshContextProvider onRefresh={onDeploymentAction}>
                <Space direction="vertical" className="ot-line">
                    {
                        !selectedSlot && !selectedBuild &&
                        <Empty
                            description="Select a build and an environment"
                        />
                    }
                    {
                        selectedBuild &&
                        <Card
                            size="small"
                            title={
                                <Space>
                                    <Typography.Text>Selected build</Typography.Text>
                                    <BuildLink build={selectedBuild}/>
                                </Space>
                            }
                        >
                            <PromotionRuns promotionRuns={selectedBuild.promotionRuns}/>
                        </Card>
                    }
                    {
                        selectedBuild && !selectedSlot &&
                        <Empty
                            description="Select an environment for the deployment"
                        />
                    }
                    {
                        !selectedBuild && selectedSlot &&
                        <Empty
                            description="Select a build for the deployment"
                        />
                    }
                    {
                        selectedBuild && selectedSlot &&
                        <LoadingContainer loading={currentDeploymentLoading}>
                            <Flex
                                justify="center"
                                align="center"
                                vertical={true}
                                gap={16}
                            >
                                <FaArrowDown size={48} color="var(--ot-icon-muted)"/>
                                {
                                    !currentDeployment &&
                                    <SlotPipelineCreateButton
                                        slot={selectedSlot}
                                        build={selectedBuild}
                                        text="Create candidate deployment"
                                        onStart={onDeploymentAction}
                                    />
                                }
                                {
                                    currentDeployment &&
                                    <Card
                                        size="small"
                                        style={{width: '100%'}}
                                        title={
                                            <Space>
                                                Deployment
                                                <SlotPipelineLink pipelineId={currentDeployment.id} numberOnly={true}/>
                                            </Space>
                                        }
                                        extra={
                                            <AutoRefreshButton size="small"/>
                                        }
                                        actions={
                                            (currentDeployment.status === 'DONE' || currentDeployment.status === 'CANCELLED') && [
                                                <SlotPipelineCreateButton
                                                    key="new"
                                                    slot={selectedSlot}
                                                    build={selectedBuild}
                                                    text="Create new deployment"
                                                    onStart={onDeploymentAction}
                                                />
                                            ]
                                        }
                                    >
                                        <SlotPipelineSteps
                                            pipelineId={currentDeployment.id}
                                            reloadState={0}
                                            onChange={onDeploymentAction}
                                        />
                                    </Card>
                                }
                                <FaArrowDown size={48} color="var(--ot-icon-muted)"/>
                            </Flex>
                        </LoadingContainer>
                    }
                    {
                        selectedSlot &&
                        <Card
                            size="small"
                            title="Target environment"
                            extra={
                                <SlotLink slot={selectedSlot}/>
                            }
                        >
                            <EnvironmentTitle environment={selectedSlot.environment} tags={false} editable={false}/>
                        </Card>
                    }
                </Space>
            </AutoRefreshContextProvider>
        </>
    )
}