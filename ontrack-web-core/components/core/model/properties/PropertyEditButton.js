import InlineCommand from "@components/common/InlineCommand";
import {FaPencilAlt} from "react-icons/fa";
import PropertyDialog, {usePropertyDialog} from "@components/core/model/properties/PropertyDialog";

export default function PropertyEditButton({entityType, entityId, property}) {

    const dialog = usePropertyDialog({})

    const startDialog = () => {
        const propertyList = [property]
        dialog.start({entityType, entityId, propertyList, initialProperty: property})
    }

    return (
        <>
            <InlineCommand
                id="property-edit"
                icon={<FaPencilAlt/>}
                title="Edit this property"
                onClick={startDialog}
            />
            <PropertyDialog dialog={dialog}/>
        </>
    )
}