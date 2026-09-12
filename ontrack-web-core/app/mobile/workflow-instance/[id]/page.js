import MobileWorkflowInstanceScreen from "./WorkflowInstanceScreen"

/**
 * One workflow run on a phone, reached from the build screen (a promotion's
 * workflows) or from the deployment screen (a slot's), or by following a desktop
 * `/extension/workflows/instances/[id]` link - which the redirect maps here.
 *
 * The screen itself is a client component; the page is only the route.
 *
 * **The id is decoded, and it has to be.** An instance id is
 * `ISO_LOCAL_DATE_TIME-UUID`, so it carries colons - and the App Router hands a
 * dynamic segment back exactly as it appears in the URL, which is to say
 * percent-encoded: `2026-09-12T14%3A43%3A40.648194679-<uuid>`. Passed on as it
 * arrives, it is an id no instance has, and the screen answers "this workflow run
 * could not be found" for a run that is right there. No other mobile route hits
 * this, because no other entity id has a character a browser encodes.
 */
export default function MobileWorkflowInstancePage({params}) {
    return <MobileWorkflowInstanceScreen id={decodeURIComponent(params.id)}/>
}
