import workflowNodeDepths from "@components/extension/workflows/workflowNodeDepths";

/**
 * Builds the `nodes` list of a workflow definition from a map of
 * node id -> list of parent ids.
 */
const nodes = (spec) => Object.entries(spec).map(([id, parents]) => ({
    id,
    parents: parents.map(parentId => ({id: parentId})),
}))

/**
 * Reduces the result of `workflowNodeDepths` to the ids per column, for readable assertions.
 */
const ids = (columns) => columns.map(column => column.map(node => node.id))

describe('workflowNodeDepths', () => {

    it('returns no column for no node', () => {
        expect(workflowNodeDepths([])).toEqual([])
    })

    it('returns no column for undefined nodes', () => {
        expect(workflowNodeDepths(undefined)).toEqual([])
    })

    it('puts a single node in the first column', () => {
        expect(ids(workflowNodeDepths(nodes({a: []})))).toEqual([['a']])
    })

    it('gives one node per column for a linear chain', () => {
        const columns = workflowNodeDepths(nodes({
            a: [],
            b: ['a'],
            c: ['b'],
        }))
        expect(ids(columns)).toEqual([['a'], ['b'], ['c']])
    })

    it('gives one wide column for a fan-out', () => {
        const columns = workflowNodeDepths(nodes({
            a: [],
            b: ['a'],
            c: ['a'],
            d: ['a'],
        }))
        expect(ids(columns)).toEqual([['a'], ['b', 'c', 'd']])
    })

    it('places a joining node in the column after all its parents', () => {
        const columns = workflowNodeDepths(nodes({
            a: [],
            b: ['a'],
            c: ['a'],
            join: ['b', 'c'],
        }))
        expect(ids(columns)).toEqual([['a'], ['b', 'c'], ['join']])
    })

    it('handles a diamond', () => {
        const columns = workflowNodeDepths(nodes({
            start: [],
            left: ['start'],
            right: ['start'],
            end: ['left', 'right'],
        }))
        expect(ids(columns)).toEqual([['start'], ['left', 'right'], ['end']])
    })

    it('places a node after its deepest parent', () => {
        // "late" is at depth 2, so "target", which depends on it, is at depth 3
        const columns = workflowNodeDepths(nodes({
            early: [],
            mid: ['early'],
            late: ['mid'],
            target: ['early', 'late'],
        }))
        expect(ids(columns)).toEqual([['early'], ['mid'], ['late'], ['target']])
    })

    it('puts disconnected roots in the same first column', () => {
        const columns = workflowNodeDepths(nodes({
            a: [],
            b: [],
            aChild: ['a'],
            bChild: ['b'],
        }))
        expect(ids(columns)).toEqual([['a', 'b'], ['aChild', 'bChild']])
    })

    it('preserves the declaration order inside a column', () => {
        const columns = workflowNodeDepths(nodes({
            root: [],
            zebra: ['root'],
            alpha: ['root'],
        }))
        expect(ids(columns)).toEqual([['root'], ['zebra', 'alpha']])
    })

    it('ignores parents which are not part of the node list', () => {
        const columns = workflowNodeDepths(nodes({
            a: ['ghost'],
            b: ['a'],
        }))
        expect(ids(columns)).toEqual([['a'], ['b']])
    })

    it('tolerates a node with no parents property', () => {
        const columns = workflowNodeDepths([{id: 'a'}, {id: 'b', parents: [{id: 'a'}]}])
        expect(ids(columns)).toEqual([['a'], ['b']])
    })

    it('does not hang on a self-referencing node', () => {
        const columns = workflowNodeDepths(nodes({
            a: [],
            loop: ['a', 'loop'],
        }))
        expect(ids(columns)).toEqual([['a'], ['loop']])
    })

    it('does not hang on a cycle', () => {
        const columns = workflowNodeDepths(nodes({
            a: ['c'],
            b: ['a'],
            c: ['b'],
        }))
        // Every node is still placed exactly once, in some column
        expect(columns.flat().map(node => node.id).sort()).toEqual(['a', 'b', 'c'])
    })

    it('attaches the matching node execution to each node', () => {
        const columns = workflowNodeDepths(
            nodes({a: [], b: ['a']}),
            [
                {id: 'a', status: 'SUCCESS'},
                {id: 'b', status: 'ERROR'},
            ],
        )
        expect(columns[0][0].execution).toEqual({id: 'a', status: 'SUCCESS'})
        expect(columns[1][0].execution).toEqual({id: 'b', status: 'ERROR'})
    })

    it('leaves the execution undefined when there is none for a node', () => {
        const columns = workflowNodeDepths(nodes({a: []}), [])
        expect(columns[0][0].execution).toBeUndefined()
    })
})
