import {Space, Typography} from "antd";

export default function PrCreatingData({data}) {
    const message = data.message
    const error = data.error
    return (
        <>
            <Space orientation="vertical">
                <Typography.Text>{message}</Typography.Text>
                {
                    error &&
                    <Typography.Text
                        copyable={{text: error}}
                    >
                        <pre style={{margin: 0}}>{error}</pre>
                    </Typography.Text>
                }
            </Space>
        </>
    )
}
