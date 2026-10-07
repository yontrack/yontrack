package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class SCMCommitAssistantServiceImpl(
    private val cachedSettingsService: CachedSettingsService,
) : SCMCommitAssistantService {

    /**
     * Rules compiled for the last settings read. The settings are cached already, but the rules are
     * read for every commit of every change log, and compiling them means compiling regular
     * expressions: they are compiled again only when the settings change.
     */
    private val cache = AtomicReference<CachedRules?>(null)

    override fun getAssistants(commit: SCMCommit): List<SCMCommitAssistant> =
        rules().assistants(commit)

    private fun rules(): AgentMarkerRules {
        val settings = cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java)
        val cached = cache.get()
        return if (cached != null && cached.settings == settings) {
            cached.rules
        } else {
            val rules = AgentMarkerRules(settings)
            cache.set(CachedRules(settings, rules))
            rules
        }
    }

    private class CachedRules(
        val settings: AgentMarkersSettings,
        val rules: AgentMarkerRules,
    )
}
