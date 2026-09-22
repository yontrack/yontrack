import {Space} from "antd";

export default function Rows({children, size = 16, padding = 16}) {
    return <Space orientation="vertical"
                  size={size}
                  className="ot-line"
                  style={{
                      padding: padding,
                  }}
    >
        {children}
    </Space>
}