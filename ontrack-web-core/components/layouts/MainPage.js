import {Layout, Space, theme} from "antd";
import MainPageBar from "@components/layouts/MainPageBar";
import {useContext} from "react";
import {MainLayoutContext} from "@components/layouts/MainLayout";
import MainGlobalMessages from "@components/layouts/MainGlobalMessages";

const {Content} = Layout;

export default function MainPage({pageId = '', title, breadcrumbs, commands, description, warning, children}) {

    const {
        token: {colorBgContainer},
    } = theme.useToken();

    const {expanded} = useContext(MainLayoutContext)

    return (
        <>
            <Layout data-page-id={`page-${pageId}`}>
                <Content
                    style={{
                        padding: 12,
                        paddingTop: 8,
                        minHeight: 280,
                        background: colorBgContainer,
                    }}
                >
                    {
                        !expanded && <MainPageBar
                            breadcrumbs={breadcrumbs}
                            title={title}
                            commands={commands}
                            description={description}
                        />
                    }
                    <Space orientation="vertical" className="ot-line">
                        {warning}
                        <MainGlobalMessages/>
                        {children}
                    </Space>
                </Content>
            </Layout>
        </>
    )
}