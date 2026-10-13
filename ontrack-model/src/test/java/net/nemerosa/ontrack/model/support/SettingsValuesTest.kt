package net.nemerosa.ontrack.model.support

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SettingsValuesTest {

    private enum class Level { LOW, HIGH }

    private class SampleSettings(
        val enabled: Boolean,
        val count: Int,
        val duration: Long,
        val text: String,
        val secret: String,
        val level: Level,
    )

    private class OtherSettings

    private val stored = mapOf(
        "enabled" to "true",
        "count" to "12",
        "duration" to "3600000",
        "text" to "Some text",
        "secret" to "encrypted",
        "level" to "HIGH",
        "blank" to "",
    )

    private val values = SettingsValues(SampleSettings::class.java, stored)

    @Test
    fun `Stored values are converted`() {
        assertEquals(true, values.getBoolean(SampleSettings::enabled, false))
        assertEquals(12, values.getInt(SampleSettings::count, 0))
        assertEquals(3600000L, values.getLong(SampleSettings::duration, 0L))
        assertEquals("Some text", values.getString(SampleSettings::text, ""))
        assertEquals(Level.HIGH, values.getEnum(SampleSettings::level, Level.LOW))
    }

    @Test
    fun `Passwords are decrypted`() {
        assertEquals("plain", values.getPassword(SampleSettings::secret, "") { "plain" })
    }

    @Test
    fun `Missing values take their default`() {
        val empty = SettingsValues(SampleSettings::class.java, emptyMap())
        assertEquals(true, empty.getBoolean(SampleSettings::enabled, true))
        assertEquals(5, empty.getInt(SampleSettings::count, 5))
        assertEquals(7L, empty.getLong(SampleSettings::duration, 7L))
        assertEquals("default", empty.getString(SampleSettings::text, "default"))
        assertEquals("default", empty.getPassword(SampleSettings::secret, "default") { error("Not decrypted") })
        assertEquals(Level.LOW, empty.getEnum(SampleSettings::level, Level.LOW))
    }

    @Test
    fun `Blank values take their default`() {
        assertEquals("default", values.getString(SampleSettings::class.java, "blank", "default"))
        assertEquals(3, values.getInt(SampleSettings::class.java, "blank", 3))
    }

    @Test
    fun `The values of another category cannot be read`() {
        assertThrows<IllegalArgumentException> {
            values.getString(OtherSettings::class.java, "text", "")
        }
    }

    @Test
    fun `The values of a category are read in one call to the repository`() {
        val repository = mockk<SettingsRepository>()
        every { repository.getAllValues(SampleSettings::class.java) } returns stored
        val read = repository.values(SampleSettings::class.java)
        assertEquals(true, read.getBoolean(SampleSettings::enabled, false))
        assertEquals("Some text", read.getString(SampleSettings::text, ""))
        verify(exactly = 1) { repository.getAllValues(SampleSettings::class.java) }
    }
}
