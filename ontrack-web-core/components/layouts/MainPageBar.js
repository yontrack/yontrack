import {Breadcrumb, Space, Typography} from "antd";
import MainPageCommands from "@components/layouts/MainPageCommands";

const {Text} = Typography;

export default function MainPageBar({breadcrumbs, title, commands, description}) {

    // Purely derived from the props - computed here rather than parked in a
    // `useState` filled by a `useEffect`, which would render an empty
    // breadcrumb on the first pass.
    const actualBreadcrumbs = [
        ...breadcrumbs.map(b => ({title: b})),
        {title: <Text strong>{title}</Text>},
    ]

    return (
        <>
            <Space direction="vertical" className="ot-line" size={0} data-testid="main-page-bar">
                <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    // The commands wrap onto their own line rather than squeezing the
                    // breadcrumb: at phone width the home page's bar crushed "Home"
                    // into one letter per line before this (#1729). Wrapping is the
                    // whole of the responsiveness the page bar gets - everything
                    // below it stays desktop-only by design.
                    flexWrap: 'wrap',
                    columnGap: 8,
                }}>
                    <Breadcrumb
                        style={{
                            margin: '16px 0',
                        }}
                        items={actualBreadcrumbs}
                    />

                    {/* Commands on the right */}
                    <MainPageCommands commands={commands}/>
                </div>
                {
                    description && <div
                        style={{
                            marginBottom: 16,
                        }}
                    >
                        <Typography.Text disabled>{description}</Typography.Text>
                    </div>
                }
            </Space>
        </>
    )
}
