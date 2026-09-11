import MobileDeploymentScreen from "./DeploymentScreen"

/**
 * One deployment on a phone, reached from the build it deploys or by following a
 * desktop `/extension/environments/pipeline/[id]` link - which the redirect maps
 * here.
 *
 * The screen itself is a client component; the page is only the route.
 */
export default function MobileDeploymentPage({params}) {
    return <MobileDeploymentScreen id={params.id}/>
}
