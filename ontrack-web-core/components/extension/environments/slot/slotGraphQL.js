import {gql} from "graphql-request"
import {gqlSlotPipelineData} from "@components/extension/environments/EnvironmentGraphQL"

/**
 * What the slot page needs about the slot itself: its name, and what this reader may do to it.
 *
 * Deliberately small. The header block asks for the slot's state on its own (it polls, and the page
 * does not), each tab asks for what it draws, and none of that belongs in a query whose answer
 * decides the page title and which tabs exist.
 */
export const gqlSlotPage = gql`
    query SlotPage($id: String!) {
        slotById(id: $id) {
            id
            qualifier
            description
            environment {
                id
                name
                order
            }
            project {
                id
                name
            }
            authorizations {
                name
                action
                authorized
            }
        }
    }
`

/**
 * The slot's deployment history, filtered and paged.
 *
 * Every filter is a nullable variable, so "no filter" is the same document with nulls rather than a
 * second document built by string concatenation - which is what makes it checkable against the
 * schema in one place, whatever the reader has typed.
 */
export const gqlSlotDeployments = gql`
    query SlotDeployments(
        $id: String!,
        $offset: Int!,
        $size: Int!,
        $status: SlotPipelineStatus,
        $buildName: String,
        $user: String,
    ) {
        slotById(id: $id) {
            pipelines(
                offset: $offset,
                size: $size,
                status: $status,
                buildName: $buildName,
                user: $user,
            ) {
                pageInfo {
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    ...SlotPipelineData
                    lastChange {
                        user
                        timestamp
                    }
                }
            }
        }
    }
    ${gqlSlotPipelineData}
`
