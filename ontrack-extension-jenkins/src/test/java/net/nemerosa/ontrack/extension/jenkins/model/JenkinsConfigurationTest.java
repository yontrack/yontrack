package net.nemerosa.ontrack.extension.jenkins.model;

import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JenkinsConfigurationTest {

    @Test
    public void obfuscate() {
        JenkinsConfiguration config = new JenkinsConfiguration("Test", "http://host", "user", "secret");
        assertEquals("secret", config.getPassword());
        config = config.obfuscate();
        assertEquals("", config.getPassword());
    }

}
