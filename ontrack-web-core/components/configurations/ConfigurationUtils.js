import {gql} from "graphql-request";
import {callGraphQL} from "@components/services/GraphQL";

export const prepareConfigValues = (values, configurationType) => {
    // The configuration mutations expect three fields:
    // - type: configuration type
    // - name: the name of the configuration
    // - data: the values for this configuration
    // Right now, all values are flattened into the values
    const input = {
        type: configurationType,
        name: values.name,
        data: {...values},
    }
    delete input.data.name
    return input
}

export function testConfig(config, configurationType) {
    return callGraphQL({
        query: gql`
            mutation TestConfiguration(
                $type: String!,
                $name: String!,
                $data: JSON!,
            ) {
                testConfiguration(input: {
                    type: $type,
                    name: $name,
                    data: $data,
                }) {
                    connectionResult {
                        type
                        message
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        variables: prepareConfigValues(config, configurationType),
    }).then(data => {
        let connectionResult = undefined
        const node = data.testConfiguration
        if (node.errors) {
            const {message} = node.errors[0]
            connectionResult = {
                type: 'ERROR',
                message,
            }
        } else {
            connectionResult = node.connectionResult
        }
        return connectionResult
    });
}
