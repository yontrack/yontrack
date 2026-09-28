import {isAuthorized} from "@components/common/authorizations";
import {Command} from "@components/common/Commands";
import {FaStamp} from "react-icons/fa";
import ForceDeploymentDialog, {
    useForceDeploymentDialog
} from "@components/extension/environments/ForceDeploymentDialog";

export default function ForceDeploymentCommand({deployment, onForced}) {

    const dialog = useForceDeploymentDialog({onForced})

    const onForce = () => {
        dialog.start({deployment})
    }

    return (
        <>
            {
                isAuthorized(deployment.slot, "pipeline", "override") &&
                deployment.status !== "DONE" &&
                // A failure is terminal: the server refuses to force it to deployed
                deployment.status !== "FAILED" &&
                <>
                    <Command
                        icon={<FaStamp/>}
                        text="Force deployment"
                        title="Force the deployment to be complete"
                        action={onForce}
                    />
                    <ForceDeploymentDialog dialog={dialog}/>
                </>
            }
        </>
    )
}