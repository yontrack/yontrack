import {Head, Html, Main, NextScript} from 'next/document'
import {themeInitScript} from "@components/theme/themeInitScript"

export default function Document() {
    return (
        <Html lang="en">
            <Head>
                {/*
                  Decides the theme before the first paint. Pages are statically
                  prerendered, so the server cannot know the user's choice; this
                  runs ahead of React and stamps `data-theme` on <html>, which is
                  what every colour in `globals.css` keys off.
                */}
                <script dangerouslySetInnerHTML={{__html: themeInitScript}}/>
            </Head>
            <body>
            <Main/>
            <NextScript/>
            </body>
        </Html>
    )
}
