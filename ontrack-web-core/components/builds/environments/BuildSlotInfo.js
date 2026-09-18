import Link from "next/link";
import {slotPipelineUri, slotUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import {Button, Space, Typography} from "antd";
import {slotNameWithoutProject} from "@components/extension/environments/SlotName";
import {FaChevronCircleDown, FaChevronCircleRight} from "react-icons/fa";
import EnvironmentCurrentBuild from "@components/builds/environments/EnvironmentCurrentBuild";
import EnvironmentBuild from "@components/builds/environments/EnvironmentBuild";
import {isAuthorized} from "@components/common/authorizations";
import {FaPlay} from "react-icons/fa";
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog";
import {buildKnownName} from "@components/common/Titles";
import {useRouter} from "next/router";

export default function BuildSlotInfo({slot, build, showDetails = false, setShowDetails, refresh}) {

    const router = useRouter()

    const toggleShowDetailsOn = () => {
        if (setShowDetails) setShowDetails(true)
    }

    const toggleShowDetailsOff = () => {
        if (setShowDetails) setShowDetails(false)
    }

    const navigateToPipeline = async (pipelineId) => {
        if (pipelineId) await router.push(slotPipelineUri(pipelineId))
    }

    /*
     * The build page's environments cell is one of the four entry points the redesign collapses
     * into the deploy dialog (#1797). It opens from the *build*, so the dialog offers every slot of
     * the project - including the ones refusing it, with the rule that refuses - rather than this
     * row's slot behind a `Popconfirm` which said only that "all currently active deployments"
     * would be cancelled.
     */
    const deployDialog = useDeployDialog({onSuccess: navigateToPipeline})

    return (
        <>
            <Space>
                <Link href={slotUri(slot)}>
                    <Typography.Text strong>
                        {slotNameWithoutProject(slot)}
                    </Typography.Text>
                </Link>
                {
                    !showDetails &&
                    <>
                        <EnvironmentBuild slot={slot} build={build} vertical={false}/>
                        <EnvironmentCurrentBuild slot={slot} build={build}/>
                        {
                            isAuthorized(slot, "pipeline", "create") &&
                            <Button
                                icon={<FaPlay color="green"/>}
                                title={`Start deploying ${buildKnownName(build)}`}
                                data-testid={`build-slot-deploy-${slot.id}`}
                                onClick={() => deployDialog.start({build})}
                            >
                                {buildKnownName(build)}
                            </Button>
                        }
                        <Button
                            type="text"
                            icon={<FaChevronCircleRight/>}
                            title="Show deployment details"
                            onClick={toggleShowDetailsOn}
                        />
                    </>
                }
                {
                    showDetails &&
                    <Button
                        type="text"
                        icon={<FaChevronCircleDown/>}
                        title="Hide deployment details"
                        onClick={toggleShowDetailsOff}
                    />
                }
            </Space>
                {
                    showDetails &&
                    <>
                        <EnvironmentBuild slot={slot} build={build}/>
                        <EnvironmentCurrentBuild slot={slot} build={build}/>
                    </>
                }
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}