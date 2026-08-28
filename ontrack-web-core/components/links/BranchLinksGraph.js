import {
    applyNodeChanges,
    Background,
    Controls,
    ReactFlow,
    ReactFlowProvider,
    useNodesInitialized,
    useReactFlow
} from "reactflow";
import {useCallback, useContext, useEffect, useMemo, useState} from "react";
import BranchNode from "@components/links/BranchNode";
import BranchLinkNode from "@components/links/BranchLinkNode";
import {autoLayout} from "@components/links/GraphUtils";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {Skeleton} from "antd";
import {AutoRefreshContext} from "@components/common/AutoRefresh";
import {edgeStyle} from "@components/links/LinksGraphConstants";
import {branchQuery} from "@components/links/BranchDependenciesFragments";

function BranchLinksFlow({branch, loadPullRequests, loadPullRequestsCount}) {

    const client = useGraphQLClient()

    const [nodes, setNodes] = useState([])
    const [edges, setEdges] = useState([])

    const [selectedNodeId, setSelectedNodeId] = useState(null)
    const [focusNodeId, setFocusNodeId] = useState(null)

    const onToggleFocus = useCallback((node) => {
        setFocusNodeId(current => {
            if (current === node.id) {
                return null
            } else {
                return node.id
            }
        })
    }, [])

    const nodesInitialized = useNodesInitialized();
    const {fitView} = useReactFlow();

    const [layoutDone, setLayoutDone] = useState(false)

    useEffect(() => {
        if (nodesInitialized && nodes.length > 0 && !layoutDone) {
            // A small delay is sometimes needed to ensure that the nodes are fully rendered
            // and their dimensions are correctly reported by React Flow.
            const timeout = window.setTimeout(() => {
                autoLayout({
                    nodes,
                    edges,
                    setNodes,
                    setEdges,
                }).then(() => {
                    setLayoutDone(true)
                    window.setTimeout(() => {
                        fitView()
                    }, 100)
                })
            }, 100)
            return () => window.clearTimeout(timeout)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [nodesInitialized, layoutDone, nodes, edges]);

    const nodeTypes = useMemo(() => ({
        branch: BranchNode,
        branchLink: BranchLinkNode,
    }), []);

    const branchToNode = (branch, id, visible = true) => {
        return {
            id: id ? id : String(branch.id),
            position: {x: 0, y: 0},
            data: {
                branch,
                visible,
                onToggleFocus,
            },
            type: 'branch',
        }
    }

    const branchLinkToNode = (id, sourceBranch, targetBranch, link, visible = true) => {
        return {
            id: id,
            position: {x: 0, y: 0},
            data: {
                sourceBranch,
                targetBranch,
                link,
                visible,
                onToggleFocus,
            },
            type: 'branchLink',
        }
    }

    const collectDownstreamNodes = (startNode, nodes, edges, nodesCache, edgesCache) => {
        const {branch} = startNode.data

        branch.downstreamLinks?.forEach(link => {
            const qualifier = link.qualifier
            const childBranch = link.branch

            // Gets or creates the child node
            const childId = String(childBranch.id)
            let childNode = nodesCache[childId]
            if (!childNode) {
                childNode = branchToNode(childBranch, childId)
                nodesCache[childId] = childNode
                nodes.push(childNode)
            }

            // Node for the dependency information
            const dependencyId = qualifier ?
                `${startNode.id}-${childNode.id}-${qualifier}` :
                `${startNode.id}-${childNode.id}`
            let dependencyNode = nodesCache[dependencyId]
            if (!dependencyNode) {
                dependencyNode = branchLinkToNode(dependencyId, startNode.data.branch, childNode.data.branch, link)
                nodesCache[dependencyId] = dependencyNode
                nodes.push(dependencyNode)
            }

            // Edge from the start node to the link node
            const edgeStartId = `${startNode.id}-${dependencyNode.id}`
            if (!edgesCache[edgeStartId]) {
                const edge = {
                    id: edgeStartId,
                    source: startNode.id,
                    target: dependencyNode.id,
                    type: 'straight',
                    ...edgeStyle,
                }
                edges.push(edge)
                edgesCache[edgeStartId] = edge
            }

            // Edge from the link node to the child node
            const edgeChildId = `${dependencyNode.id}-${childNode.id}`
            if (!edgesCache[edgeChildId]) {
                const edge = {
                    id: edgeChildId,
                    source: dependencyNode.id,
                    target: childNode.id,
                    type: 'straight',
                    ...edgeStyle,
                }
                edges.push(edge)
                edgesCache[edgeChildId] = edge
            }

            // Iteration
            collectDownstreamNodes(childNode, nodes, edges, nodesCache, edgesCache)
        })
    }

    const collectUpstreamNodes = (startNode, nodes, edges, nodesCache, edgesCache) => {
        const {branch} = startNode.data

        branch.upstreamLinks?.forEach(link => {
            const qualifier = link.qualifier
            const parentBranch = link.branch

            // Gets or creates the parent node
            const parentId = String(parentBranch.id)
            let parentNode = nodesCache[parentId]
            if (!parentNode) {
                parentNode = branchToNode(parentBranch, parentId)
                nodesCache[parentId] = parentNode
                nodes.push(parentNode)
            }

            // Node for the dependency information
            const dependencyId = qualifier ?
                `${parentNode.id}-${startNode.id}-${qualifier}` :
                `${parentNode.id}-${startNode.id}`
            let dependencyNode = nodesCache[dependencyId]
            if (!dependencyNode) {
                dependencyNode = branchLinkToNode(dependencyId, parentNode.data.branch, startNode.data.branch, link)
                nodesCache[dependencyId] = dependencyNode
                nodes.push(dependencyNode)
            }

            // Edge from the parent node to the link node
            const edgeParentId = `${parentNode.id}-${dependencyNode.id}`
            if (!edgesCache[edgeParentId]) {
                const edge = {
                    id: edgeParentId,
                    source: parentNode.id,
                    target: dependencyNode.id,
                    type: 'straight',
                    ...edgeStyle,
                }
                edges.push(edge)
                edgesCache[edgeParentId] = edge
            }

            // Edge from the link node to the start node
            const edgeStartId = `${dependencyNode.id}-${startNode.id}`
            if (!edgesCache[edgeStartId]) {
                const edge = {
                    id: edgeStartId,
                    source: dependencyNode.id,
                    target: startNode.id,
                    type: 'straight',
                    ...edgeStyle,
                }
                edges.push(edge)
                edgesCache[edgeStartId] = edge
            }

            // Iteration
            collectUpstreamNodes(parentNode, nodes, edges, nodesCache, edgesCache)
        })
    }

    const {autoRefreshCount} = useContext(AutoRefreshContext)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        if (client && branch && branch.id) {
            setLoading(true)
            client.request(
                branchQuery({downstream: true, upstream: true}),
                {
                    branchId: Number(branch.id),
                    loadPullRequests,
                }
            ).then(data => {

                setLayoutDone(false)

                // Root branch
                const rootBranch = data.branch

                // Nodes & edges to build
                const nodes = []
                const edges = []

                // Cache for the nodes & edges
                const nodesCache = {}
                const edgesCache = {}

                // Root node
                const rootNode = branchToNode(rootBranch)
                rootNode.data.selected = true
                nodes.push(rootNode)
                nodesCache[rootNode.id] = rootNode

                collectDownstreamNodes(rootNode, nodes, edges, nodesCache, edgesCache)
                collectUpstreamNodes(rootNode, nodes, edges, nodesCache, edgesCache)

                setNodes(nodes)
                setEdges(edges)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, branch, autoRefreshCount, loadPullRequestsCount]);

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [],
    );

    const onNodeClick = useCallback((event, node) => {
        setSelectedNodeId(node.id)
    }, [])

    useEffect(() => {
        const selectedEdgeIds = edges
            .filter(edge => edge.source === selectedNodeId || edge.target === selectedNodeId)
            .map(edge => edge.id)

        const connectedNodeIds = edges
            .filter(edge => edge.source === selectedNodeId || edge.target === selectedNodeId)
            .flatMap(edge => [edge.source, edge.target])

        const focusedNodeIds = edges
            .filter(edge => edge.source === focusNodeId || edge.target === focusNodeId)
            .flatMap(edge => [edge.source, edge.target])

        const secondHopNodeIds = edges
            .filter(edge => focusedNodeIds.includes(edge.source) || focusedNodeIds.includes(edge.target))
            .flatMap(edge => [edge.source, edge.target])

        const allFocusedNodeIds = [...new Set([...focusedNodeIds, ...secondHopNodeIds])]

        const allFocusedEdgeIds = edges
            .filter(edge => allFocusedNodeIds.includes(edge.source) && allFocusedNodeIds.includes(edge.target))
            .map(edge => edge.id)

        setNodes((nds) => nds.map((node) => {
            const selected = node.id === selectedNodeId || connectedNodeIds.includes(node.id)
            const visible = !focusNodeId || (node.id === focusNodeId || allFocusedNodeIds.includes(node.id))
            return {
                ...node,
                data: {
                    ...node.data,
                    selected,
                    visible,
                    focused: node.id === focusNodeId,
                }
            }
        }))
        setEdges((eds) => eds.map((edge) => {
            const selected = selectedEdgeIds.includes(edge.id)
            const focused = allFocusedEdgeIds.includes(edge.id)
            const visible = !focusNodeId || focused
            return {
                ...edge,
                hidden: !visible,
                style: {
                    ...edge.style,
                    stroke: selected ? 'var(--ot-graph-edge-selected)' : 'var(--ot-graph-edge)',
                    strokeWidth: selected ? 4 : 2,
                }
            }
        }))
    }, [selectedNodeId, focusNodeId])

    useEffect(() => {
        if (layoutDone) {
            window.setTimeout(() => {
                fitView()
            }, 100)
        }
    }, [focusNodeId, layoutDone, fitView]);

    return (
        <>
            <div style={{height: '800px'}}>
                <Skeleton active loading={loading || !branch}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onNodeClick={onNodeClick}
                        fitView={true}
                        nodeTypes={nodeTypes}
                    >
                        <Background/>
                        <Controls/>
                    </ReactFlow>
                </Skeleton>
            </div>
        </>
    )
}

export default function BranchLinksGraph({branch, loadPullRequests, loadPullRequestsCount}) {
    return (
        <>
            <ReactFlowProvider>
                <BranchLinksFlow branch={branch} loadPullRequests={loadPullRequests} loadPullRequestsCount={loadPullRequestsCount}/>
            </ReactFlowProvider>
        </>
    )
}