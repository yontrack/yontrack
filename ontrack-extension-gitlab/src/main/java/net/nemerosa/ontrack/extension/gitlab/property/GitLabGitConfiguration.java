package net.nemerosa.ontrack.extension.gitlab.property;

import net.nemerosa.ontrack.extension.git.model.GitConfiguration;
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService;
import net.nemerosa.ontrack.git.GitRepositoryAuthenticator;
import net.nemerosa.ontrack.git.UsernamePasswordGitRepositoryAuthenticator;
import org.jetbrains.annotations.Nullable;

import static java.lang.String.format;

public class GitLabGitConfiguration implements GitConfiguration {

    public static final String CONFIGURATION_REPOSITORY_SEPARATOR = ":";

    /**
     * User name used for HTTPS Git authentication.
     *
     * <p>GitLab accepts any user name beside a personal access token, and the configuration therefore holds
     * none: {@code oauth2} is the name GitLab's own documentation uses.
     */
    public static final String GIT_USER = "oauth2";

    private final GitLabProjectConfigurationProperty property;
    private final ConfiguredIssueService configuredIssueService;

    public GitLabGitConfiguration(GitLabProjectConfigurationProperty property, ConfiguredIssueService configuredIssueService) {
        this.property = property;
        this.configuredIssueService = configuredIssueService;
    }

    @Override
    public String getType() {
        return "gitlab";
    }

    @Override
    public String getName() {
        return property.getConfiguration().getName();
    }

    public GitLabProjectConfigurationProperty getProperty() {
        return property;
    }

    @Override
    public String getRemote() {
        return format(
                "%s/%s.git",
                property.getConfiguration().getUrl(),
                property.getRepository()
        );
    }

    @Nullable
    @Override
    public GitRepositoryAuthenticator getAuthenticator() {
        return new UsernamePasswordGitRepositoryAuthenticator(
                GIT_USER,
                property.getConfiguration().getToken()
        );
    }

    @Override
    public String getCommitLink() {
        return format(
                // `/-/` is GitLab's canonical form; the legacy one without it only redirects, and a change
                // log must not render two link shapes for the same project.
                "%s/%s/-/commit/{commit}",
                property.getConfiguration().getUrl(),
                property.getRepository()
        );
    }

    @Override
    public String getFileAtCommitLink() {
        return format(
                "%s/%s/-/blob/{commit}/{path}",
                property.getConfiguration().getUrl(),
                property.getRepository()
        );
    }

    @Override
    public int getIndexationInterval() {
        return property.getIndexationInterval();
    }

    @Nullable
    @Override
    public ConfiguredIssueService getConfiguredIssueService() {
        return configuredIssueService;
    }
}
