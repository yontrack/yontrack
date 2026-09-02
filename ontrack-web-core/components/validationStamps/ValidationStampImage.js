import EntityIcon from "@components/primitives/EntityIcon";

/**
 * The icon for a validation stamp.
 *
 * A thin wrapper over `EntityIcon`, which owns the image-first rule and the
 * fallback. Note that the fallback now honours `size`: this component used to
 * drop it on the way to `GeneratedIcon`, so a 24px stamp header silently shrank
 * to 16px for any stamp with no uploaded image.
 *
 * `ValidationChip` deliberately does NOT go through here - it substitutes a
 * data-type glyph for the initials tile, because a list of chips is scanned
 * rather than read and two-letter tiles stop being distinguishable in one.
 */
export default function ValidationStampImage({validationStamp, size = 16, disabled = false, onClick, tooltipText}) {
    return <EntityIcon
        kind="validationStamp"
        entity={validationStamp}
        size={size}
        disabled={disabled}
        onClick={onClick}
        tooltipText={tooltipText}
    />
}
