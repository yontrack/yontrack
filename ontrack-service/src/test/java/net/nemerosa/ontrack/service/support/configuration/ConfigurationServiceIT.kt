package net.nemerosa.ontrack.service.support.configuration

import net.nemerosa.ontrack.extension.api.support.TestConfiguration
import net.nemerosa.ontrack.extension.api.support.TestConfiguration.Companion.config
import net.nemerosa.ontrack.extension.api.support.TestConfigurationService
import net.nemerosa.ontrack.extension.api.support.TestProperty
import net.nemerosa.ontrack.extension.api.support.TestProperty.Companion.of
import net.nemerosa.ontrack.extension.api.support.TestPropertyType
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.ConfigurationValidationException
import net.nemerosa.ontrack.model.support.ConnectionResultType
import net.nemerosa.ontrack.repository.PropertyRepository
import net.nemerosa.ontrack.test.TestUtils
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@AsAdminTest
class ConfigurationServiceIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var configurationService: TestConfigurationService

    @Autowired
    private lateinit var configurationRepository: ConfigurationRepository

    @Autowired
    private lateinit var encryptionService: EncryptionService

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    @Test
    fun validate_ok_on_new_configuration() {
        val configuration = TestConfiguration(TestUtils.uid("T"), "check", "test")
        val savedConfig =
            asUser().with(GlobalSettings::class.java).call { configurationService.newConfiguration(configuration) }
        assertEquals(configuration.name, savedConfig.name)
        assertTrue(StringUtils.isEmpty(savedConfig.password))
    }

    @Test
    fun validate_nok_on_new_configuration() {
        val configuration = TestConfiguration(TestUtils.uid("T"), "check", "xxx")
        assertFailsWith<ConfigurationValidationException> {
            asUser().with(GlobalSettings::class.java).call { configurationService.newConfiguration(configuration) }
        }
    }

    /**
     * Regression test for #531
     */
    @Test
    fun test_ok_on_new_configuration_with_blank_password() {
        val configuration = TestConfiguration(TestUtils.uid("T"), "", "")
        val result = asUser().with(GlobalSettings::class.java).call { configurationService.test(configuration) }
        assertEquals(ConnectionResultType.OK, result.type)
    }

    @Test
    fun validate_ok_on_updated_configuration_with_old_password() {
        // Creates a configuration
        val name = TestUtils.uid("T")
        val configuration = TestConfiguration(name, "check", "test")
        asUser().with(GlobalSettings::class.java).call { configurationService.newConfiguration(configuration) }
        // Updates the configuration and fills the password
        val updatedConfiguration = TestConfiguration(name, "check", "test")
        asUser().with(GlobalSettings::class.java).call<Any> {
            configurationService.updateConfiguration(name, updatedConfiguration)
        }
    }

    @Test
    fun validate_ok_on_updated_configuration_without_old_password() {
        // Creates a configuration
        val name = TestUtils.uid("T")
        val configuration = TestConfiguration(name, "check", "test")
        asUser().with(GlobalSettings::class.java).call { configurationService.newConfiguration(configuration) }
        // Updates the configuration and fills the password
        val updatedConfiguration = TestConfiguration(name, "check", "")
        asUser().with(GlobalSettings::class.java).call<Any> {
            configurationService.updateConfiguration(name, updatedConfiguration)
        }
    }

    @Test
    fun validate_nok_on_updated_configuration_with_wrong_old_password() {
        // Creates a configuration
        val name = TestUtils.uid("T")
        val configuration = TestConfiguration(name, "check", "test")
        asUser().with(GlobalSettings::class.java).call { configurationService.newConfiguration(configuration) }
        // Updates the configuration and fills the password
        val updatedConfiguration = TestConfiguration(name, "check", "xxx")
        assertFailsWith<ConfigurationValidationException> {
            asUser().with(GlobalSettings::class.java).call<Any> {
                configurationService.updateConfiguration(name, updatedConfiguration)
            }
        }
    }

    @Test
    fun encryption() {
        val crypted = encryptionService.encrypt("test")
        assertNotEquals("test", crypted)
        assertEquals("test", encryptionService.decrypt(crypted))
    }

    @Test
    fun encryptedPasswordForNewConfig() {
        // Creates a new configuration, with a password we want to keep secret
        val configuration = config("test1")
        asUser().with(GlobalSettings::class.java).call {
            // Saves this configuration in the database
            val savedConfiguration = configurationService.newConfiguration(configuration)
            // The returned password is not encrypted (we may need it)
            assertEquals("", savedConfiguration.password)
            // Loads the configuration
            // The returned password is not encrypted (we may need it)
            val loadedConfiguration = configurationService.getConfiguration("test1")
            assertEquals(TestConfiguration.PLAIN_PASSWORD, loadedConfiguration.password)
            // Now, checks the raw result in the repository
            val rawConfiguration = configurationRepository.find(
                TestConfiguration::class.java, "test1"
            )!!
            assertNotNull(rawConfiguration)
            assertNotEquals("Password must be encrypted", TestConfiguration.PLAIN_PASSWORD, rawConfiguration.password)
            true
        }
    }

    @Test
    fun encryptedPasswordForSavedConfigWithNewPassword() {
        // Creates a new configuration, with a password we want to keep secret
        val testName = "test2"
        val configuration = config(testName)
        asUser().with(GlobalSettings::class.java).call {
            // Saves this configuration in the database
            val savedConfiguration = configurationService.newConfiguration(configuration)
            // Now saves again this configuration with a new password
            configurationService.updateConfiguration(
                "test2",
                savedConfiguration.withPassword("newpassword")
            )
            // Loads the configuration
            val loadedConfiguration = configurationService.getConfiguration("test2")
            assertEquals("newpassword", loadedConfiguration.password)
            // Now, checks the raw result in the repository
            val rawConfiguration = configurationRepository.find(
                TestConfiguration::class.java, "test2"
            )!!
            assertNotNull(rawConfiguration)
            assertNotEquals("Password must be encrypted", TestConfiguration.PLAIN_PASSWORD, rawConfiguration.password)
            assertNotEquals("Password must be encrypted", "newpassword", rawConfiguration.password)
            true
        }
    }

    @Test
    fun encryptedPasswordForSavedConfigWithNoNewPassword() {
        // Creates a new configuration, with a password we want to keep secret
        val configuration = config("test3")
        asUser().with(GlobalSettings::class.java).call {
            // Saves this configuration in the database
            val savedConfiguration = configurationService.newConfiguration(configuration)
            // Now saves again this configuration with no new password
            configurationService.updateConfiguration(
                "test3",
                savedConfiguration.withPassword("")
            )
            // Loads the configuration
            val loadedConfiguration = configurationService.getConfiguration("test3")
            assertEquals(TestConfiguration.PLAIN_PASSWORD, loadedConfiguration.password)
            // Now, checks the raw result in the repository
            val rawConfiguration = configurationRepository.find(
                TestConfiguration::class.java, "test3"
            )!!
            assertNotNull(rawConfiguration)
            assertNotEquals("Password must be encrypted", TestConfiguration.PLAIN_PASSWORD, rawConfiguration.password)
            true
        }
    }

    @Test
    fun update_name_check() {
        assertFailsWith<IllegalStateException> {
            asUser().with(GlobalSettings::class.java).call {
                configurationService.updateConfiguration("test", config("testx"))
            }
        }
    }

    /**
     * Checks that the password is unchanged when it is given as blank for the same user.
     */
    @Test
    fun update_blank_password() {
        asUser().with(GlobalSettings::class.java).call {
            configurationService.newConfiguration(config("test5"))
            // Gets the saved password
            val savedPassword = configurationService.getConfiguration("test5").password
            // Update with blank password
            configurationService.updateConfiguration("test5", config("test5").withPassword(""))
            // Gets the saved password and compares it
            val newSavedPassword = configurationService.getConfiguration("test5").password
            assertEquals(savedPassword, newSavedPassword)
            true
        }
    }

    /**
     * Checks that the password is changed when it is given as blank for another user.
     */
    @Test
    fun update_blank_password_for_different_user() {
        asUser().with(GlobalSettings::class.java).call {
            configurationService.newConfiguration(config("test6"))
            // Update with blank password for another user
            configurationService.updateConfiguration("test6", TestConfiguration("test6", "user2", "").withPassword(""))
            // Gets the configuration back and checks the password is blank
            assertEquals("", configurationService.getConfiguration("test6").password)
            assertEquals("user2", configurationService.getConfiguration("test6").user)
            true
        }
    }

    /**
     * Checks that configuration properties are removed when an associated configuration is deleted.
     */
    @Test
    fun configuration_property_removed_on_configuration_deleted() {
        // Creates two configurations
        val conf1Name = TestUtils.uid("C")
        val conf2Name = TestUtils.uid("C")
        val conf1 = asUser().with(GlobalSettings::class.java)
            .call { configurationService.newConfiguration(config(conf1Name)) }
        val conf2 = asUser().with(GlobalSettings::class.java)
            .call { configurationService.newConfiguration(config(conf2Name)) }
        // Creates two projects
        val p1 = doCreateProject()
        val p2 = doCreateProject()
        // Sets the properties
        asUser().withProjectFunction(
            p1,
            ProjectEdit::class.java
        ).call {
            propertyService.editProperty(
                p1,
                TestPropertyType::class.java,
                of(conf1, "1")
            )
        }
        asUser().withProjectFunction(
            p2,
            ProjectEdit::class.java
        ).call {
            propertyService.editProperty(
                p2,
                TestPropertyType::class.java,
                of(conf2, "2")
            )
        }
        // Assert the properties are there
        asUser().withProjectFunction(
            p1,
            ProjectView::class.java
        ).execute(Runnable {
            assertTrue(
                propertyService.hasProperty(
                    p1,
                    TestPropertyType::class.java
                )
            )
        }
        )
        asUser().withProjectFunction(
            p2,
            ProjectView::class.java
        ).execute(Runnable {
            assertTrue(
                propertyService.hasProperty(
                    p2,
                    TestPropertyType::class.java
                )
            )
        }
        )
        // Deletes the first configuration
        asUser().with(GlobalSettings::class.java)
            .execute(Runnable { configurationService.deleteConfiguration(conf1Name) }
            )
        // Checks the property 1 is gone
        asUser().withProjectFunction(p1, ProjectView::class.java).execute {
            assertFalse(
                propertyService.hasProperty<TestProperty>(
                    p1,
                    TestPropertyType::class.java
                ),
                "Project configuration should be gone"
            )
        }
        // ... but not the second one
        asUser().withProjectFunction(
            p2,
            ProjectView::class.java
        ).execute(Runnable {
            assertTrue(
                propertyService.hasProperty(
                    p2,
                    TestPropertyType::class.java
                )
            )
        })
    }

    /**
     * Checks that configuration properties are removed when an associated configuration is deleted,
     * even when another property of the same type cannot be read.
     */
    @Test
    fun configuration_property_removed_on_configuration_deleted_despite_unreadable_property() {
        // Creates two configurations
        val conf1Name = TestUtils.uid("C")
        val conf2Name = TestUtils.uid("C")
        val conf1 = configurationService.newConfiguration(config(conf1Name))
        val conf2 = configurationService.newConfiguration(config(conf2Name))
        // Properties pointing to the configurations
        val p1 = doCreateProject()
        val p2 = doCreateProject()
        propertyService.editProperty(p1, TestPropertyType::class.java, of(conf1, "1"))
        propertyService.editProperty(p2, TestPropertyType::class.java, of(conf2, "2"))
        // A property which cannot be read (no value), stored last so that it is met first by the cleanup
        val unreadable = doCreateProject()
        propertyRepository.saveProperty(
            TestPropertyType::class.java.name,
            unreadable.projectEntityType,
            unreadable.id,
            mapOf("configuration" to conf1Name).asJson()
        )
        // Deletes the first configuration
        configurationService.deleteConfiguration(conf1Name)
        // The property pointing to the deleted configuration is gone
        assertFalse(
            propertyService.hasProperty(p1, TestPropertyType::class.java),
            "Property pointing to the deleted configuration should be gone"
        )
        // ... but not the one pointing to the other configuration
        assertTrue(propertyService.hasProperty(p2, TestPropertyType::class.java))
        // ... and the unreadable one is left alone
        assertTrue(propertyService.hasProperty(unreadable, TestPropertyType::class.java))
        // The configuration is gone
        assertNull(configurationService.findConfiguration(conf1Name))
    }
}
