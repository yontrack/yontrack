import MobileAccountScreen from "./AccountScreen"

/**
 * Who is signed in, and the way to sign out.
 *
 * Reached by tapping the name in the header - see `MobileHeader`. It is not in
 * the route map: it stands in for no desktop route, so no phone is ever
 * redirected here.
 */
export default function MobileAccountPage() {
    return <MobileAccountScreen/>
}
