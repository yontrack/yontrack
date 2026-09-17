import {gql} from "graphql-request";

/**
 * Everything the shared label chip needs to render: the display parts
 * (category, name), the tooltip (description) and both colours.
 *
 * Any query feeding a `LabelChip` uses this fragment rather than listing the
 * fields again - the chip is used on the admin page, on the project page and in
 * the project lists.
 */
export const gqlLabelFragment = gql`
    fragment labelFragment on Label {
        id
        category
        name
        description
        color
        foregroundColor
    }
`
