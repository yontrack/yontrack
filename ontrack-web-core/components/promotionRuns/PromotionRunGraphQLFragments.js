import {gql} from "graphql-request";

export const gqlPromotionRunContentFragment = gql`
    fragment PromotionRunContent on PromotionRun {
        id
        description
        annotatedDescription
        build {
            id
            name
            releaseProperty {
                value
            }
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
        promotionLevel {
            id
            name
            image
            fields {
                name
                displayName
                description
                type
                required
                options
                position
            }
        }
        fieldValues {
            name
            value
        }
    }
`