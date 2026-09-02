import fs from "fs";
import path from "path";
import {buildSchema, parse, validate} from "graphql";

import {gqlBuilds} from "@components/branches/branchQueries";
import {gqlValidationRunTableContent} from "@components/validationRuns/ValidationRunGraphQLFragments";

/**
 * `ValidationChip` needs `dataType` on its stamp, and the field is now supplied
 * by a shared fragment spread into two queries that are otherwise unrelated. A
 * misspelled spread, a fragment that never got embedded, or a field the backend
 * does not have would all render perfectly well and simply show the neutral
 * glyph for every stamp - so it is checked against the schema here rather than
 * discovered by someone wondering why a percentage stamp looks like a rubber
 * stamp.
 */
describe('validation chip queries', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const expectValid = (document) => {
        const errors = validate(schema, parse(document))
        expect(errors.map(error => error.message)).toEqual([])
    }

    it('the builds query is valid', () => {
        expectValid(gqlBuilds)
    })

    it('the validation run table query is valid', () => {
        // The table fragment is not an operation on its own; wrap it in the
        // smallest query that uses it, which is what its consumers do.
        expectValid(`
            ${gqlValidationRunTableContent}
            query TestValidationRuns($buildId: Int!) {
                build(id: $buildId) {
                    validationRuns {
                        ...ValidationRunTableContent
                    }
                }
            }
        `)
    })

    it('both carry the stamp data type the chip falls back on', () => {
        expect(gqlBuilds).toContain('...ValidationChipStamp')
        expect(gqlValidationRunTableContent).toContain('...ValidationChipStamp')
        expect(gqlBuilds).toContain('fragment ValidationChipStamp on ValidationStamp')
        expect(gqlValidationRunTableContent).toContain('fragment ValidationChipStamp on ValidationStamp')
    })
})
