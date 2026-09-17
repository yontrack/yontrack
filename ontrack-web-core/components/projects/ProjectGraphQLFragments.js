import {gql} from "graphql-request";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";

/**
 * Minimal content for a project.
 *
 * The labels are part of it because `ProjectBox` displays them as chips, and every project list
 * of the UI is built on `ProjectBox` - a list which selected the fields itself would show a
 * project without its labels for no reason the user could see.
 *
 * It carries `labelFragment` with it: a query using this fragment and `...labelFragment` of its
 * own must not add `${gqlLabelFragment}` again, or the document declares the fragment twice and
 * the whole query is rejected.
 */
export const gqlProjectContentFragment = gql`
    fragment ProjectContent on Project {
        id
        name
        disabled
        description
        annotatedDescription
        labels {
            ...labelFragment
        }
    }

    ${gqlLabelFragment}
`
