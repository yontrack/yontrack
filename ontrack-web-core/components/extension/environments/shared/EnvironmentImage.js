import GeneratedIcon from "@components/common/icons/GeneratedIcon"
import ProxyImage from "@components/common/ProxyImage"
import {restEnvironmentImageUri} from "@components/extension/environments/EnvironmentsLinksUtils"

/**
 * An environment's icon, drawn from an environment the caller already has.
 *
 * The difference with `EnvironmentIcon` is the query: that one is given an *id* and fetches the
 * environment to find out whether it has an image. That is fine beside a single slot and wrong in a
 * matrix header, where it would be one request per column on every poll. Here the three fields it
 * needs - `id`, `name`, `order`, `image` - come with whatever query already asked for the
 * environment.
 *
 * @param {Object} environment The environment, with `id`, `name`, `order` and `image`
 * @param {number} size Pixel size of the icon
 */
export default function EnvironmentImage({environment, size = 16, onClick, tooltipText}) {

    if (!environment) return null

    const tooltip = tooltipText ?? environment.name

    return environment.image ?
        <ProxyImage
            restUri={restEnvironmentImageUri(environment)}
            alt={environment.name}
            width={size}
            height={size}
            onClick={onClick}
            tooltipText={tooltip}
        /> :
        <GeneratedIcon
            name={environment.name}
            colorIndex={environment.order}
            onClick={onClick}
            tooltipText={tooltip}
        />
}
