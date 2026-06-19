import ValidationStamp from "@components/validationStamps/ValidationStamp";

/**
 * Validation stamp header for the validation stamp columns in a list of builds.
 * @param validationStamp Validation stamp to display
 * @param selectable If the validation stamp can be selected instead of linking to the validation stamp page
 * @param tooltip Alternative tooltip
 * @param selected Current selection for this header (used only if `selectable` is `true`)
 * @param onSelect Callback on selection (used only if `selectable` is `true`)
 */
export default function ValidationStampHeader({
                                                  validationStamp,
                                                  selectable,
                                                  tooltip,
                                                  selected, onSelect,
                                              }) {
    return (
        <>
            <ValidationStamp
                validationStamp={validationStamp}
                displayImage={true}
                displayText={false}
                tooltip={tooltip}
                displayTooltip={true}
                displayLink={!selectable}
                onClick={selectable && onSelect ? onSelect : undefined}
                selectable={selectable}
                selected={selected}
                imageSize={26}
            />
        </>
    )
}