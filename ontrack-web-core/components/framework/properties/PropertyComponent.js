import {Alert} from "antd";
import {Dynamic} from "@components/common/Dynamic";

export default function PropertyComponent({property, entityType, entityId}) {
    if (property.error) {
        return <Alert
            type="error"
            showIcon
            title="The value of this property cannot be read."
            description={property.error}
            data-testid="property-error"
        />
    }
    const shortTypeName = property.type.typeName.slice("net.nemerosa.ontrack.extension.".length)
    return <Dynamic
        path={`framework/properties/${shortTypeName}/Display`}
        props={{property, entityType, entityId}}
    />
}
