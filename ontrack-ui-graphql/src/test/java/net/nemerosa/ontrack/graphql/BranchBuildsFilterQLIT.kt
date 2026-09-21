package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

@AsAdminTest
class BranchBuildsFilterQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Default filter with validation stamp`() {
        project {
            branch {
                validationStamp("NONE")
                val vs = validationStamp("VS")
                build {
                    validate(vs)

                    run("""
                        {
                            branches (id: ${branch.id}) {
                                builds(filter: {withValidationStamp: "VS"}) {
                                    name
                                }
                            }
                        }
                    """) { data ->
                        assertEquals(
                                listOf(name),
                                data.path("branches").first().path("builds").values().map { it.path("name").asText() }
                        )
                    }

                    run("""
                        {
                            branches (id: ${branch.id}) {
                                builds(filter: {withValidationStamp: "NONE"}) {
                                    name
                                }
                            }
                        }
                    """) { data ->
                        assertEquals(
                                emptyList(),
                                data.path("branches").first().path("builds").values().map { it.path("name").asText() }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Default filter with validation stamp and returning validation runs`() {
        project {
            branch {
                val vs = validationStamp("VS")
                build {
                    validate(vs)
                    run("""{
                        branches (id: ${branch.id}) {
                            builds(filter: {withValidationStamp: "VS"}) {
                                name
                                validationRuns {
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                    }
                                }
                            }
                        }
                    }""") { data ->
                        assertEquals(
                                listOf(name),
                                data.path("branches").first()
                                        .path("builds")
                                        .values().map { it.path("name").asText() }
                        )
                        assertEquals(
                                "PASSED",
                                data.path("branches").first()
                                        .path("builds").first()
                                        .path("validationRuns").first()
                                        .path("validationRunStatuses").first()
                                        .path("statusID").path("id").asText()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Default filter with validation stamp status`() {
        project {
            branch {
                val vs = validationStamp()
                val passed = build {
                    validate(vs)
                }
                build {
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                }
                run("""{
                    branches (id: $id) {
                        builds(filter: {withValidationStamp: "${vs.name}", withValidationStampStatus: "PASSED"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf(passed.name),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since validation stamp`() {
        project {
            branch {
                val vs = validationStamp("VS")
                build("1") {
                    validate(vs)
                }
                build("2") {
                    validate(vs)
                }
                build("3")

                run("""{
                    branches (id: $id) {
                        builds(filter: {sinceValidationStamp: "VS"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("3", "2"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since validation stamp status`() {
        project {
            branch {
                val vs = validationStamp("VS")
                build("1") {
                    validate(vs, ValidationRunStatusID.STATUS_PASSED)
                }
                build("2") {
                    validate(vs, ValidationRunStatusID.STATUS_PASSED)
                }
                build("3") {
                    validate(vs, ValidationRunStatusID.STATUS_FAILED)
                }
                build("4")

                run("""{
                    branches (id: $id) {
                        builds(filter: {sinceValidationStamp: "VS", sinceValidationStampStatus: "PASSED"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("4", "3", "2"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter with promotion level`() {
        project {
            branch {
                val copper = promotionLevel("COPPER")
                build("1") {
                    promote(copper)
                }
                build("2")
                build("3") {
                    promote(copper)
                }

                run("""{
                    branches (id: $id) {
                        builds(filter: {withPromotionLevel: "COPPER"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("3", "1"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since promotion level`() {
        project {
            branch {
                val copper = promotionLevel("COPPER")
                build("1") {
                    promote(copper)
                }
                build("2")
                build("3") {
                    promote(copper)
                }
                build("4")

                run("""{
                    branches (id: $id) {
                        builds(filter: {sincePromotionLevel: "COPPER"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("4", "3"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since and with promotion level`() {
        project {
            branch {
                val copper = promotionLevel("COPPER")
                val bronze = promotionLevel("BRONZE")

                build("1")
                build("2") {
                    promote(copper)
                    promote(bronze)
                }
                build("3")
                build("4") {
                    promote(copper)
                }
                build("5")

                run("""{
                    branches (id: $id) {
                        builds(filter: {sincePromotionLevel: "BRONZE", withPromotionLevel: "COPPER"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("4", "2"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter dates`() {
        project {
            branch {
                build("1") {
                    updateBuildSignature(time = LocalDateTime.of(2016, 11, 30, 17, 0))
                }
                build("2") {
                    updateBuildSignature(time = LocalDateTime.of(2016, 12, 2, 17, 10))
                }
                build("3") {
                    updateBuildSignature(time = LocalDateTime.of(2016, 12, 4, 17, 20))
                }

                run("""{
                    branches (id: $id) {
                        builds(filter: {afterDate: "2016-12-01", beforeDate: "2016-12-03"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("2"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter with property`() {
        project {
            branch {
                build("1") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("1")
                    )
                }
                build("2")
                build("3") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("3")
                    )
                }
                build("4")

                run("""{
                    branches (id: $id) {
                        builds(filter: {withProperty: "net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType", withPropertyValue: "1"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("1"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since property`() {
        project {
            branch {
                build("1") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("1")
                    )
                }
                build("2")
                build("3") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("3")
                    )
                }
                build("4")

                run("""{
                    branches (id: $id) {
                        builds(filter: {sinceProperty: "net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType"}) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("4", "3"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter since property value`() {
        project {
            branch {
                build("1") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("1")
                    )
                }
                build("2")
                build("3") {
                    setProperty(
                            this,
                            TestSimplePropertyType::class.java,
                            TestSimpleProperty("3")
                    )
                }
                build("4")

                run("""{
                    branches (id: $id) {
                        builds(filter: {
                            sinceProperty: "net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType",
                            sincePropertyValue: "1"
                        }) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("4", "3", "2", "1"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Default filter with linked FROM criteria`() {
        // Project 1
        val build1 = project<Build> {
            branch<Build> {
                build("1.0")
            }
        }
        // Project 2
        val build2 = project<Build> {
            branch<Build> {
                build("2.0")
            }
        }
        // Link build 2 --> build 1
        asAdmin {
            build2.linkTo(build1)
        }

        run("""{
            branches (id: ${build1.branch.id}) {
                builds(filter: {
                    linkedFrom: "${build2.project.name}:*"
                }) {
                    name
                }
            }
        }""") { data ->
            assertEquals(
                    listOf("1.0"),
                    data.path("branches").first()
                            .path("builds")
                            .values().map { it.path("name").asText() }
            )
        }
    }

    @Test
    fun `Default filter with linked TO criteria`() {
        // Project 1
        val build1 = project<Build> {
            branch<Build> {
                build("1.0")
            }
        }
        // Project 2
        val build2 = project<Build> {
            branch<Build> {
                build("2.0")
            }
        }
        // Link build 2 --> build 1
        asAdmin {
            build2.linkTo(build1)
        }

        run("""{
            branches (id: ${build2.branch.id}) {
                builds(filter: {
                    linkedTo: "${build1.project.name}:*"
                }) {
                    name
                }
            }
        }""") { data ->
            assertEquals(
                    listOf("2.0"),
                    data.path("branches").first()
                            .path("builds")
                            .values().map { it.path("name").asText() }
            )
        }
    }

    @Test
    fun `Last promotion filter`() {
        project {
            branch {
                val copper = promotionLevel()
                val bronze = promotionLevel()
                val silver = promotionLevel()

                build("1")
                build("2") {
                    promote(silver)
                }
                build("3") {
                    promote(bronze)
                }
                build("4")
                build("5") {
                    promote(copper)
                }
                build("6")

                run("""{
                    branches (id: $id) {
                        builds(lastPromotions: true) {
                            name
                        }
                    }
                }""") { data ->
                    assertEquals(
                            listOf("5", "3", "2"),
                            data.path("branches").first()
                                    .path("builds")
                                    .values().map { it.path("name").asText() }
                    )
                }
            }
        }
    }

}
