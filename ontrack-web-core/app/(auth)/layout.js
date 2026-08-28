/**
 * Root layout for the auth page
 */

import "./auth.css"
import {themeInitScript} from "@components/theme/themeInitScript"

export default function RootLayout({children}) {
    return (
        <>
            {/*
              `suppressHydrationWarning` because this is an App Router root
              layout, so React hydrates <html> itself - and by then the inline
              script below has already stamped `data-theme` and `color-scheme`
              on it, which the client render does not reproduce. Without this,
              every sign-in load logs an "extra attributes from the server"
              warning. It suppresses the warning for this element's attributes
              only, not for the tree below.
            */}
            <html lang="en" suppressHydrationWarning>
            <head>
                <title>Yontrack - Signin</title>
                {/*
                  This route is a separate App Router root - it is not covered by
                  the provider stack in `pages/_app.js`. There is no user
                  preference to read before signing in, so the theme comes from
                  the same cookie mirror and OS preference the main application
                  starts from, resolved by the same script before the first paint.
                */}
                <script dangerouslySetInnerHTML={{__html: themeInitScript}}/>
            </head>
            <body>
            {children}
            </body>
            </html>
        </>
    )
}
