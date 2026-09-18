import {Button, Flex, List, Typography} from "antd";
import {FaPlay} from "react-icons/fa";
import {isAuthorized} from "@components/common/authorizations";
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog";
import BuildDeploymentListItem from "@components/builds/environments/BuildDeploymentListItem";
import {buildKnownName} from "@components/common/Titles";

export default function BuildEnvironmentDeployment({slot, build, refresh}) {

    /*
     * The build page's environments cell is one of the four entry points the redesign collapses
     * into the deploy dialog (#1797). It opens from the build, so the dialog asks which slot -
     * including this one - and names the deployment a new one would cancel.
     */
    const deployDialog = useDeployDialog({onSuccess: refresh})

    return (
        <>
            <Flex vertical={true} justify="flex-start" gap={8} flex={3}>
                {
                    isAuthorized(slot, "pipeline", "create") &&
                    <Button
                        icon={<FaPlay color="green"/>}
                        data-testid={`build-environment-deploy-${slot.id}`}
                        onClick={() => deployDialog.start({build})}
                    >
                        {`Start deploying ${buildKnownName(build)}`}
                    </Button>
                }
                {
                    slot.currentPipeline && !slot.currentPipeline.finished && slot.currentPipeline.build.id !== build.id &&
                    <>
                        <Typography.Text type="secondary">
                            Another build is being deployed
                        </Typography.Text>
                        <BuildDeploymentListItem
                            deployment={slot.currentPipeline}
                            build={slot.currentPipeline.build}
                            refresh={refresh}
                        />
                    </>
                }
                {
                    slot.pipelines.pageItems.length === 0 &&
                    <Typography.Text type="secondary">This build was not deployed
                        yet</Typography.Text>
                }
                {
                    slot.pipelines.pageItems.length > 0 &&
                    <>
                        <Typography.Text type="secondary">Deployments for this
                            build</Typography.Text>
                        <List
                            size="small"
                            dataSource={slot.pipelines.pageItems}
                            renderItem={(deployment) =>
                                <BuildDeploymentListItem
                                    deployment={deployment}
                                    refresh={refresh}
                                />
                            }
                        />
                    </>
                }
            </Flex>
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}