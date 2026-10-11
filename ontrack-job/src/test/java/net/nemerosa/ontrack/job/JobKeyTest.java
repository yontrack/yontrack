package net.nemerosa.ontrack.job;

import net.nemerosa.ontrack.job.support.ConfigurableJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JobKeyTest {

    @Test
    public void same_category() {
        JobKey key = Fixtures.TEST_CATEGORY.getType("xxx").getKey("xxx");
        assertTrue(key.sameCategory(Fixtures.TEST_CATEGORY));
        assertTrue(new ConfigurableJob().getKey().sameCategory(Fixtures.TEST_CATEGORY));
    }

    /**
     * The string form of a job key is also the key of its run lock, shared across instances and
     * versions: it must not change.
     */
    @Test
    public void key_string() {
        JobKey key = Fixtures.TEST_CATEGORY.getType("my-type").getKey("my-key");
        assertEquals("[test][my-type][my-key]", key.toString());
    }

}
