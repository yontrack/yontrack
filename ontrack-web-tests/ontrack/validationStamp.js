import {generate} from "@ontrack/utils";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

/**
 * Creates a validation stamp on a branch.
 *
 * @param branch Parent branch
 * @param name Name of the validation stamp (generated if not set)
 * @param dataType FQCN of the validation data type (optional)
 * @param dataTypeConfig Configuration of the data type, in its form (input) shape (optional)
 */
export const createValidationStamp = async (branch, name, {dataType, dataTypeConfig} = {}) => {
    const actualName = name ?? generate('vs_')

    const data = await graphQLCallMutation(
        branch.ontrack.connection,
        'createValidationStampById',
        gql`
            mutation CreateValidationStamp(
                $branchId: Int!,
                $name: String!,
                $dataType: String,
                $dataTypeConfig: JSON,
            ) {
                createValidationStampById(input: {
                    branchId: $branchId,
                    name: $name,
                    description: "",
                    dataType: $dataType,
                    dataTypeConfig: $dataTypeConfig,
                }) {
                    validationStamp {
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
            branchId: Number(branch.id),
            name: actualName,
            dataType,
            dataTypeConfig,
        }
    )

    return validationStampInstance(branch, data.createValidationStampById.validationStamp)
}


const validationStampInstance = (branch, data) => {
    const validationStamp = {
        ontrack: branch.ontrack,
        ...data,
        branch,
    }

    /**
     * Gets the data type of the validation stamp, with its stored and form configurations.
     */
    validationStamp.getDataType = async () => {
        const data = await graphQLCall(
            validationStamp.ontrack.connection,
            gql`
                query ValidationStampDataType($id: Int!) {
                    validationStamp(id: $id) {
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
            {id: Number(validationStamp.id)}
        )
        return data.validationStamp.dataType
    }

    return validationStamp
}