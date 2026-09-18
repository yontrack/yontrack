import {gql} from "graphql-request"
import {gqlSlotCellData} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * The project's slot graph, with every node already carrying what a cell draws.
 *
 * `SlotCellData` is asked of the node's slot rather than of anything graph-shaped, because a node of
 * this graph **is** a slot cell (#1795): the same fragment the matrix asks of a hundred slots, asked
 * here of the handful a project has. That is what makes the graph one request rather than one per
 * node, and what makes the two screens say exactly the same thing about the same slot.
 *
 * `parents` needs no more than an id: a parent is always a node of this very graph, so the edge only
 * has to name it.
 */
export const gqlProjectSlotGraph = gql`
    query ProjectSlotGraph(
        $id: Int!,
        $qualifier: String!,
    ) {
        project(id: $id) {
            id
            name
            slotGraph(qualifier: $qualifier) {
                slotNodes {
                    slot {
                        ...SlotCellData
                    }
                    parents {
                        id
                    }
                }
            }
        }
    }
    ${gqlSlotCellData}
`

/**
 * Which qualifiers a project's slots carry, for the selector.
 *
 * Asked of the environments and not of the project: `slotGraph` takes *one* qualifier and answers
 * for it alone, so it cannot be used to discover the others, and the schema keeps the project's
 * slots on `Environment.slots(projects:)`. A qualifier used in several environments comes back
 * several times - `qualifierOptions` makes the set.
 */
export const gqlProjectQualifiers = gql`
    query ProjectQualifiers($projectName: String!) {
        environments {
            id
            slots(projects: [$projectName]) {
                id
                qualifier
            }
        }
    }
`
