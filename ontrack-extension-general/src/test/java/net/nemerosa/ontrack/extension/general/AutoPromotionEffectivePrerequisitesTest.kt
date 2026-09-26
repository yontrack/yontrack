package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AutoPromotionEffectivePrerequisitesTest {

    private val branch = Branch.of(
        Project.of(nd("P", "")).withId(ID.of(1)),
        nd("B", "")
    ).withId(ID.of(1))

    private val quality = ValidationStamp.of(branch, nd("QUALITY", "")).withId(ID.of(1))
    private val security = ValidationStamp.of(branch, nd("SECURITY", "")).withId(ID.of(2))
    private val ciSmoke = ValidationStamp.of(branch, nd("CI-SMOKE", "")).withId(ID.of(3))
    private val ciUnit = ValidationStamp.of(branch, nd("CI-UNIT", "")).withId(ID.of(4))

    private val bronze = PromotionLevel.of(branch, nd("BRONZE", "")).withId(ID.of(1))
    private val silver = PromotionLevel.of(branch, nd("SILVER", "")).withId(ID.of(2))
    private val gold = PromotionLevel.of(branch, nd("GOLD", "")).withId(ID.of(3))

    private val branchValidationStamps = listOf(quality, security, ciSmoke, ciUnit)
    private val branchPromotionLevels = listOf(bronze, silver, gold)

    private fun effective(property: AutoPromotionProperty) =
        AutoPromotionEffectivePrerequisites.of(
            property = property,
            branchValidationStamps = branchValidationStamps,
            branchPromotionLevels = branchPromotionLevels,
        )

    private fun AutoPromotionEffectivePrerequisites.stampNames() = validationStamps.map { it.name }
    private fun AutoPromotionEffectivePrerequisites.levelNames() = promotionLevels.map { it.name }

    @Test
    fun `Direct validation stamps`() {
        val effective = effective(AutoPromotionProperty(listOf(security, quality), "", "", emptyList()))
        assertEquals(listOf("QUALITY", "SECURITY"), effective.stampNames(), "Branch order, not property order")
        assertEquals(emptyList(), effective.levelNames())
    }

    @Test
    fun `Include regular expression`() {
        val effective = effective(AutoPromotionProperty(emptyList(), "CI-.*", "", emptyList()))
        assertEquals(listOf("CI-SMOKE", "CI-UNIT"), effective.stampNames())
    }

    @Test
    fun `Exclude regular expression removes a stamp`() {
        val effective = effective(AutoPromotionProperty(emptyList(), "CI-.*", "CI-SMOKE", emptyList()))
        assertEquals(listOf("CI-UNIT"), effective.stampNames())
    }

    @Test
    fun `Direct and included stamps are merged without duplicates`() {
        val effective = effective(AutoPromotionProperty(listOf(ciUnit, quality), "CI-.*", "", emptyList()))
        assertEquals(listOf("QUALITY", "CI-SMOKE", "CI-UNIT"), effective.stampNames())
    }

    @Test
    fun `A direct validation stamp is required even when excluded by the pattern`() {
        val effective = effective(AutoPromotionProperty(listOf(ciSmoke), "", "CI-.*", emptyList()))
        assertEquals(listOf("CI-SMOKE"), effective.stampNames())
    }

    @Test
    fun `Required promotions in branch order`() {
        val effective = effective(AutoPromotionProperty(emptyList(), "", "", listOf(gold, bronze)))
        assertEquals(emptyList(), effective.stampNames())
        assertEquals(listOf("BRONZE", "GOLD"), effective.levelNames())
    }

    @Test
    fun `An empty property requires nothing`() {
        val effective = effective(AutoPromotionProperty(emptyList(), "", "", emptyList()))
        assertEquals(emptyList(), effective.stampNames())
        assertEquals(emptyList(), effective.levelNames())
    }
}
