package net.nemerosa.ontrack.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JobKeyTest {

    @Test
    public void same_category() {
        JobKey key = Fixtures.TEST_CATEGORY.getType("xxx").getKey("xxx");
        assertTrue(key.sameCategory(Fixtures.TEST_CATEGORY));
    }

    /**
     * The string form of a job key is also the name under which Yontrack 5 recorded a paused job
     * in the settings, read when the job is first stored: it must not change.
     */
    @Test
    public void key_string() {
        JobKey key = Fixtures.TEST_CATEGORY.getType("my-type").getKey("my-key");
        assertEquals("[test][my-type][my-key]", key.toString());
    }

}
