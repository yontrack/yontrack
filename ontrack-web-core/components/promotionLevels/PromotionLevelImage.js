import EntityIcon from "@components/primitives/EntityIcon";

/**
 * The icon for a promotion level.
 *
 * A thin wrapper over `EntityIcon`, which owns the image-first rule and the
 * fallback. Kept as a named component because it reads better at the call sites
 * and because there are many of them; it must not grow behaviour of its own.
 */
export const PromotionLevelImage = ({promotionLevel, size = 24, disabled = false, onClick, tooltipText}) =>
    <EntityIcon
        kind="promotionLevel"
        entity={promotionLevel}
        size={size}
        disabled={disabled}
        onClick={onClick}
        tooltipText={tooltipText}
    />
