package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AsAdminTest
class GQLRootQueryValidationStampNamesIT: AbstractQLKTITSupport() {

    @Test
    fun `Getting filtered names of validation stamps across projects`() {
        withNoGrantViewToAll {
            val prefix = uid("vs_")

            val vsa = project<ValidationStamp> {
                branch<ValidationStamp> {
                    validationStamp(name = uid(prefix))
                }
            }

            val vsb = project<ValidationStamp> {
                branch<ValidationStamp> {
                    validationStamp(name = uid(prefix))
                }
            }

            asAdmin {

                // Restriction on token
                run(
                    """{
                    validationStampNames(token: "$prefix")
                }"""
                ) { data ->
                    val names = data["validationStampNames"].values().map { it.asText() }
                    assertTrue(names.contains(vsa.name), "Contains the first VS")
                    assertTrue(names.contains(vsb.name), "Contains the second VS")
                }

                // Restriction on PL name
                run(
                    """{
                    validationStampNames(token: "${vsa.name}")
                }"""
                ) { data ->
                    val names = data["validationStampNames"].values().map { it.asText() }
                    assertEquals(listOf(vsa.name), names)
                }

            }

            // Restriction on access rights
            asUser().withView(vsa).execute {
                run(
                    """{
                    validationStampNames(token: "$prefix")
                }"""
                ) { data ->
                    val names = data["validationStampNames"].values().map { it.asText() }
                    assertTrue(names.contains(vsa.name), "Contains the first VS")
                    assertFalse(names.contains(vsb.name), "No access to the second VS")
                }
            }
        }
    }

}