package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ValidationStampMutationsIT : AbstractQLKTITSupport() {

    @Test
    fun `Moving validation stamps preserves insertion order`() {
        asAdmin {
            project {
                branch {
                    validationStamp("ALPHA")
                    validationStamp("BETA")
                    validationStamp("GAMMA")
                    validationStamp("DELTA")

                    fun move(oldName: String, newName: String) {
                        run(
                            """
                            mutation {
                                reorderValidationStampById(input: {
                                    branchId: $id,
                                    oldName: "$oldName",
                                    newName: "$newName",
                                }) {
                                    errors {
                                        message
                                    }
                                }
                            }
                        """
                        ) { data ->
                            checkGraphQLUserErrors(data, "reorderValidationStampById")
                        }
                    }

                    // Drag DELTA (index 3) to index 0: [DELTA, ALPHA, BETA, GAMMA]
                    move("DELTA", "ALPHA")
                    assertEquals(
                        listOf(
                            "DELTA",
                            "ALPHA",
                            "BETA",
                            "GAMMA",
                        ),
                        structureService.getValidationStampListForBranch(id).map { it.name }
                    )

                    // Drag ALPHA (index 1) to index 2: [DELTA, BETA, ALPHA, GAMMA]
                    move("ALPHA", "BETA")
                    assertEquals(
                        listOf(
                            "DELTA",
                            "BETA",
                            "ALPHA",
                            "GAMMA",
                        ),
                        structureService.getValidationStampListForBranch(id).map { it.name }
                    )

                }
            }
        }
    }

}
