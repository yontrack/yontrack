package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Picks the [MockSCMStore] the mock SCM runs with.
 *
 * The `persistent` property is read here rather than turned into a condition on each
 * implementation: the store also has to answer to `enabled`, like the rest of the mock SCM,
 * and `@ConditionalOnProperty` does not repeat on a class.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "ontrack.config.extension.scm.mock",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class MockSCMStoreConfig {

    @Bean
    fun mockSCMStore(
        scmExtensionConfigProperties: SCMExtensionConfigProperties,
        storageService: StorageService,
    ): MockSCMStore =
        if (scmExtensionConfigProperties.mock.persistent) {
            StorageMockSCMStore(storageService)
        } else {
            InMemoryMockSCMStore()
        }

}
