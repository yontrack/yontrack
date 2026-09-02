import fs from "fs";
import path from "path";
import {buildSchema, parse, validate} from "graphql";

import {
    gqlPipelineBranchFacts,
    gqlPipelineBuilds,
    gqlPipelineBuildInspection,
} from "@components/branches/views/pipeline/pipelineQueries";

/**
 * The pipeline view's documents, checked against the schema.
 *
 * Worth checking here rather than in the browser because most of what could go wrong is silent: a
 * mistyped field makes the whole query fail at runtime with a message nobody reads until a page is
 * blank, and a missing `dataType` on a stamp makes every validation chip fall back to the neutral
 * glyph while looking perfectly fine.
 */
describe('pipeline view queries', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const expectValid = (document) => {
        const errors = validate(schema, parse(document))
        expect(errors.map(error => error.message)).toEqual([])
    }

    it('the branch facts query is valid', () => {
        expectValid(gqlPipelineBranchFacts)
    })

    it('the builds query is valid', () => {
        expectValid(gqlPipelineBuilds)
    })

    it('the build inspection query is valid', () => {
        expectValid(gqlPipelineBuildInspection)
    })

    it('the stage cards count builds, not promotion runs', () => {
        // The same build can be promoted again to the same level, so the number of runs is not the
        // number of builds which reached it - and the card claims the latter
        expect(gqlPipelineBranchFacts).toContain('promotedBuildCount')
    })

    it('the total builds stat is read outside the active filter', () => {
        // A total which moves when you filter is a filter readout, not a branch fact
        expect(gqlPipelineBranchFacts).toContain('allBuilds: buildsPaginated(size: 1)')
    })

    it('the chips carry the stamp data type they fall back on', () => {
        expect(gqlPipelineBuilds).toContain('...ValidationChipStamp')
        expect(gqlPipelineBuildInspection).toContain('...ValidationChipStamp')
    })

})
