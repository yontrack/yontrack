import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

/**
 * Whether the licence of the instance enables a licensed feature.
 *
 * @param featureId ID of the licensed feature, like `extension.agents`
 * @return `{enabled, loading}` - `enabled` is `undefined` until the licence is known, so that nothing
 * is said about the licence before it is
 */
export function useLicensedFeature(featureId) {
    const {data, loading, finished} = useQuery(
        gql`
            query LicensedFeature {
                licenseInfo {
                    license {
                        licensedFeatures {
                            id
                            enabled
                        }
                    }
                }
            }
        `,
        {
            deps: [featureId],
            dataFn: data => data.licenseInfo?.license?.licensedFeatures ?? [],
        }
    )
    const enabled = finished && data ?
        data.some(feature => feature.id === featureId && feature.enabled) :
        undefined
    return {
        enabled,
        loading: loading || !finished,
    }
}
