package net.nemerosa.ontrack.extension.workflows.definition

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser

object WorkflowFixtures {

    val simpleLinearWorkflowYaml = """
        name: Simple Linear
        nodes:
          - id: start
            executorId: mock
            data:
              text: Start node
          - id: end
            parents:
              - id: start
            executorId: mock
            data:
              text: End node
    """.trimIndent()

    @Deprecated("Use YAML instead")
    fun simpleLinearWorkflow(
        name: String = "Simple linear"
    ) =
        Workflow(
            name = name,
            nodes = listOf(
                WorkflowNode(
                    id = "start",
                    executorId = "mock",
                    data = StringNode("Start node"),
                    parents = emptyList(),
                ),
                WorkflowNode(
                    id = "end",
                    executorId = "mock",
                    data = StringNode("End node"),
                    parents = listOf(
                        WorkflowParentNode(
                            id = "start"
                        )
                    ),
                ),
            )
        )

    @Deprecated("Use YAML instead")
    fun cyclicWorkflow() =
        Workflow(
            name = "Simple cyclic",
            nodes = listOf(
                WorkflowNode(
                    id = "start",
                    executorId = "mock",
                    data = StringNode("Start node"),
                    parents = listOf(
                        WorkflowParentNode(
                            id = "end",
                        )
                    ),
                ),
                WorkflowNode(
                    id = "end",
                    executorId = "mock",
                    data = StringNode("End node"),
                    parents = listOf(
                        WorkflowParentNode(
                            id = "start"
                        )
                    ),
                ),
            )
        )

    fun twoParallelAndJoin() =
        WorkflowParser.parseYamlWorkflow(
            """
                name: Parallel with Join
                nodes:
                  - id: start-a
                    executorId: mock
                    data:
                        text: Start node A
                  - id: start-b
                    executorId: mock
                    data:
                        text: Start node B
                  - id: end
                    parents:
                      - id: start-a
                      - id: start-b
                    executorId: mock
                    data:
                        text: End node
            """.trimIndent()
        )

}