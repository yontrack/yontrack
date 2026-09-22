import {Empty, Space, Typography} from "antd";

/**
 * What the delivery map says when it has nothing to draw.
 *
 * This is the first thing many users will see, because a branch has to be configured before the map
 * has anything on it. An empty rectangle would read as a broken page; the point of this panel is to
 * say what the map WOULD show and which configuration produces it.
 */
export default function DeliveryMapEmpty() {
    return (
        <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            data-testid="delivery-map-empty"
            description={
                <Space orientation="vertical">
                    <Typography.Text data-testid="delivery-map-empty-message">
                        Nothing on the delivery map yet
                    </Typography.Text>
                    <Typography.Text type="secondary">
                        The map shows what a build on this branch has to pass through on its way to an
                        environment: its promotion levels and validation stamps, and the dependencies
                        between them. Create a promotion level on this branch to start it off.
                    </Typography.Text>
                </Space>
            }
        />
    )
}
