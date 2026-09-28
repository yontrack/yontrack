package net.nemerosa.ontrack.extension.jenkins.casc

import net.nemerosa.ontrack.extension.casc.removed.CascRemovedKey
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CasC keys of the Jenkins extension which are no longer supported.
 */
@Configuration
class JenkinsCascRemovedKeys {

    /**
     * Settings of the Jenkins pipeline libraries indicators, removed with the indicators.
     */
    @Bean
    fun jenkinsPipelineLibraryIndicatorRemovedKey() = CascRemovedKey.settings(
        field = "jenkins-pipeline-library-indicator",
        removedIn = "6.0",
    )

}
