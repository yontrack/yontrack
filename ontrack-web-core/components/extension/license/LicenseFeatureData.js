import {Dynamic} from "@components/common/Dynamic";

/**
 * Renders the data of a licensed feature through its `framework/license-feature-data/<featureId>/Info`
 * component. A feature with no data needs no such component and renders nothing.
 */
export default function LicenseFeatureData({featureId, featureData}) {

    if (!featureData || featureData.length === 0) {
        return null
    }

    const featureDataObject = {}
    for (const {name, value} of featureData) {
        featureDataObject[name] = value
    }

    return (
        <>
            <Dynamic
                path={`framework/license-feature-data/${featureId}/Info`}
                props={featureDataObject}
            />
        </>
    )
}
