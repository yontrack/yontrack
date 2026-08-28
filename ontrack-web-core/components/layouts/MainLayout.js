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