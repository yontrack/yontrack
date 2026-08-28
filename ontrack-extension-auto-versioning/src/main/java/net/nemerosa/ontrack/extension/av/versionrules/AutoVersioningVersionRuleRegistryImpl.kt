package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.extension.api.ExtensionManager
import org.springframework.stereotype.Service

@Service
class AutoVersioningVersionRuleRegistryImpl(
    private val extensionManager: ExtensionManager,
) : AutoVersioningVersionRuleRegistry {

    @Suppress("UNCHECKED_CAST")
    override fun <T> findVersionRuleById(id: String): AutoVersioningVersionRule<T>? =
        allVersionRules.find { it.id == id } as? AutoVersioningVersionRule<T>?

    override val allVersionRules: List<AutoVersioningVersionRule<*>> by lazy {
        extensionManager.getExtensions(AutoVersioningVersionRule::class.java).toList()
    }

}
