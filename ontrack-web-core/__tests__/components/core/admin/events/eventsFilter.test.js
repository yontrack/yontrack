import dayjs from "dayjs"
import utc from "dayjs/plugin/utc"
import {
    ACTOR_ALL,
    eventEntityLink,
    eventsActorOptions,
    eventsFilterVariables,
} from "@components/core/admin/events/eventsFilter"
import {eventsExportUri} from "@components/common/Links"

dayjs.extend(utc)

/**
 * The filter form of the events page holds a date-time range, a user, event types and a
 * project: `eventsFilterVariables` turns it into the variables of the `events` query.
 */
describe('eventsFilterVariables', () => {

    it('an empty form gives no variable, so that every event is listed', () => {
        expect(eventsFilterVariables({})).toEqual({})
    })

    it('blank and empty values give no variable', () => {
        expect(eventsFilterVariables({
            range: null,
            user: "  ",
            eventTypes: [],
            project: null,
        })).toEqual({})
    })

    it('the range gives the from and to times, in UTC', () => {
        expect(eventsFilterVariables({
            range: [
                dayjs.utc("2026-10-01T08:30:00Z"),
                dayjs.utc("2026-10-07T18:00:00Z"),
            ],
        })).toEqual({
            from: "2026-10-01T08:30:00.000Z",
            to: "2026-10-07T18:00:00.000Z",
        })
    })

    it('an open range gives only the time it has', () => {
        expect(eventsFilterVariables({
            range: [dayjs.utc("2026-10-01T08:30:00Z"), null],
        })).toEqual({
            from: "2026-10-01T08:30:00.000Z",
        })
        expect(eventsFilterVariables({
            range: [null, dayjs.utc("2026-10-07T18:00:00Z")],
        })).toEqual({
            to: "2026-10-07T18:00:00.000Z",
        })
    })

    it('the user, the event types and the project are passed as they are, the user trimmed', () => {
        expect(eventsFilterVariables({
            user: " adm ",
            eventTypes: ["new_project", "new_branch"],
            project: "my-project",
        })).toEqual({
            user: "adm",
            eventTypes: ["new_project", "new_branch"],
            project: "my-project",
        })
    })

    it('the actor is passed as it is, and "all" gives no variable', () => {
        expect(eventsFilterVariables({actor: "agent"})).toEqual({actor: "agent"})
        expect(eventsFilterVariables({actor: "human"})).toEqual({actor: "human"})
        expect(eventsFilterVariables({actor: "claude[agent]"})).toEqual({actor: "claude[agent]"})
        expect(eventsFilterVariables({actor: ACTOR_ALL})).toEqual({})
        expect(eventsFilterVariables({actor: null})).toEqual({})
    })

})

/**
 * The actor filter offers all the events, those of the persons, those of the agents, and each
 * registered agent.
 */
describe('eventsActorOptions', () => {

    it('without any agent, all, humans and agents', () => {
        expect(eventsActorOptions([])).toEqual([
            {value: "all", label: "All"},
            {value: "human", label: "Humans"},
            {value: "agent", label: "Agents"},
        ])
    })

    it('each registered agent, by its identifier', () => {
        const options = eventsActorOptions([
            {id: 1, email: "claude[agent]", fullName: "Claude"},
            {id: 2, email: "codex[agent]", fullName: "Codex"},
        ])
        expect(options.map(option => option.value ?? option.label)).toEqual(["all", "human", "agent", "Agent"])
        expect(options[3].options).toEqual([
            {value: "claude[agent]", label: "Claude (claude[agent])"},
            {value: "codex[agent]", label: "Codex (codex[agent])"},
        ])
    })

})

/**
 * The entities of an event come with their type, ID and display name (which names the type, like
 * `Branch P/X`) only: the link to their page is computed from their type and ID.
 */
describe('eventEntityLink', () => {

    it.each([
        ["PROJECT", "/project/10"],
        ["BRANCH", "/branch/10"],
        ["PROMOTION_LEVEL", "/promotionLevel/10"],
        ["VALIDATION_STAMP", "/validationStamp/10"],
        ["BUILD", "/build/10"],
        ["PROMOTION_RUN", "/promotionRun/10"],
        ["VALIDATION_RUN", "/validationRun/10"],
    ])('%s links to its page', (type, href) => {
        expect(eventEntityLink({type, id: 10, displayName: "Some entity"})).toEqual({
            href,
            text: "Some entity",
        })
    })

    it('an unknown type has no link', () => {
        expect(eventEntityLink({type: "UNKNOWN", id: 10, displayName: "Some entity"})).toEqual({
            href: null,
            text: "Some entity",
        })
    })

})

/**
 * The export of the events is downloaded through the protected downloads of the UI, with the
 * variables of the filter as its query string.
 */
describe('eventsExportUri', () => {

    it('with no filter, only the format', () => {
        expect(eventsExportUri("csv", {})).toBe("/api/protected/downloads/events/export?format=csv")
    })

    it('the variables of the filter as parameters, the event types repeated', () => {
        expect(eventsExportUri("json", eventsFilterVariables({
            range: [dayjs.utc("2026-10-01T08:30:00Z"), null],
            user: "adm",
            eventTypes: ["new_project", "new_branch"],
            project: "my project",
        }))).toBe(
            "/api/protected/downloads/events/export?format=json" +
            "&from=2026-10-01T08%3A30%3A00.000Z" +
            "&user=adm" +
            "&eventTypes=new_project&eventTypes=new_branch" +
            "&project=my+project"
        )
    })

    it('the actor of the filter as a parameter', () => {
        expect(eventsExportUri("csv", eventsFilterVariables({actor: "claude[agent]"}))).toBe(
            "/api/protected/downloads/events/export?format=csv&actor=claude%5Bagent%5D"
        )
    })

})
