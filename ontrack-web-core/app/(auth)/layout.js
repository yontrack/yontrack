/**
 * Root layout for the auth page
 */

import "./auth.css"
import {themeInitScript} from "@components/theme/themeInitScript"

export default function RootLayout({children}) {
    return (
        <>
            <html lang="en">
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
