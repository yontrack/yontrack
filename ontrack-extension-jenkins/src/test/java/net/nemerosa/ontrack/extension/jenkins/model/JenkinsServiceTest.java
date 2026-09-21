package net.nemerosa.ontrack.extension.jenkins.model;

import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration;
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService;
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationServiceImpl;
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClient;
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClientFactory;
import net.nemerosa.ontrack.model.events.EventFactory;
import net.nemerosa.ontrack.model.events.EventPostService;
import net.nemerosa.ontrack.model.security.EncryptionService;
import net.nemerosa.ontrack.model.security.SecurityService;
import net.nemerosa.ontrack.model.support.ConfigurationRepository;
import net.nemerosa.ontrack.model.support.OntrackConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class JenkinsServiceTest {

    private ConfigurationRepository configurationRepository;
    private JenkinsConfigurationService jenkinsService;
    private EncryptionService encryptionService;

    @BeforeEach
    public void before() {
        SecurityService securityService = mock(SecurityService.class);
        configurationRepository = mock(ConfigurationRepository.class);
        encryptionService = mock(EncryptionService.class);

        JenkinsClientFactory jenkinsClientFactory = mock(JenkinsClientFactory.class);
        JenkinsClient okJenkinsClient = mock(JenkinsClient.class);
        when(jenkinsClientFactory.getClient(any(JenkinsConfiguration.class))).thenReturn(okJenkinsClient);

        OntrackConfigProperties ontrackConfigProperties = new OntrackConfigProperties();

        jenkinsService = new JenkinsConfigurationServiceImpl(configurationRepository, securityService, encryptionService,
                mock(EventPostService.class), mock(EventFactory.class), jenkinsClientFactory, ontrackConfigProperties);
    }

    @Test
    public void update_name_check() {
        assertThrows(IllegalStateException.class, () ->
                jenkinsService.updateConfiguration("test", new JenkinsConfiguration("Test", "http://host", "user", ""))
        );
    }

    @Test
    public void update_blank_password() {
        when(encryptionService.encrypt("secret")).thenReturn("xxxxx");
        when(encryptionService.decrypt("xxxxx")).thenReturn("secret");
        when(configurationRepository.find(JenkinsConfiguration.class, "test")).thenReturn(
            new JenkinsConfiguration("test", "http://host", "user", "xxxxx")
        );
        jenkinsService.updateConfiguration("test", new JenkinsConfiguration("test", "http://host", "user", ""));
        verify(configurationRepository, times(1)).save(new JenkinsConfiguration("test", "http://host", "user", "xxxxx"));
    }

    @Test
    public void update_blank_password_for_different_user() {
        when(encryptionService.encrypt("")).thenReturn("xxxxx");
        when(configurationRepository.find(JenkinsConfiguration.class, "test")).thenReturn(
            new JenkinsConfiguration("test", "http://host", "user", "xxxxx")
        );
        jenkinsService.updateConfiguration("test", new JenkinsConfiguration("test", "http://host", "user1", ""));
        verify(configurationRepository, times(1)).save(new JenkinsConfiguration("test", "http://host", "user1", "xxxxx"));
    }

    @Test
    public void update_new_password() {
        when(encryptionService.encrypt("pwd")).thenReturn("xxxxx");
        when(configurationRepository.find(JenkinsConfiguration.class, "test")).thenReturn(
            new JenkinsConfiguration("test", "http://host", "user", "xxxxx")
        );
        jenkinsService.updateConfiguration("test", new JenkinsConfiguration("test", "http://host", "user", "pwd"));
        verify(configurationRepository, times(1)).save(new JenkinsConfiguration("test", "http://host", "user", "xxxxx"));
    }

}
