/**
 * @jest-environment node
 */
import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"
import {gqlBuildAgentActionsProbe, gqlBuildAgents} from "@components/builds/agents/buildAgents"

/**
 * The queries of the Agents section of a build page, checked against the schema: a mistyped field
 * fails the whole document, and the build page with it.
 */
describe('the queries of the Agents section of a build', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    it('the content of the section is valid', () => {
        const errors = validate(schema, parse(gqlBuildAgents))
        expect(errors.map(error => error.message)).toEqual([])
    })

    it('the probe the build page selects is valid', () => {
        const document = `query Probe($id: Int!) { build(id: $id) { id ${gqlBuildAgentActionsProbe} } }`
        const errors = validate(schema, parse(document))
        expect(errors.map(error => error.message)).toEqual([])
    })
})
