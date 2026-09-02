// @ts-check

import {restPromotionLevelImageUri, restValidationStampImageUri} from "@components/common/Links"
import {useEventForRefresh} from "@components/common/EventsContext"
import ProxyImage from "@components/common/ProxyImage"
import GeneratedIcon from "@components/common/icons/GeneratedIcon"

/**
 * The icon for a promotion level or a validation stamp.
 *
 * IMAGE-FIRST: when the entity has an uploaded image it wins, always. There is
 * no computed medal, no tier gradient, no hue derived from the level's ordinal
 * position - promotion levels in Yontrack carry a position and no tier, and
 * turning "level 2 of 4" into "silver" would present an arithmetic accident to
 * the user as a fact about their branch. When there is no image, the fallback
 * below is what the user gets, and it is expected to be good rather than
 * apologetic.
 *
 * This generalises what `PromotionLevelImage` did for one entity type. Both
 * `PromotionLevelImage` and `ValidationStampImage` are now thin wrappers over
 * it, so the image/fallback decision exists once.
 */

/**
 * @typedef {Object} EntityIconKind
 * @property {string} refreshEvent Page event fired when the image is replaced.
 * @property {(entity: any) => string} restImageUri
 * @property {(id: any) => string} imageTestId
 * @property {(id: any) => string} fallbackTestId
 */

/**
 * The entity types that have an uploadable image.
 *
 * The two test ids are deliberately DIFFERENT strings. The UI tests assert on
 * the image one and read its `src`, so the fallback must not answer to the same
 * id - a fallback matching `*-image-*` would turn "no image was uploaded" into a
 * confusing assertion failure about a missing `src` instead of a clear one about
 * a missing element.
 *
 * @type {Record<string, EntityIconKind>}
 */
export const ENTITY_ICON_KINDS = {
    promotionLevel: {
        refreshEvent: "promotionLevel.image",
        restImageUri: restPromotionLevelImageUri,
        imageTestId: (id) => `promotion-level-image-${id}`,
        fallbackTestId: (id) => `promotion-level-icon-${id}`,
    },
    validationStamp: {
        refreshEvent: "validationStamp.image",
        restImageUri: restValidationStampImageUri,
        imageTestId: (id) => `validation-stamp-image-${id}`,
        fallbackTestId: (id) => `validation-stamp-icon-${id}`,
    },
}

/**
 * @param {Object} props
 * @param {'promotionLevel'|'validationStamp'} props.kind
 * @param {{id: any, name: string, image?: boolean}} [props.entity]
 * @param {number} [props.size] Box size in px, applied to BOTH the image and the
 *   fallback. The fallback silently ignoring it was a real defect: a 32px medal
 *   collapsed to a 24px tile for any level whose owner never uploaded an image.
 * @param {boolean} [props.disabled]
 * @param {() => void} [props.onClick]
 * @param {string} [props.tooltipText]
 * @param {import('react').ReactNode} [props.fallback] Replaces the initials tile
 *   when there is no image. Used by `ValidationChip`, which draws a glyph for
 *   the stamp's data type instead. Ignored when there IS an image.
 */
export default function EntityIcon({
                                       kind,
                                       entity,
                                       size = 24,
                                       disabled = false,
                                       onClick,
                                       tooltipText,
                                       fallback,
                                   }) {

    const config = ENTITY_ICON_KINDS[kind]

    // Unconditional, and defensive on `config`: a kind outside the union above
    // is a typo or a kind since removed, and an icon is never worth crashing a
    // page over. It resolves to an event name nothing is ever fired under,
    // which is exactly the no-op wanted, without changing the hook order.
    const refreshCount = useEventForRefresh(config?.refreshEvent ?? `entityIcon.unknown.${kind}`)

    if (!entity) return null

    // An unknown kind has no REST URI to fetch from, so it can only fall back.
    // Rendering the fallback beats crashing a page over an icon.
    if (entity.image && config) {
        return <ProxyImage
            id={config.imageTestId(entity.id)}
            restUri={`${config.restImageUri(entity)}?key=${refreshCount}`}
            alt={entity.name}
            width={size}
            height={size}
            onClick={onClick}
            tooltipText={tooltipText}
            disabled={disabled}
        />
    }

    if (fallback) return <>{fallback}</>

    return <GeneratedIcon
        id={config?.fallbackTestId(entity.id)}
        name={entity.name}
        colorIndex={entity.id}
        size={size}
        onClick={onClick}
        tooltipText={tooltipText}
        disabled={disabled}
    />
}
