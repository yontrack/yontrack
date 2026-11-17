import {graphQLCallMutation} from "@ontrack/graphql";
import {gql} from "graphql-request";

export class HttpConfigurations {

    constructor(ontrack) {
        this.ontrack = ontrack
    }

    async createConfig({name, url, authentication = {type: 'none', config: {}}}) {
        await graphQLCallMutation(
            this.ontrack.connection,
            'createConfiguration',
            gql`
                mutation CreateHttpConfiguration(
                    $name: String!,
                    $data: JSON!,
                ) {
                    createConfiguration(input: {
                        name: $name,
                        type: "http",
                        data: $data,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {
                name,
                data: {
                    url,
                    authentication,
                }
            }
        )
    }

}
