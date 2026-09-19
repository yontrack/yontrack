package net.nemerosa.ontrack.extension.gitlab.service;

import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory;
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration;
import net.nemerosa.ontrack.extension.support.AbstractConfigurationService;
import net.nemerosa.ontrack.model.events.EventFactory;
import net.nemerosa.ontrack.model.events.EventPostService;
import net.nemerosa.ontrack.model.security.EncryptionService;
import net.nemerosa.ontrack.model.security.SecurityService;
import net.nemerosa.ontrack.model.support.ConfigurationRepository;
import net.nemerosa.ontrack.model.support.ConnectionResult;
import net.nemerosa.ontrack.model.support.OntrackConfigProperties;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GitLabConfigurationServiceImpl extends AbstractConfigurationService<GitLabConfiguration> implements GitLabConfigurationService {

    private final Logger logger = LoggerFactory.getLogger(GitLabConfigurationServiceImpl.class);

    private final GitLabClientFactory gitLabClientFactory;

    @Autowired
    public GitLabConfigurationServiceImpl(
            ConfigurationRepository configurationRepository,
            SecurityService securityService,
            EncryptionService encryptionService,
            EventPostService eventPostService,
            EventFactory eventFactory,
            GitLabClientFactory gitLabClientFactory,
            OntrackConfigProperties ontrackConfigProperties
    ) {
        super(
                GitLabConfiguration.class,
                configurationRepository,
                securityService,
                encryptionService,
                eventPostService,
                eventFactory,
                ontrackConfigProperties
        );
        this.gitLabClientFactory = gitLabClientFactory;
    }

    @NotNull
    @Override
    public String getType() {
        return "gitlab";
    }

    @Override
    protected ConnectionResult validate(GitLabConfiguration configuration) {
        try {
            // Checks the connection and the token
            gitLabClientFactory.create(configuration).validate();
            // OK
            return ConnectionResult.ok();
        } catch (Exception ex) {
            logger.error(
                    String.format(
                            "Cannot validate GitLab configuration: remote: %s, name: %s",
                            configuration.getUrl(),
                            configuration.getName()
                    ),
                    ex
            );
            return ConnectionResult.error(ex.getMessage());
        }
    }
}
