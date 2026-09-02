import {gql} from "graphql-request";

/**
 * The validation stamp fields a `ValidationChip` needs.
 *
 * A fragment rather than a note in a doc comment because `dataType` is easy to
 * leave out and its absence is silent: the chip still renders, it just falls
 * back to the neutral glyph for every stamp, and nobody notices until someone
 * wonders why a percentage stamp looks like a rubber stamp. Spread this into a
 * query and the field cannot be forgotten.
 */
export const gqlValidationChipStamp = gql`
    fragment ValidationChipStamp on ValidationStamp {
        id
        name
        image
        dataType {
            descriptor {
                id
            }
        }
    }
`
