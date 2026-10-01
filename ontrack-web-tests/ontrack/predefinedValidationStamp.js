import {generate} from "@ontrack/utils";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

/**
 * Creates a predefined validation stamp.
 *
 * @param ontrack Ontrack connection
 * @param name Name of the predefined validation stamp (generated if not set)
 * @param dataType FQCN of the validation data type (optional)
 * @param dataTypeConfig Configuration of the data type, in its form (input) shape (optional)
 */
export const createPredefinedValidationStamp = async (ontrack, {name, dataType, dataTypeConfig} = {}) => {
    const data = await graphQLCallMutation(
        ontrack.connection,
        'createPredefinedValidationStamp',
        gql`
            mutation CreatePredefinedValidationStamp(
                $name: String!,
                $dataType: String,
                $dataTypeConfig: JSON,
            ) {
                createPredefinedValidationStamp(input: {
                    name: $name,
                    description: "",
                    dataType: $dataType,
                    dataTypeConfig: $dataTypeConfig,
                }) {
                    predefinedValidationStamp {
                        id
                        name
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        {
            name: name ?? generate('pvs_'),
            dataType,
            dataTypeConfig,
        }
    )
    const pvs = {
        ontrack,
        ...data.createPredefinedValidationStamp.predefinedValidationStamp,
    }

    /**
     * Gets the data type of the predefined validation stamp, with its stored and form configurations.
     */
    pvs.getDataType = async () => {
        const data = await graphQLCall(
            ontrack.connection,
            gql`
                query PredefinedValidationStampDataType($name: String!) {
                    predefinedValidationStampByName(name: $name) {
                        dataType {
                            descriptor {
                                id
                            }
                            config
                            formConfig
                        }
                    }
                }
            `,
            {name: pvs.name}
        )
        return data.predefinedValidationStampByName.dataType
    }

    return pvs
}
