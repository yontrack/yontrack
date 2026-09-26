import {getExtensionShortName} from "@components/common/ExtensionUtils";
import {Dynamic} from "@components/common/Dynamic";

/**
 * Displays one decoration of an entity.
 *
 * @param decoration Decoration to display
 * @param entity Decorated entity, passed on to the decoration component (optional - most decorations
 * only need their own data)
 */
export default function Decoration({decoration, entity}) {
    const shortName = getExtensionShortName(decoration.decorationType)
    return <Dynamic
        path={`framework/decorations/${shortName}`}
        props={{decoration, entity}}
    />

}