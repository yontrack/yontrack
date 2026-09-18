import {gql} from "graphql-request"
import {gqlSlotCellData} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * The matrix, in one query.
 *
 * Every cell of the screen comes from here, `SlotCellData` included: the cell is a *pure* component
 * (see `SlotCell`), so a matrix of a hundred of them is this one request and not a hundred. The
 * server answers it without a query per slot either - the four fields the fragment selects on a slot
 * are resolved through batch loaders.
 */
export const gqlEnvironmentMatrix = gql`
    query EnvironmentMatrix(
        $filter: EnvironmentMatrixFilter,
        $offset: Int,
        $size: Int,
    ) {
        environmentMatrix(filter: $filter, offset: $offset, size: $size) {
            totalProjects
            offset
            size
            hasFavourites
            environments {
                id
                name
                order
                tags
                image
            }
            projects {
                project {
                    id
                    name
                }
                rows {
                    qualifier
                    slots {
                        ...SlotCellData
                    }
                }
            }
        }
    }
    ${gqlSlotCellData}
`

/**
 * How many environments exist at all, which is a different question from how many the matrix is
 * showing.
 *
 * An empty matrix means either "nothing matches your filter" or "this instance has no environment
 * yet", and those two deserve opposite screens - a hint to widen the filter, or an explanation of
 * the whole feature. The matrix alone cannot tell them apart, because it only ever reports the
 * environments its own rows fill.
 */
export const gqlEnvironmentsCount = gql`
    query EnvironmentsCount {
        environmentsCount
    }
`

/**
 * The labels a project can carry, for the toolbar's label filter.
 */
export const gqlProjectLabels = gql`
    query ProjectLabels {
        labels {
            id
            category
            name
            color
        }
    }
`

/**
 * Every tag any environment carries, for the toolbar's tag filter.
 *
 * Asked for separately rather than read from the matrix's own `environments`: those have already
 * been narrowed by the tag filter, so picking one tag would make every other option disappear from
 * the very control that set it.
 */
export const gqlEnvironmentTags = gql`
    query EnvironmentTags {
        environments {
            id
            tags
        }
    }
`
