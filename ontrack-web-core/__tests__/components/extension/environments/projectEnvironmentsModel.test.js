import fs from "fs"
import path from "path"
import {buildSchema, parse, validate} from "graphql"

import {
    hasSeveralQualifiers,
    projectMatrixFilter,
    projectViewFromQuery,
    projectViewToQuery,
    qualifierOptions,
    qualifiersFromEnvironments,
    slotGraphEdges,
    slotGraphNodes,
    VIEW_GRAPH,
    VIEW_MATRIX,
} from "@components/extension/environments/project/projectEnvironmentsModel"
import {
    gqlProjectQualifiers,
    gqlProjectSlotGraph,
} from "@components/extension/environments/project/projectEnvironmentsGraphQL"

describe('the project environments view, read from the URL', () => {

    it('opens on the graph when the address says nothing', () => {
        expect(projectViewFromQuery({})).toEqual({view: VIEW_GRAPH, qualifier: ''})
    })

    it('reads the view and the qualifier the address asks for', () => {
        expect(projectViewFromQuery({view: 'matrix', qualifier: 'canary'}))
            .toEqual({view: VIEW_MATRIX, qualifier: 'canary'})
    })

    it('ignores a view it does not know rather than showing nothing', () => {
        expect(projectViewFromQuery({view: 'pie-chart'}).view).toEqual(VIEW_GRAPH)
    })

    it('takes the first of a repeated parameter, since there is one view and one qualifier', () => {
        expect(projectViewFromQuery({view: ['matrix', 'graph'], qualifier: ['canary', 'other']}))
            .toEqual({view: VIEW_MATRIX, qualifier: 'canary'})
    })
})

describe('the project environments view, written to the URL', () => {

    it('writes nothing at all for the default view of the default qualifier', () => {
        expect(projectViewToQuery({view: VIEW_GRAPH, qualifier: ''})).toEqual({})
    })

    it('writes the view only when it is not the graph', () => {
        expect(projectViewToQuery({view: VIEW_MATRIX, qualifier: ''})).toEqual({view: VIEW_MATRIX})
    })

    it('writes the qualifier only when it is not the default one', () => {
        expect(projectViewToQuery({view: VIEW_GRAPH, qualifier: 'canary'})).toEqual({qualifier: 'canary'})
    })
})

describe('the qualifier selector', () => {

    it('always offers the default qualifier, first and named', () => {
        expect(qualifierOptions([])).toEqual([{value: '', label: 'Default'}])
    })

    it('offers each named qualifier once, alphabetically, after the default one', () => {
        expect(qualifierOptions(['canary', 'blue', 'canary'])).toEqual([
            {value: '', label: 'Default'},
            {value: 'blue', label: 'blue'},
            {value: 'canary', label: 'canary'},
        ])
    })

    it('is drawn only when the project has more than the default qualifier', () => {
        expect(hasSeveralQualifiers(qualifierOptions([]))).toBe(false)
        expect(hasSeveralQualifiers(qualifierOptions(['', '']))).toBe(false)
        expect(hasSeveralQualifiers(qualifierOptions(['canary']))).toBe(true)
    })

    it('collects the qualifiers across every environment holding a slot of the project', () => {
        const environments = [
            {id: '1', slots: [{qualifier: ''}, {qualifier: 'canary'}]},
            {id: '2', slots: [{qualifier: ''}]},
            {id: '3', slots: []},
        ]
        expect(qualifiersFromEnvironments(environments)).toEqual(['', 'canary', ''])
        expect(qualifierOptions(qualifiersFromEnvironments(environments))).toEqual([
            {value: '', label: 'Default'},
            {value: 'canary', label: 'canary'},
        ])
    })
})

describe('the slot graph, as React Flow draws it', () => {

    /** staging -> production, which is the shape the demo's petclinic has. */
    const graph = {
        slotNodes: [
            {slot: {id: 'slot-staging', environment: {id: '1', order: 100}}, parents: []},
            {
                slot: {id: 'slot-production', environment: {id: '2', order: 200}},
                parents: [{id: 'slot-staging'}],
            },
        ],
    }

    it('makes one node per slot, carrying the slot and nothing else', () => {
        expect(slotGraphNodes(graph)).toEqual([
            {
                id: 'slot-staging',
                position: {x: 0, y: 0},
                data: {slot: graph.slotNodes[0].slot},
                type: 'slotNode',
            },
            {
                id: 'slot-production',
                position: {x: 0, y: 0},
                data: {slot: graph.slotNodes[1].slot},
                type: 'slotNode',
            },
        ])
    })

    it('draws an edge from a parent slot to the slot admitting from it', () => {
        const edges = slotGraphEdges(graph)
        expect(edges).toHaveLength(1)
        expect(edges[0].id).toEqual('slot-staging-slot-production')
        expect(edges[0].source).toEqual('slot-staging')
        expect(edges[0].target).toEqual('slot-production')
    })

    it('draws nothing at all for a project with no slot', () => {
        expect(slotGraphNodes(null)).toEqual([])
        expect(slotGraphEdges(null)).toEqual([])
        expect(slotGraphNodes({slotNodes: []})).toEqual([])
    })
})

describe('the Matrix view of one project', () => {

    it('pins the matrix on the project by its exact name, over every project rather than favourites', () => {
        expect(projectMatrixFilter({id: 12, name: 'petclinic'})).toEqual({
            projects: ['petclinic'],
            scope: 'all',
        })
    })

    it('pins nothing while the project is not known yet', () => {
        expect(projectMatrixFilter(null).projects).toEqual([])
    })
})

describe('the project environments documents', () => {

    const schema = buildSchema(
        fs.readFileSync(path.join(process.cwd(), 'ontrack.graphql'), 'utf-8'),
    )

    const check = (name, document) => {
        it(`${name} is valid`, () => {
            const errors = validate(schema, parse(document))
            expect(errors.map(error => error.message)).toEqual([])
        })
    }

    check('gqlProjectSlotGraph', gqlProjectSlotGraph)
    check('gqlProjectQualifiers', gqlProjectQualifiers)
})
