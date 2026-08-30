/**
 * Arranges the nodes of a workflow into depth columns.
 *
 * A workflow is a DAG but the node strip is a line, so the columns are *derived* from the graph:
 *
 *     depth(n) = 0 if n has no parent, else 1 + max(depth(p) for p in n.parents)
 *
 * One column per depth, same-depth nodes stacked in that column. Concurrency, fan-out and joins
 * all fall out of this. A node is therefore always drawn after every one of its parents — but a
 * column boundary is not an edge: which parent fed which child is not readable from the strip.
 *
 * @param nodes the `workflow.nodes` list — objects with an `id` and an optional `parents: [{id}]`
 * @param nodesExecutions the `nodesExecutions` list of the instance, matched to nodes by `id`
 * @return an array of columns, each an array of `{...node, execution}` in declaration order
 */
export default function workflowNodeDepths(nodes, nodesExecutions = []) {
    if (!nodes || nodes.length === 0) return []

    const byId = new Map(nodes.map(node => [node.id, node]))
    const executionById = new Map((nodesExecutions ?? []).map(execution => [execution.id, execution]))

    // Depth of each node, computed lazily with memoisation.
    // `visiting` breaks cycles: a node reached again while its own depth is being computed
    // contributes no depth, so a cyclic or self-referencing graph terminates instead of hanging.
    // The backend validates workflows, so this only guards against corrupt data.
    const depths = new Map()
    const visiting = new Set()

    const depthOf = (node) => {
        if (depths.has(node.id)) return depths.get(node.id)
        if (visiting.has(node.id)) return 0
        visiting.add(node.id)
        let depth = 0
        for (const parent of node.parents ?? []) {
            const parentNode = byId.get(parent.id)
            // Parents which are not part of the node list are ignored
            if (parentNode) {
                depth = Math.max(depth, depthOf(parentNode) + 1)
            }
        }
        visiting.delete(node.id)
        depths.set(node.id, depth)
        return depth
    }

    const columns = []
    nodes.forEach(node => {
        const depth = depthOf(node)
        while (columns.length <= depth) columns.push([])
        columns[depth].push({
            ...node,
            execution: executionById.get(node.id),
        })
    })

    return columns
}
