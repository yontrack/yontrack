import {gql} from "graphql-request"

/**
 * Everything the Setup page draws, in one query.
 *
 * One query for both tabs and not one each: the Environments tab counts an environment's slots and
 * the Slots tab lists them, and two queries would let the count disagree with the list. It is a
 * configuration page - a few dozen rows, read rarely, no polling - so the slots are fetched whole
 * rather than counted server-side.
 *
 * The rules and workflows come back as ids only, because all the tab shows is how many there are;
 * what they *say* belongs to the slot's own Setup tab, which is one click away on every row.
 */
export const gqlSetup = gql`
    query Setup {
        environments {
            id
            name
            description
            order
            tags
            slots {
                id
                qualifier
                description
                project {
                    id
                    name
                }
                admissionRules {
                    id
                }
                workflows {
                    id
                }
            }
        }
    }
`
