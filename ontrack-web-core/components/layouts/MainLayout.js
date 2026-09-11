import {Layout} from "antd";
import NavBar from "@components/layouts/NavBar";
import {createContext, useEffect, useState} from "react";
import MainLayoutRestoreViewButton from "@components/layouts/MainLayoutRestoreViewButton";
import {useRouter} from "next/router";
import LayoutContent from "@components/layouts/LayoutContent";

const {Header} = Layout;

export const MainLayoutContext = createContext({expanded: false})

export default function MainLayout({children}) {

    const [expanded, setExpanded] = useState(false)

    const router = useRouter()

    useEffect(() => {
        // Gets the `expanded` query parameter
        setExpanded(router.query.expanded)
    }, [router])

    const toggleExpansion = () => {
        setExpanded(!expanded)
    }

    return (
        <>
            <MainLayoutContext.Provider value={{expanded, toggleExpansion}}>
                {
                    !expanded && <Layout>
                        <Header
                            style={{
                                backgroundColor: "var(--ot-header-bg)",
                                padding: '0 12px', // Aligned with padding of `MainPage`
                                // The header is a fixed 64px box, and antd gives it a 64px
                                // `line-height` to centre a single line of text in it. Any text
                                // inside that wraps therefore gets 64px-tall line boxes: at phone
                                // width the user's name wrapped to three lines and grew the nav
                                // row to 192px, which overflowed the header and painted over the
                                // page bar below - burying the user-menu trigger under the home
                                // page's "New project" command (#1729).
                                //
                                // `line-height: normal` stops text inflating the row, the flex
                                // box centres it in the 64px instead, and `overflow: hidden` is
                                // the backstop: whatever the header ends up holding, it can never
                                // reach the page bar again.
                                lineHeight: 'normal',
                                display: 'flex',
                                alignItems: 'center',
                                overflow: 'hidden',
                            }}
                        >
                            <NavBar/>
                        </Header>
                        <LayoutContent>
                            {children}
                        </LayoutContent>
                    </Layout>
                }
                {
                    expanded && <Layout>
                        <LayoutContent>
                            {children}
                        </LayoutContent>
                    </Layout>
                }
                <MainLayoutRestoreViewButton/>
            </MainLayoutContext.Provider>
        </>
    )
}