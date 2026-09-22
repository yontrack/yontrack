import {Alert, Typography} from "antd";

/**
 * What the delivery map says when it has checkpoints but nothing joins them.
 *
 * The promotion levels are still drawn - they are what a build has to pass through whether or not
 * anything is configured to grant them - but a map whose subject is dependencies and which shows no
 * dependency says nothing on its own. Rather than leave the reader to wonder whether the branch has
 * no configuration or the map is broken, it says which configuration draws the lines.
 */
export default function DeliveryMapNoDependencies() {
    return (
        <Alert
            type="info"
            showIcon
            data-testid="delivery-map-no-dependencies"
            title={
                <Typography.Text>
                    Nothing joins these checkpoints yet
                </Typography.Text>
            }
            description={
                <Typography.Text type="secondary">
                    Auto promotion on a promotion level draws the validation stamps and promotions
                    which grant it, and promotion dependencies draw the promotions it cannot be
                    granted without.
                </Typography.Text>
            }
        />
    )
}
