package net.nemerosa.ontrack.extension.jenkins.model;

import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration;
import net.nemerosa.ontrack.extension.jenkins.JenkinsJobProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JenkinsJobPropertyTest {

    private JenkinsConfiguration configuration;
    private JenkinsJobProperty property;

    @BeforeEach
    public void before() {
        configuration = new JenkinsConfiguration(
                "MyConfig",
                "http://jenkins",
                "user",
                "secret"
        );
    }

    @Test
    public void url_simple() {
        property = new JenkinsJobProperty(
                configuration,
                "MyJob"
        );
        assertEquals("http://jenkins/job/MyJob", property.getUrl());
    }

    @Test
    public void url_folder_1_slash() {
        property = new JenkinsJobProperty(
                configuration,
                "prj/build"
        );
        assertEquals("http://jenkins/job/prj/job/build", property.getUrl());
    }

    @Test
    public void url_folder_2_slash() {
        property = new JenkinsJobProperty(
                configuration,
                "prj/branch/build"
        );
        assertEquals("http://jenkins/job/prj/job/branch/job/build", property.getUrl());
    }

    @Test
    public void url_folder_1_job() {
        property = new JenkinsJobProperty(
                configuration,
                "prj/job/build"
        );
        assertEquals("http://jenkins/job/prj/job/build", property.getUrl());
    }

    @Test
    public void url_folder_2_job() {
        property = new JenkinsJobProperty(
                configuration,
                "prj/job/branch/job/build"
        );
        assertEquals("http://jenkins/job/prj/job/branch/job/build", property.getUrl());
    }

}
