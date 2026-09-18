import {useRouter} from "next/router"
import {Typography} from "antd"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils"

/**
 * "Start deployment" in a build's user menu.
 *
 * One of the four entry points the redesign collapses into the deploy dialog. It used to open
 * `BuildStartDeploymentDialog`, whose `Select` listed ineligible slots as disabled options - a
 * click that did nothing and said nothing - and which never mentioned that starting a deployment
 * cancels the slot's active one.
 */
export default function EnvironmentsBuildStartPipeline({buildId}) {

    const router = useRouter()

    const dialog = useDeployDialog({
        onSuccess: (pipelineId) => {
            if (pipelineId) {
                router.push(slotPipelineUri(pipelineId))
            }
        },
    })

    const onClick = () => {
        dialog.start({build: {id: buildId}})
    }

    return (
        <>
            <Typography.Text onClick={onClick}>Start deployment</Typography.Text>
            <DeployDialog dialog={dialog}/>
        </>
    )
}
