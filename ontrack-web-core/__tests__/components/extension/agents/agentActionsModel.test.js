/**
 * @jest-environment node
 */
import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"
import {
    AGENT_ALL,
    agentActionsAgentOptions,
    agentActionsFilterFromQuery,
    agentActionsUri,
    agentActionsVariables,
    agentActionsWindow,
    agentActivityTiles,
    assistedSharePercent,
    gqlAgentActions,
    gqlAgentActivityStats,
} from "@components/extension/agents/agentActionsModel"

const now = new Date("2026-10-08T10:00:00.000Z")

const stats = {
    window: 30,
    builds: 12,
    promotions: 3,
    deployments: 5,
    assistedBuilds: 4,
    knownBuilds: 10,
    unknownBuilds: 7,
    assistedShare: 0.4,
}

describe('the cross-project views of the agent activity', () => {

    it('keeps a window among 7, 30 and 90 days', () => {
        expect(agentActionsWindow(30)).toBe(30)
        expect(agentActionsWindow("90")).toBe(90)
        expect(agentActionsWindow(12)).toBe(7)
        expect(agentActionsWindow(undefined, 30)).toBe(30)
    })

    it('links to the page, filtered', () => {
        expect(agentActionsUri()).toBe("/extension/agents/actions")
        expect(agentActionsUri({
            window: 30,
            eventTypes: ["slot-pipeline-creation", "slot-pipeline-deployed"],
            project: "P",
        })).toBe("/extension/agents/actions?window=30&eventTypes=slot-pipeline-creation%2Cslot-pipeline-deployed&project=P")
    })

    it('reads the filter of the page from its URL', () => {
        expect(agentActionsFilterFromQuery({
            window: "30",
            eventTypes: "slot-pipeline-creation,slot-pipeline-deployed",
            project: "P",
            agent: "claude[agent]",
        })).toEqual({
            days: 30,
            eventTypes: ["slot-pipeline-creation", "slot-pipeline-deployed"],
            project: "P",
            agent: "claude[agent]",
        })
    })

    it('reads the last 7 days from an URL without a filter', () => {
        expect(agentActionsFilterFromQuery({})).toEqual({days: 7})
        expect(agentActionsFilterFromQuery({window: "1000"})).toEqual({days: 7})
    })

    it('turns the filter into the variables of the query', () => {
        expect(agentActionsVariables({days: 30, eventTypes: [], agent: AGENT_ALL}, now))
            .toEqual({from: "2026-09-08T10:00:00.000Z"})
        expect(agentActionsVariables({
            days: 7,
            eventTypes: ["new_build"],
            project: "P",
            agent: "claude[agent]",
        }, now)).toEqual({
            from: "2026-10-01T10:00:00.000Z",
            eventTypes: ["new_build"],
            project: "P",
            agent: "claude[agent]",
        })
        // A cleared filter is the window by default
        expect(agentActionsVariables({}, now)).toEqual({from: "2026-10-01T10:00:00.000Z"})
    })

    it('offers all the agents, then each one', () => {
        expect(agentActionsAgentOptions([{email: "claude[agent]", fullName: "Claude"}])).toEqual([
            {value: AGENT_ALL, label: "All agents"},
            {value: "claude[agent]", label: "Claude (claude[agent])"},
        ])
    })

    it('says the assisted share as a percentage, nothing without a known build', () => {
        expect(assistedSharePercent(0.4)).toBe("40%")
        expect(assistedSharePercent(1 / 3)).toBe("33%")
        expect(assistedSharePercent(null)).toBeNull()
    })

    it('gives four tiles, each linking to the page filtered on what it counts', () => {
        const tiles = agentActivityTiles(stats)
        expect(tiles.map(tile => [tile.key, tile.label, tile.value])).toEqual([
            ["builds", "Builds by agents", "12"],
            ["promotions", "Promotions by agents", "3"],
            ["deployments", "Deployments by agents", "5"],
            ["assisted", "Assisted share", "40%"],
        ])
        expect(tiles[0].href).toBe("/extension/agents/actions?window=30&eventTypes=new_build")
        expect(tiles[1].href).toBe("/extension/agents/actions?window=30&eventTypes=new_promotion_run")
        expect(tiles[2].href).toBe("/extension/agents/actions?window=30&eventTypes=slot-pipeline-creation%2Cslot-pipeline-deploying%2Cslot-pipeline-deployed")
        expect(tiles[3].detail).toBe("4 of 10 builds")
        expect(tiles[3].extra).toBe("7 unknown")
    })

    it('filters the page on the project of a widget narrowed to one project', () => {
        expect(agentActivityTiles(stats, ["P"])[0].href)
            .toBe("/extension/agents/actions?window=30&eventTypes=new_build&project=P")
        expect(agentActivityTiles(stats, ["P", "Q"])[0].href)
            .toBe("/extension/agents/actions?window=30&eventTypes=new_build")
    })

    it('shows no share without a known build', () => {
        const tile = agentActivityTiles({...stats, knownBuilds: 0, assistedShare: null})[3]
        expect(tile.value).toBe("-")
        expect(tile.ariaValue).toBe("no known build")
    })

    it('reads valid queries against the schema', () => {
        const schema = buildSchema(
            fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
        )
        expect(validate(schema, parse(gqlAgentActivityStats)).map(error => error.message)).toEqual([])
        expect(validate(schema, parse(gqlAgentActions)).map(error => error.message)).toEqual([])
    })
})
