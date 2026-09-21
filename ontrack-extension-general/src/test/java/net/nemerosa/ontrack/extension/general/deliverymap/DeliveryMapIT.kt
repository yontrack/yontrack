package net.nemerosa.ontrack.extension.general.deliverymap

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.general.PromotionDependenciesProperty
import net.nemerosa.ontrack.extension.general.PromotionDependenciesPropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End to end assembly of a branch's delivery map, from the configuration to the GraphQL field.
 */
class DeliveryMapIT : AbstractQLKTITSupport() {

    @Test
    fun `A branch with no promotion level at all has an empty map`() {
        asAdmin {
            project {
                branch {
                    val map = deliveryMap(this)
                    assertEquals(emptyList(), map.checkpoints.map { it.getRequiredTextField("id") })
                    assertEquals(emptyList(), map.edges.map { it.getRequiredTextField("id") })
                }
            }
        }
    }

    @Test
    fun `Promotion levels are on the map even when nothing is configured to grant them`() {
        asAdmin {
            project {
                branch {
                    val bronze = promotionLevel("BRONZE")
                    val silver = promotionLevel("SILVER")
                    val map = deliveryMap(this)
                    assertEquals(
                        listOf("promotion-level:${bronze.id}", "promotion-level:${silver.id}"),
                        map.checkpoints.map { it.getRequiredTextField("id") },
                    )
                    // No configuration, so nothing joins them: this is the map's empty state
                    assertEquals(emptyList(), map.edges.map { it.getRequiredTextField("id") })
                }
            }
        }
    }

    @Test
    fun `A validation stamp no promotion depends on is not on the map`() {
        asAdmin {
            project {
                branch {
                    validationStamp("LONELY")
                    val map = deliveryMap(this)
                    assertTrue(map.checkpoints.none { it.getRequiredTextField("name") == "LONELY" })
                }
            }
        }
    }

    @Test
    fun `Auto promotion draws an unlocks edge from each named validation stamp`() {
        asAdmin {
            project {
                branch {
                    val quality = validationStamp("QUALITY")
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(quality), "", "", emptyList()),
                    )
                    val map = deliveryMap(this)
                    assertTrue(map.checkpoints.any { it.getRequiredTextField("id") == "validation-stamp:${quality.id}" })
                    val edge = map.edges.single()
                    assertEquals("UNLOCKS", edge.getRequiredTextField("kind"))
                    assertEquals("validation-stamp:${quality.id}", edge.getRequiredTextField("source"))
                    assertEquals("promotion-level:${silver.id}", edge.getRequiredTextField("target"))
                }
            }
        }
    }

    @Test
    fun `An auto promotion pattern draws one aggregate checkpoint standing for the matched stamps`() {
        asAdmin {
            project {
                branch {
                    validationStamp("CI-BUILD")
                    validationStamp("CI-TEST")
                    validationStamp("MANUAL")
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "CI-.*", "", emptyList()),
                    )
                    val map = deliveryMap(this)
                    val aggregate = map.checkpoints.single {
                        it.getRequiredTextField("type") == "validation-stamp-pattern"
                    }
                    assertEquals("CI-.*", aggregate.getRequiredTextField("name"))
                    assertEquals(
                        listOf("CI-BUILD", "CI-TEST"),
                        aggregate.path("members").values().map { it.getRequiredTextField("name") },
                    )
                    // One edge for the whole pattern, not one per stamp
                    assertEquals(1, map.edges.size)
                }
            }
        }
    }

    @Test
    fun `Promotion dependencies draw a requires edge from the prerequisite`() {
        asAdmin {
            project {
                branch {
                    val bronze = promotionLevel("BRONZE")
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        PromotionDependenciesPropertyType::class.java,
                        PromotionDependenciesProperty(listOf("BRONZE")),
                    )
                    val edge = deliveryMap(this).edges.single()
                    assertEquals("REQUIRES", edge.getRequiredTextField("kind"))
                    assertEquals("promotion-level:${bronze.id}", edge.getRequiredTextField("source"))
                    assertEquals("promotion-level:${silver.id}", edge.getRequiredTextField("target"))
                }
            }
        }
    }

    @Test
    fun `A promotion dependency naming no promotion level of the branch draws no edge`() {
        asAdmin {
            project {
                branch {
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        PromotionDependenciesPropertyType::class.java,
                        PromotionDependenciesProperty(listOf("NO-SUCH-PROMOTION")),
                    )
                    assertEquals(emptyList(), deliveryMap(this).edges.map { it.getRequiredTextField("id") })
                }
            }
        }
    }

    @Test
    fun `A promotion level names the latest build promoted to it`() {
        asAdmin {
            project {
                branch {
                    val silver = promotionLevel("SILVER")
                    build("1") { promote(silver) }
                    val latest = build("2") { promote(silver) }
                    build("3")
                    val checkpoint = deliveryMap(this).checkpoints.single()
                    val arrival = checkpoint.path("arrival")
                    assertEquals(latest.name, arrival.path("build").getRequiredTextField("name"))
                    // Arriving *is* the outcome on a promotion level
                    assertTrue(arrival.path("status").isNull)
                }
            }
        }
    }

    @Test
    fun `A promotion level nothing has reached has no arrival`() {
        asAdmin {
            project {
                branch {
                    promotionLevel("SILVER")
                    build("1")
                    assertTrue(deliveryMap(this).checkpoints.single().path("arrival").isNull)
                }
            }
        }
    }

    @Test
    fun `A validation stamp shows a build which arrived and failed`() {
        asAdmin {
            project {
                branch {
                    val quality = validationStamp("QUALITY")
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(quality), "", "", emptyList()),
                    )
                    build("1") { validate(quality, ValidationRunStatusID.STATUS_PASSED) }
                    val latest = build("2") { validate(quality, ValidationRunStatusID.STATUS_FAILED) }
                    val checkpoint = deliveryMap(this).checkpoints.single {
                        it.getRequiredTextField("type") == "validation-stamp"
                    }
                    val arrival = checkpoint.path("arrival")
                    assertEquals(latest.name, arrival.path("build").getRequiredTextField("name"))
                    assertEquals("FAILED", arrival.path("status").getRequiredTextField("id"))
                }
            }
        }
    }

    @Test
    fun `A validation stamp never run has no arrival`() {
        asAdmin {
            project {
                branch {
                    val quality = validationStamp("QUALITY")
                    val silver = promotionLevel("SILVER")
                    setProperty(
                        silver,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(quality), "", "", emptyList()),
                    )
                    build("1")
                    val checkpoint = deliveryMap(this).checkpoints.single {
                        it.getRequiredTextField("type") == "validation-stamp"
                    }
                    assertNull(checkpoint.path("arrival").takeIf { !it.isNull })
                }
            }
        }
    }

    @Test
    fun `The map's head is the branch's latest build`() {
        asAdmin {
            project {
                branch {
                    promotionLevel("SILVER")
                    build("1")
                    val latest = build("2")
                    assertEquals(latest.name, deliveryMap(this).head?.getRequiredTextField("name"))
                }
            }
        }
    }

    @Test
    fun `A branch with no build at all has no head`() {
        asAdmin {
            project {
                branch {
                    promotionLevel("SILVER")
                    assertNull(deliveryMap(this).head)
                }
            }
        }
    }

    @Test
    fun `A checkpoint the latest build has reached is at the head`() {
        asAdmin {
            project {
                branch {
                    val silver = promotionLevel("SILVER")
                    build("1")
                    build("2") { promote(silver) }
                    val arrival = deliveryMap(this).checkpoints.single().path("arrival")
                    assertEquals(0, arrival.path("lag").asInt())
                }
            }
        }
    }

    @Test
    fun `A checkpoint says how many builds behind the head it is`() {
        asAdmin {
            project {
                branch {
                    val silver = promotionLevel("SILVER")
                    build("1") { promote(silver) }
                    build("2")
                    build("3")
                    val arrival = deliveryMap(this).checkpoints.single().path("arrival")
                    assertEquals(2, arrival.path("lag").asInt())
                }
            }
        }
    }

    private data class RenderedMap(
        val checkpoints: List<JsonNode>,
        val edges: List<JsonNode>,
        val head: JsonNode?,
    )

    private fun deliveryMap(branch: Branch): RenderedMap =
        run(
            """
                {
                    branch(id: ${branch.id}) {
                        deliveryMap {
                            checkpoints {
                                id
                                type
                                name
                                description
                                data
                                arrival {
                                    build { name }
                                    time
                                    status { id }
                                    lag
                                }
                                members { id name }
                            }
                            edges { id kind source target }
                            head { name }
                        }
                    }
                }
            """
        ).let { data ->
            val map = data.path("branch").path("deliveryMap")
            RenderedMap(
                checkpoints = map.path("checkpoints").toList(),
                edges = map.path("edges").toList(),
                head = map.path("head").takeIf { !it.isNull },
            )
        }

}
