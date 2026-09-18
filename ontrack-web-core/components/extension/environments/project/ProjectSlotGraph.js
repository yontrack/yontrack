import {useCallback, useEffect, useState} from "react"
import {applyNodeChanges, Background, ControlButton, Controls, ReactFlow} from "reactflow"
import {FaProjectDiagram} from "react-icons/fa"
import {Empty, Spin} from "antd"
import {useQuery} from "@components/services/GraphQL"
import {autoLayout} from "@components/links/GraphUtils"
import SlotGraphNode, {SlotGraphNodeContext} from "@components/extension/environments/project/SlotGraphNode"
import {
    slotGraphEdges,
    slotGraphNodes,
} from "@components/extension/environments/project/projectEnvironmentsModel"
import {gqlProjectSlotGraph} from "@components/extension/environments/project/projectEnvironmentsGraphQL"

/*
 * React Flow compares `nodeTypes` by identity and remounts every node when it changes, so the map
 * is a module constant rather than something rebuilt on each render.
 */
const nodeTypes = {
    slotNode: SlotGraphNode,
}

/** How tall the graph is. Enough for a four-environment pipeline without scrolling the page. */
const GRAPH_HEIGHT = '600px'

/**
 * A project's slots as a left-to-right graph, each node a slot cell.
 *
 * **Why the graph survived the redesign** while the builds panel and the actions panel did not: it
 * shows something a matrix cannot, the dependency between two slots - an `environment` admission
 * rule saying production only admits what staging already holds. The matrix arranges slots by
 * environment order, which *looks* like the same thing and is not: two environments of equal order,
 * or an environment a project skips, read identically in a matrix and differently here.
 *
 * Everything else about a slot is the cell's business, and the node draws one rather than a second
 * rendering of the same facts - that is what makes the graph and the matrix agree by construction.
 *
 * @param {Object} project The project, with `id`
 * @param {string} qualifier Which qualifier's graph to draw
 * @param {function} onSlotClick Called with a slot when a node is activated - the screen opens the
 *   drawer on it
 * @param {number} refreshCount Bumped by the screen's freshness, to ask the server again
 */
export default function ProjectSlotGraph({project, qualifier, onSlotClick, refreshCount = 0}) {

    const {data: slotGraph, loading, finished} = useQuery(gqlProjectSlotGraph, {
        variables: {id: Number(project?.id), qualifier},
        condition: !!project,
        deps: [project?.id, qualifier, refreshCount],
        initialData: null,
        dataFn: data => data.project?.slotGraph,
    })

    const [nodes, setNodes] = useState([])
    const [edges, setEdges] = useState([])

    /*
     * ELK is asynchronous, so the laid-out nodes land in state rather than being computed in the
     * render body. What goes into it is pure - see `slotGraphNodes` - and what a click does is not
     * in there at all: it is read from context by the node, so it cannot be captured stale by this
     * effect. See `SlotGraphNodeContext`.
     */
    const layout = (nodes, edges) => {
        autoLayout({
            nodes,
            edges,
            nodeWidth: 260,
            nodeHeight: 110,
            setNodes,
            setEdges,
        })
    }

    useEffect(() => {
        layout(slotGraphNodes(slotGraph), slotGraphEdges(slotGraph))
    }, [slotGraph])

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [],
    )

    /*
     * Read from the *answer* and not from `nodes`, which stay empty for a tick longer while ELK
     * lays them out - long enough to flash "no slot" over a project which has several.
     */
    const empty = finished && !!slotGraph && (slotGraph.slotNodes ?? []).length === 0

    return (
        <SlotGraphNodeContext.Provider value={{onSlotClick}}>
            <div
                style={{height: GRAPH_HEIGHT, width: '100%'}}
                data-testid="project-slot-graph"
            >
                {
                    /*
                     * A project with no slot for this qualifier is not an error and not a loading
                     * state: it is a project nobody has given a slot yet, and an empty React Flow
                     * canvas says nothing at all about which of the three it is.
                     */
                    empty ?
                        <Empty
                            data-testid="project-slot-graph-empty"
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="This project has no deployment slot for this qualifier."
                        /> :
                        <Spin spinning={loading && nodes.length === 0}>
                            <div style={{height: GRAPH_HEIGHT, width: '100%'}}>
                                <ReactFlow
                                    nodes={nodes}
                                    edges={edges}
                                    fitView={true}
                                    /*
                                     * Fit, but never magnify: a project with two slots would
                                     * otherwise open at nearly 200%, drawing a cell at twice the
                                     * size of the very same cell in the Matrix view beside it.
                                     */
                                    fitViewOptions={{maxZoom: 1}}
                                    nodeTypes={nodeTypes}
                                    onNodesChange={onNodesChange}
                                >
                                    <Background/>
                                    <Controls>
                                        <ControlButton
                                            title="Adjust the layout"
                                            onClick={() => layout(nodes, edges)}
                                        >
                                            <FaProjectDiagram/>
                                        </ControlButton>
                                    </Controls>
                                </ReactFlow>
                            </div>
                        </Spin>
                }
            </div>
        </SlotGraphNodeContext.Provider>
    )
}
