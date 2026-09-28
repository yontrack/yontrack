package net.nemerosa.ontrack.extension.casc.removed

import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CascRemovedKeysTest {

    private val removedSettings = CascRemovedKey.settings("removed-settings", removedIn = "6.0")

    private val removedKeys = CascRemovedKeys(listOf(removedSettings))

    @Test
    fun `A settings key sits under ontrack, config and settings`() {
        assertEquals(
            listOf("ontrack", "config", "settings", "removed-settings"),
            removedSettings.path,
        )
    }

    @Test
    fun `The warning names the key and the version it was removed in`() {
        assertEquals(
            "CasC key ontrack/config/settings/removed-settings is ignored: it was removed in 6.0.",
            removedSettings.warning,
        )
    }

    @Test
    fun `A removed key is taken out of the CasC and reported`() {
        val node = """
            {
              "ontrack": {
                "config": {
                  "settings": {
                    "removed-settings": [{"library": "any"}],
                    "job-history": {"retention": "60d"}
                  }
                }
              }
            }
        """.trimIndent().parseAsJson()

        val found = removedKeys.prune(node)

        assertEquals(listOf(removedSettings), found)
        assertEquals(
            """
                {
                  "ontrack": {
                    "config": {
                      "settings": {
                        "job-history": {"retention": "60d"}
                      }
                    }
                  }
                }
            """.trimIndent().parseAsJson(),
            node,
        )
    }

    @Test
    fun `A CasC without any removed key is left untouched`() {
        val source = """
            {
              "ontrack": {
                "config": {
                  "settings": {
                    "job-history": {"retention": "60d"}
                  }
                }
              }
            }
        """.trimIndent()
        val node = source.parseAsJson()

        val found = removedKeys.prune(node)

        assertTrue(found.isEmpty())
        assertEquals(source.parseAsJson(), node)
    }

    @Test
    fun `A CasC without the parents of a removed key is left untouched`() {
        val source = """
            {
              "ontrack": {
                "admin": {}
              }
            }
        """.trimIndent()
        val node = source.parseAsJson()

        val found = removedKeys.prune(node)

        assertTrue(found.isEmpty())
        assertEquals(source.parseAsJson(), node)
    }

    @Test
    fun `A key with the same name elsewhere in the CasC is not a removed key`() {
        val source = """
            {
              "ontrack": {
                "extensions": {
                  "removed-settings": {}
                }
              }
            }
        """.trimIndent()
        val node = source.parseAsJson()

        val found = removedKeys.prune(node)

        assertTrue(found.isEmpty())
        assertEquals(source.parseAsJson(), node)
    }

    @Test
    fun `No removed key at all`() {
        val node = """{"ontrack": {"config": {"settings": {"removed-settings": {}}}}}""".parseAsJson()
        assertTrue(CascRemovedKeys(emptyList()).prune(node).isEmpty())
    }

}
