import {Space} from "antd";

export function NodeSection({icon, title, children}) {
    return (
        <>
            <Space
                orientation="vertical"
                style={{
                    borderTop: "solid 1px gray",
                    padding: '8px',
                    width: '100%',
                }}
            >
                <Space>
                    {icon}
                    {title}
                </Space>
                {children}
            </Space>
        </>
    )
}