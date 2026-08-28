import {Card, Skeleton} from "antd";

export default function PageSection({
                                        id,
                                        loading,
                                        title,
                                        extra,
                                        padding = false,
                                        height = '100%',
                                        children
                                    }) {
    return (
        <Card
            id={id}
            data-testid={id}
            size="small"
            title={loading ? "Loading..." : title}
            extra={extra}
            className="ot-line"
            style={{
                height: height,
            }}
            headStyle={{
                height: 48,
                backgroundColor: 'var(--ot-bg-section-head)'
            }}
            bodyStyle={{
                padding: padding ? 16 : 0,
                height: 'calc(100% - 47px)',
                width: '100%',
                overflowY: 'auto',
            }}
        >
            <Skeleton active loading={loading}>
                {children}
            </Skeleton>
        </Card>
    )
}