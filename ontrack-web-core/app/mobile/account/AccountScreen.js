"use client"

/**
 * The account screen: who is signed in, how the app looks, and the way to stop
 * being signed in.
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
 * version, and sign out. The desktop's own user-profile page
 * (`/core/admin/userProfile`) is API tokens and groups, and neither belongs on a
 * phone; nothing else from the desktop user menu arrives here.
 *
 * The theme control is here because the theme is a preference of the *user*,
 * not of the device - one `themeMode` on the account, shared with the desktop
 * UI. That is the same line this screen already draws twice, in declining the
 * desktop-version opt-out and in leaving the `yontrack-ui=desktop` cookie alone
 * on sign out; the theme falls on the other side of it.
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
import {MOBILE_HOME} from "@components/mobile/mobileRoutes"

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

            <Button
                block
                danger
                // Large rather than default: the one action on the screen, and
                // a 40px antd button is under the 44px a thumb wants.
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
