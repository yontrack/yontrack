"use client"

/**
 * The mobile UI's provider stack.
 *
 * `/mobile` is its own App Router root, so - exactly like the sign-in page under
 * `app/(auth)` - it is *not* covered by the stack in `pages/_app.js` and has to
 * assemble its own.
 *
 * It is a shorter stack than the desktop one on purpose. `SearchContextProvider`
 * and `EventsContextProvider` serve desktop surfaces the mobile UI has no
 * counterpart for, and `LoadingAggregator` drives the desktop page bar. What is
 * here is what a mobile screen genuinely needs: the session, the theme, antd's
 * message channel, the signed-in user, their preferences and the reference data.
 */

import {SessionProvider} from "next-auth/react"
import ThemeProvider from "@components/providers/ThemeProvider"
import MessageContextProvider from "@components/providers/MessageProvider"
import AuthProvider from "@components/providers/AuthProvider"
import UserContextProvider from "@components/providers/UserProvider"
import PreferencesContextProvider from "@components/providers/PreferencesProvider"
import RefDataContextProvider from "@components/providers/RefDataProvider"
import ThemePreferenceSync from "@components/theme/ThemePreferenceSync"

export default function MobileProviders({children}) {
    return (
        <SessionProvider>
            {/*
              Outermost, above the message provider, so antd's portalled surfaces
              - messages, modals, drawers - are themed too. Same ordering as the
              desktop stack, and for the same reason.
            */}
            <ThemeProvider>
                <MessageContextProvider>
                    <AuthProvider>
                        <UserContextProvider>
                            <PreferencesContextProvider>
                                <RefDataContextProvider>
                                    <ThemePreferenceSync/>
                                    {children}
                                </RefDataContextProvider>
                            </PreferencesContextProvider>
                        </UserContextProvider>
                    </AuthProvider>
                </MessageContextProvider>
            </ThemeProvider>
        </SessionProvider>
    )
}
