package net.nemerosa.ontrack.repository.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static net.nemerosa.ontrack.repository.support.AbstractJdbcRepository.dateTimeForDB;
import static net.nemerosa.ontrack.repository.support.AbstractJdbcRepository.dateTimeFromDB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AbstractJdbcRepositoryTest {

    @Test
    public void dateTimeForDB_null() {
        assertNull(dateTimeForDB(null));
    }

    @Test
    public void dateTimeForDB_ok() {
        assertEquals("2014-05-22T22:07:10", dateTimeForDB(LocalDateTime.of(2014, 5, 22, 22, 7, 10)));
    }

    @Test
    public void dateTime_to_and_from_db() {
        LocalDateTime time = LocalDateTime.of(2014, 5, 22, 22, 7, 10);
        // In the database:
        String db = dateTimeForDB(time);
        assertEquals("2014-05-22T22:07:10", db);
        // Back from the database:
        LocalDateTime back = dateTimeFromDB(db);
        assertEquals(time, back);
    }

}
