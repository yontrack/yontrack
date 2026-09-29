import {applyNodeChanges, Background, Controls, ReactFlow, ReactFlowProvider} from "reactflow";
import {useCallback, useEffect, useMemo, useState} from "react";
import BuildNode from "@components/links/BuildNode";
import BuildGroupNode from "@components/links/BuildGroupNode";
import {autoLayout} from "@components/links/GraphUtils";
import {useQuery} from "@components/services/GraphQL";
import {Skeleton} from "antd";
import {buildQuery} from "@components/links/BuildLinksUtils";
import {edgeStyle} from "@components/links/LinksGraphConstants";


function BuildLinksFlow({build}) {

    const [nodes, setNodes] = useState([])
    const [edges, setEdges] = useState([])

    const nodeTypes = useMemo(() => ({
        build: BuildNode,
        group: BuildGroupNode,
    }), []);

    const buildToNode = (build, id) => {
        return {
            id: id ? id : String(build.id),
            position: {x: 0, y: 0},
            data: {build},
            type: 'build',
        }
    }

    /**
     * A _box_ is the aggregation of build upstream dependencies for the same project and qualifier.
     */
    const collectUpstreamBoxes = (build, currentBox, boxes) => {
        build.usedByQualified?.pageItems?.forEach(link => {
            const parent = link.build
            const parentBoxId = parent.branch.project.name

            let parentBox = boxes[parentBoxId]
            if (!parentBox) {
                parentBox = {
                    id: parentBoxId,
                }
                boxes[parentBoxId] = parentBox
            }

            if (parentBox.builds) {
                parentBox.builds.push(parent)
            } else {
                parentBox.builds = [parent]
            }

            if (currentBox) {
                if (!parentBox.children) {
                    parentBox.children = {}
                }
                parentBox.children[currentBox.id] = currentBox
            }

            collectUpstreamBoxes(parent, parentBox, boxes)
        })
    }

    const collectUpstreamNodes = (rootNodeId, build, nodes, edges, nodesCache, edgesCache) => {

        const boxes = {}
        collectUpstreamBoxes(build, {
            id: rootNodeId,
        }, boxes)

        for (const boxId in boxes) {
            const box = boxes[boxId]
            // We take boxes into account only if they contain builds
            if (box.builds.length > 0) {
                let boxNode
                // If only one build, it's a regular build node
                if (box.builds.length === 1) {
                    const boxBuild = box.builds[0]
                    boxNode = buildToNode(boxBuild, boxId)
                    nodes.push(boxNode)
                }
                // If more than 1 build, it's a group
                else {
                    boxNode = {
                        id: boxId,
                        position: {x: 0, y: 0},
                        data: {
                            builds: box.builds,
                        },
                        type: 'group',
                    }
                    nodes.push(boxNode)
                }
                // Linking to the children
                for (const childId in box.children) {
                    let edgeId = `${boxId}-${childId}`
                    if (!edgesCache[edgeId]) {
                        const edge = {
                            id: edgeId,
                            source: String(boxNode.id),
                            target: String(childId),
                            ...edgeStyle,
                        }
                        edges.push(edge)
                        edgesCache[edgeId] = edgeId
                    }
                }
            }
        }
    }

    const collectDownstreamNodes = (build, nodes, edges, nodesCache, edgesCache) => {
        // Adds the current build as a node
        const nodeId = String(build.id)
        if (!nodesCache[nodeId]) {
            const node = buildToNode(build);
            nodes.push(node)
            nodesCache[nodeId] = node
        }
        // Direct dependencies
        build.usingQualified?.pageItems?.forEach(link => {
            const child = link.build
            // Build the child node
            collectDownstreamNodes(child, nodes, edges, nodesCache, edgesCache)
            // Adding an edge
            let edgeId = `${build.id}-${child.id}`
            const cachedEdge = edgesCache[edgeId]
            if (cachedEdge) {
                // Edge already exists, completing the qualifiers
                if (cachedEdge.qualifiers.indexOf(link.qualifier) < 0) {
                    cachedEdge.qualifiers.push(link.qualifier)
                    if (cachedEdge.qualifiers.length === 1 && cachedEdge.qualifiers[0] === '') {
                        cachedEdge.label = ''
                    } else {
                        cachedEdge.label = ''
                        cachedEdge.qualifiers.forEach(qualifier => {
                            if (cachedEdge.label.length > 0) {
                                cachedEdge.label += ' / '
                            }
                            if (qualifier) {
                                cachedEdge.label += qualifier
                            } else {
                                cachedEdge.label += 'default'
                            }
                        })
                    }
                }
            } else {
                // New edge
                const edge = {
                    id: edgeId,
                    source: String(build.id),
                    target: String(child.id),
                    qualifiers: [link.qualifier],
                    label: link.qualifier,
                    ...edgeStyle,
                }
                edges.push(edge)
                edgesCache[edgeId] = edge
            }
        })
        // OK
        return nodeId
    }

    const {data, loading, finished} = useQuery(
        buildQuery,
        {
            variables: {buildId: Number(build.id)},
            deps: [build],
        }
    )

    // The nodes & edges are then owned by React Flow, which changes them (layout, etc.)
    useEffect(() => {
        if (data) {
            // Nodes & edges to build
            const nodes = []
            const edges = []

            // Cache for the nodes & edges
            const nodesCache = {}
            const edgesCache = {}

            // Building all downstream nodes recursively
            const rootNodeId = collectDownstreamNodes(
                data.build,
                nodes,
                edges,
                nodesCache,
                edgesCache,
            )

            // Selecting the root node
            nodesCache[rootNodeId].data.selected = true

            // Building all upstream nodes recursively
            collectUpstreamNodes(
                rootNodeId,
                data.build,
                nodes,
                edges,
                nodesCache,
                edgesCache,
            )

            // Layout for the graph

            autoLayout({
                nodes,
                edges,
                nodeWidth: 300,
                nodeHeight: (node) => node.type === 'group' ? 400 : 60,
                setNodes,
                setEdges,
            })
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data])

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [],
    );

    return (
        <>
            <div style={{height: '800px'}}>
                <Skeleton active loading={loading || !finished}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
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

export default function BuildLinksGraph({build}) {
    return (
        <>
            <ReactFlowProvider>
                <BuildLinksFlow build={build}/>
            </ReactFlowProvider>
        </>
    )
}