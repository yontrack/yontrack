package net.nemerosa.ontrack.extension.workflows.definition

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

    fun cyclicWorkflow() =
        WorkflowParser.parseYamlWorkflow(
            """
                name: Simple cyclic
                nodes:
                  - id: start
                    parents:
                      - id: end
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