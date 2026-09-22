import {resolveInterstitialTarget} from "@components/mobile/mobileRedirect"
import DesktopOnlyScreen from "./DesktopOnlyScreen"

/**
 * Where the proxy sends a phone whose destination has no mobile equivalent.
 *
 * A server component so the `target` parameter is sanitised before it ever
 * reaches the browser: it ends up in a navigation, and anyone can type one.
 */
export default async function DesktopOnlyPage(props) {
    const searchParams = await props.searchParams
    return <DesktopOnlyScreen target={resolveInterstitialTarget(searchParams?.target)}/>
}
