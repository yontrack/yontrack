/**
 * @jest-environment node
 */
import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"
import {
    AGENT_ACTIVITY_DEFAULT_WINDOW,
    AGENT_ACTIVITY_WINDOWS,
    agentActivityFilter,
    agentActivityFrom,
    agentActivityVariables,
    gqlAgentActivity,
} from "@components/core/admin/agents/agentActivityModel"

const now = new Date("2026-10-08T10:00:00.000Z")

describe('the activity of an agent', () => {

    it('offers 7, 30 and 90 days, 7 by default', () => {
        expect(AGENT_ACTIVITY_WINDOWS).toEqual([7, 30, 90])
        expect(AGENT_ACTIVITY_DEFAULT_WINDOW).toBe(7)
    })

    it('starts a window of days before now', () => {
        expect(agentActivityFrom(7, now)).toBe("2026-10-01T10:00:00.000Z")
        expect(agentActivityFrom(90, now)).toBe("2026-07-10T10:00:00.000Z")
    })

    it('filters on the last 7 days by default', () => {
        expect(agentActivityFilter(undefined, now)).toEqual({days: 7, from: "2026-10-01T10:00:00.000Z"})
    })

    it('gives no variable for an empty filter on the event types or the project', () => {
        expect(agentActivityVariables({id: "12", filter: {...agentActivityFilter(30, now), eventTypes: []}}))
            .toEqual({id: 12, from: "2026-09-08T10:00:00.000Z", offset: 0, size: 20})
    })

    it('gives the event types and the project when set', () => {
        expect(agentActivityVariables({
            id: 12,
            filter: {...agentActivityFilter(7, now), eventTypes: ["new_validation_run"], project: "P"},
            offset: 20,
            size: 20,
        })).toEqual({
            id: 12,
            from: "2026-10-01T10:00:00.000Z",
            eventTypes: ["new_validation_run"],
            project: "P",
            offset: 20,
            size: 20,
        })
    })

    it('reads a valid query against the schema', () => {
        const schema = buildSchema(
            fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
        )
        const errors = validate(schema, parse(gqlAgentActivity))
        expect(errors.map(error => error.message)).toEqual([])
    })
})
