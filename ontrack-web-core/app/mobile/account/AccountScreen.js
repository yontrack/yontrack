"use client"

/**
 * The account screen: who is signed in, how the app looks, which UI this device
 * is on, and the way to stop being signed in.
 *
 * **A screen rather than a drawer.** A drawer is the desktop pattern
 * (`UserMenu`), and importing it would cross the boundary the mobile shell is
 * built on. A screen is what every other mobile surface already is, it has a URL
 * a test can address, and a mis-tap costs a navigation rather than a session.
 * It is deliberately not a fourth bottom-nav tab either: two thumb-level
 * destinations are what the bar carries, and a tab is earned by a screen someone
 * returns to, not by a settings page.
 *
 * **What it holds, and what it does not.** The identity, the appearance, the
 * device switch, the version, and sign out. The desktop's own user-profile page
 * (`/core/admin/userProfile`) is API tokens and groups, and neither belongs on a
 * phone; nothing else from the desktop user menu arrives here.
 *
 * **The user/device line is a section heading, not an omission.** The theme is a
 * preference of the *user* - one `themeMode` on the account, shared with the
 * desktop UI - and lives under **Appearance**. The `yontrack-ui=desktop` opt-out
 * is a choice of the *device*, and lives under **This device**, which says so
 * where the user can read it. The screen used to draw that line by leaving the
 * opt-out off altogether (#1730, #1732); naming the section states the
 * distinction instead of hiding it, and gives the next mobile preference an
 * obvious side to land on.
 *
 * **The desktop version is reachable deliberately** (#1733). It used to be
 * reachable only by accident: `switchToDesktopUI` had one caller, the
 * interstitial, which a user reaches by following a link to a page the mobile UI
 * does not have - so wanting the desktop UI meant first going somewhere you did
 * not want to go. The desktop UI has carried a deliberate "Mobile version" entry
 * in its user menu since #1719; this is its counterpart, and it matters more on
 * this side, because #1727 installs the mobile UI as a PWA scoped to `/mobile`
 * and an installed app has no address bar to escape with.
 *
 * The version earns its row because of the PWA: no address bar, no user menu,
 * and "what version are you on?" is the first question on any support thread.
 * It is also where the desktop user menu puts it.
 *
 * **Sign out fires immediately.** No confirmation: two taps and a whole screen
 * of context is already the confirmation, and a modal on a screen the user
 * deliberately navigated to is friction that makes a phone app feel like a form.
 * The desktop does not confirm either.
 *
 * **It is a local sign-out**, exactly as the desktop's is: `signOut` drops
 * Yontrack's own session and leaves the identity provider's alone, so the
 * Keycloak SSO cookie survives and the next sign-in is silent. Making it a real
 * sign-out is #1734 - a shared auth change touching both UIs, both provider
 * configurations and the 401 handler. Nothing is said about it here: the desktop
 * makes no such statement, and a caveat the user can do nothing about reads as a
 * malfunction.
 */

import {Button, Space, Typography} from "antd"
import {useContext} from "react"
import {FaSignOutAlt} from "react-icons/fa"
import {signOut} from "next-auth/react"
import {UserContext} from "@components/providers/UserProvider"
import {useRefData} from "@components/providers/RefDataProvider"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileSection from "@components/mobile/layout/MobileSection"
import MobileThemeSwitch from "@components/mobile/account/MobileThemeSwitch"
import DesktopVersionButton from "@components/mobile/DesktopVersionButton"
import {DESKTOP_HOME, MOBILE_HOME} from "@components/mobile/mobileRoutes"

export default function MobileAccountScreen() {

    const user = useContext(UserContext)
    const {version} = useRefData()

    /*
     * `MOBILE_HOME`, and not the default.
     *
     * `signOut()` with no argument defaults `callbackUrl` to the current URL.
     * Signing out of `/mobile/build/12` would therefore leave that build as the
     * callback, and signing back in would return to it - on a shared phone, the
     * wrong souvenir. `/mobile` is redirect-exempt so the middleware leaves it
     * alone, and `AuthProvider` sends the unauthenticated visitor to the sign-in
     * page on its own.
     */
    const onSignOut = () => signOut({callbackUrl: MOBILE_HOME})

    /*
     * The email is context for the username, and it is not context for itself:
     * an OIDC provider that hands the address over as the username - which is
     * what the dev stack's Keycloak realm does - would otherwise print it twice,
     * two lines apart, for no gain.
     */
    const email = user?.email !== user?.name ? user?.email : undefined

    return (
        <MobileScreen
            // The identity is the screen's head: the full name is what a human
            // calls them, the username what Yontrack does - which is the one
            // that turns up as a promotion's author.
            title={user?.fullName || user?.name}
            // Only when it is not already the title: an account with no full
            // name would otherwise read "admin" over "admin".
            subtitle={user?.fullName ? user?.name : undefined}
        >
            {
                email &&
                <Typography.Text
                    type="secondary"
                    className="ot-mobile-account-email"
                    data-testid="mobile-account-email"
                >
                    {email}
                </Typography.Text>
            }

            {/*
              Between the identity above and the sign out below. Sign out stays
              the last thing on the screen and the only destructive one: a
              preference placed after it would sit on the path a thumb travels
              past. The section title is what makes the screen read as *account
              and preferences* rather than as a sign-out button with something
              bolted above it.
            */}
            <MobileSection title="Appearance" testId="mobile-account-appearance">
                <MobileThemeSwitch/>
            </MobileSection>

            {/*
              Between Appearance and sign out, for the same reason Appearance is
              between the identity and sign out: sign out stays last and stays
              the only destructive control.
            */}
            <MobileSection title="This device" testId="mobile-account-device">
                <Space direction="vertical" size={4} style={{width: '100%'}}>
                    {/*
                      `DESKTOP_HOME`, exactly as `switchToMobileUI` always lands
                      on `MOBILE_HOME`: by the time the user is here, the screen
                      they came from is gone, and computing a desktop equivalent
                      would need an inverse route map. `default` rather than the
                      interstitial's `primary`, and no icon - `FaDesktop` already
                      means "follow the operating system" two rows up.
                    */}
                    <DesktopVersionButton href={DESKTOP_HOME} block/>
                    {/*
                      The same shape as `themeModeCaption`, because a phone has
                      no hover. Deliberately not a constant shared with the
                      interstitial, whose sentence is bound to a destination it
                      has just named: two sentences, one fact, and `mobile-ui.md`
                      records the fact.
                    */}
                    <Typography.Text type="secondary" data-testid="mobile-account-device-caption">
                        This device stays on the desktop version until you close
                        your browser. The “Mobile version” entry in the desktop
                        user menu brings you back sooner.
                    </Typography.Text>
                </Space>
            </MobileSection>

            <Button
                block
                danger
                // Large rather than default, as every tap target on this
                // screen is: antd's default button is 32px, well under what a
                // thumb wants, and `large` is its biggest at 40px.
                size="large"
                icon={<FaSignOutAlt/>}
                data-testid="mobile-account-sign-out"
                onClick={onSignOut}
            >
                Sign out
            </Button>

            {
                /*
                 * At the foot and in secondary text: it is what you read out on
                 * a support thread, not something anyone opened this screen for.
                 */
                version &&
                <Space direction="vertical" size={0} style={{width: '100%'}}>
                    <Typography.Text type="secondary" data-testid="mobile-account-version">
                        Yontrack {version}
                    </Typography.Text>
                </Space>
            }
        </MobileScreen>
    )
}
