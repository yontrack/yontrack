import {gql} from "graphql-request";
import {useQuery} from "@components/services/useQuery";

export const useValidationStampByName = (project, branch, validationStamp) => {
    const {data: validationStampObject} = useQuery(
        gql`
            query GetValidationStampByName(
                $project: String!,
                $branch: String!,
                $validationStamp: String!,
            ) {
                validationStampByName(project: $project, branch: $branch, name: $validationStamp) {
                    id
                    name
                    description
                    image
                    branch {
                        id
                        name
                        displayName
                        project {
                            id
                            name
                        }
                    }
                }
            }
        `,
        {
            variables: {project, branch, validationStamp},
            condition: project && branch && validationStamp,
            deps: [project, branch, validationStamp],
            dataFn: data => data.validationStampByName,
        }
    )
    return {
        validationStampObject,
    }
}