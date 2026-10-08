/**
 * @jest-environment node
 */
import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"
import {gqlEvents, gqlEventsAgents, gqlEventsExport} from "@components/core/admin/events/eventsQueries"

/**
 * The queries of the events page, checked against the schema: a mistyped field fails the whole
 * document, and the page then shows no event at all.
 */
describe('the queries of the events page', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    it.each([
        ['the events, with their actor', gqlEvents],
        ['the information about the export', gqlEventsExport],
        ['the agents of the actor filter', gqlEventsAgents],
    ])('%s is valid', (_, document) => {
        const errors = validate(schema, parse(document))
        expect(errors.map(error => error.message)).toEqual([])
    })

    it('the events and the export are filtered on the actor', () => {
        expect(gqlEvents).toContain('actor: $actor')
        expect(gqlEventsExport).toContain('actor: $actor')
    })
})
