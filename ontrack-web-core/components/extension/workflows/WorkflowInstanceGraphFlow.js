import WorkflowInstanceGraphNode from "@components/extension/workflows/WorkflowInstanceGraphNode";
import {useCallback, useEffect, useRef, useState} from "react";
import {autoLayout} from "@components/links/GraphUtils";
import {applyNodeChanges, Background, ControlButton, Controls, ReactFlow, useReactFlow} from "reactflow";
import {Skeleton} from "antd";
import {FaEye, FaEyeSlash} from "react-icons/fa";
import {useLocalWorkflowShowDetails} from "@components/storage/local";

const nodeTypes = {
    workflowNode: WorkflowInstanceGraphNode,
}

// Heights of a node, before React Flow has measured it
const nodeHeight = (showDetails) => showDetails ? 220 : 120

export function WorkflowInstanceGraphFlow({instance, instanceNodeExecutions, onNodeSelected}) {

    const {showDetails, toggleShowDetails} = useLocalWorkflowShowDetails()

    const {fitView} = useReactFlow()

    // The nodes change height with their details: they are laid out again, without the
    // height which was measured with the previous details.
    useEffect(() => {
        if (nodes.length > 0) {
            autoLayout({
                nodes: nodes.map(({width, height, ...node}) => ({
                    ...node,
                    data: {
                        ...node.data,
                        showDetails,
                    },
                })),
                edges,
                nodeWidth: 180,
                nodeHeight: nodeHeight(showDetails),
                setNodes,
                setEdges,
            }).then(() => {
                window.setTimeout(() => fitView(), 100)
            })
        }
    }, [showDetails])

    // Refitting the graph when its width changes. Deferred, for React Flow to know its new size first.
    const containerRef = useRef()
    useEffect(() => {
        const observer = new ResizeObserver(() => {
            window.requestAnimationFrame(() => fitView())
        })
        observer.observe(containerRef.current)
        return () => observer.disconnect()
    }, [fitView])

    const workflowNode = (nodeExecution, workflowNodes) => {
        return {
            id: nodeExecution.id,
            position: {x: 0, y: 0},
            data: {
                nodeExecution,
                workflowNode: workflowNodes[nodeExecution.id],
                showDetails,
            },
            type: 'workflowNode',
        }
    }

    const [loading, setLoading] = useState(true)

    const [nodes, setNodes] = useState([])
    const [edges, setEdges] = useState([])

    useEffect(() => {
        if (instance.id) {
            setLoading(true)

            // Indexing all workflow nodes
            const workflowNodes = {}
            instance.workflow.nodes.forEach(node => {
                workflowNodes[node.id] = node
            })

            // Each instance node ==> node
            const nodes = []
            instance.nodesExecutions.forEach(nodeExecution => {
                nodes.push(workflowNode(nodeExecution, workflowNodes))
            })

            // Edges
            const edges = []
            instance.workflow.nodes.forEach(node => {
                node.parents.forEach(({id}) => {
                    edges.push({
                        id: `${id}-${node.id}`,
                        source: id,
                        target: node.id,
                        type: 'smoothstep',
                    })
                })
            })

            // Layout for the graph

            autoLayout({
                nodes,
                edges,
                nodeWidth: 180, // Can be a function
                nodeHeight: nodeHeight(showDetails), // Can be a function
                setNodes,
                setEdges,
            })

            setLoading(false)
        }
    }, [instance])

    useEffect(() => {
        if (nodes && !loading && instanceNodeExecutions) {
            setNodes(nodes => nodes.map(node => {
                const nodeExecution = instanceNodeExecutions.find(it => it.id === node.id)
                if (nodeExecution) {
                    return {
                        ...node,
                        data: {
                            ...node.data,
                            nodeExecution,
                        }
                    }
                } else {
                    return node
                }
            }))
        }
    }, [instanceNodeExecutions])

    const onNodeClick = useCallback((event, node) => {
        if (node && onNodeSelected) {
            setNodes(nodes => nodes.map(it => ({
                ...it,
                data: {
                    ...it.data,
                    selected: it.data.workflowNode.id === node.data.workflowNode.id,
                },
            })))
            onNodeSelected(node.data)
        }
    }, [onNodeSelected])

    const onNodesChange = useCallback(
        (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
        [],
    );

    return (
        <>
            <div ref={containerRef} style={{height: '600px'}}>
                <Skeleton active loading={loading}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        onNodesChange={onNodesChange}
                        onNodeClick={onNodeClick}
                        fitView={true}
                        nodeTypes={nodeTypes}
                    >
                        <Background/>
                        <Controls>
                            <ControlButton
                                title={
                                    showDetails ? "Show less information" : "Show more information"
                                }
                                onClick={toggleShowDetails}
                            >
                                {
                                    showDetails && <FaEyeSlash/>
                                }
                                {
                                    !showDetails && <FaEye/>
                                }
                            </ControlButton>
                        </Controls>
                    </ReactFlow>
                </Skeleton>
            </div>
        </>
    )
}