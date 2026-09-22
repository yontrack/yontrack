import {Card, Tag} from "antd";

export default function RowTag({children}) {
    return (
        <Card
            style={{
                height: '100%',
            }}
            styles={{
                body: {
                    padding: 8,
                },
            }}
        >
            {children}
        </Card>
    )
}