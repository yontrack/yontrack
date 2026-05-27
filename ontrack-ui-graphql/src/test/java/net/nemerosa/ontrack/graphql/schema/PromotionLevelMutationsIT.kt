package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.PredefinedPromotionLevel
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PromotionLevelMutationsIT : AbstractQLKTITSupport() {

    @Test
    fun `Updating a promotion level must keep the predefined promotion level description`() {
        asAdmin {
            val plName = TestUtils.uid("pl-")
            val ppl = predefinedPromotionLevelService.newPredefinedPromotionLevel(
                PredefinedPromotionLevel.of(
                    NameDescription.nd(plName, "Description at predefined level")
                )
            )

            project {
                branch {
                    // Creating a promotion level
                    run(
                        """
                        mutation {
                            setupPromotionLevel(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "$plName",
                                description: ""
                            }) {
                                promotionLevel {
                                    id
                                }
                            }
                        }
                    """
                    ) { data ->
                        val plId = ID.of(
                            data.path("setupPromotionLevel")
                                .path("promotionLevel")
                                .path("id")
                                .asInt()
                        )
                        // Predefined description must have been set
                        val pl1 = structureService.getPromotionLevel(plId)
                        assertEquals(ppl.description, pl1.description, "Initial description being set")
                        // Updating the promotion level without a description
                        run(
                            """
                        mutation {
                            setupPromotionLevel(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "$plName",
                                description: ""
                            }) {
                                promotionLevel {
                                    id
                                }
                            }
                        }
                    """
                        ) {
                            // Predefined description must have been kept
                            val pl2 = structureService.getPromotionLevel(plId)
                            assertEquals(ppl.description, pl2.description, "Predefined description must have been kept")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Moving promotion levels preserves insertion order`() {
        asAdmin {
            project {
                branch {
                    promotionLevel("PLATINUM")
                    promotionLevel("GOLD")
                    promotionLevel("SILVER")
                    promotionLevel("BRONZE")

                    fun move(oldName: String, newName: String) {
                        run(
                            """
                            mutation {
                                reorderPromotionLevelById(input: {
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
                            checkGraphQLUserErrors(data, "reorderPromotionLevelById")
                        }
                    }

                    // Drag BRONZE (index 3) to index 0: [BRONZE, PLATINUM, GOLD, SILVER]
                    move("BRONZE", "PLATINUM")
                    assertEquals(
                        listOf(
                            "BRONZE",
                            "PLATINUM",
                            "GOLD",
                            "SILVER",
                        ),
                        structureService.getPromotionLevelListForBranch(id).map { it.name }
                    )

                    // Drag PLATINUM (index 1) to index 2: [BRONZE, GOLD, PLATINUM, SILVER]
                    move("PLATINUM", "GOLD")
                    assertEquals(
                        listOf(
                            "BRONZE",
                            "GOLD",
                            "PLATINUM",
                            "SILVER",
                        ),
                        structureService.getPromotionLevelListForBranch(id).map { it.name }
                    )

                }
            }
        }
    }

}