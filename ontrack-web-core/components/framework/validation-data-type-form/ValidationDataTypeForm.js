import {Dynamic} from "@components/common/Dynamic";

export default function ValidationDataTypeForm({prefix, dataType}) {
    const shortTypeName = dataType.descriptor.id.slice("net.nemerosa.ontrack.extension.".length)

    return <Dynamic
        path={`framework/validation-data-type-form/${shortTypeName}`}
        props={{...dataType.formConfig, prefix}}
    />
}