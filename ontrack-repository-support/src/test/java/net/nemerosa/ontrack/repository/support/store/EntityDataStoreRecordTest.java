package net.nemerosa.ontrack.repository.support.store;

import com.fasterxml.jackson.databind.node.IntNode;
import net.nemerosa.ontrack.common.Time;
import net.nemerosa.ontrack.model.structure.NameDescription;
import net.nemerosa.ontrack.model.structure.Project;
import net.nemerosa.ontrack.model.structure.Signature;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityDataStoreRecordTest {

    @Test
    public void compare_on_date_only() {
        Project project = Project.of(NameDescription.nd("P", ""));
        LocalDateTime now = Time.now();
        LocalDateTime t1 = now.minusDays(1);
        LocalDateTime t2 = now.minusDays(2);
        List<EntityDataStoreRecord> records = Arrays.asList(
                new EntityDataStoreRecord(1, project, "TEST", "N1", null,
                        Signature.of(t1, "test"), new IntNode(10)),
                new EntityDataStoreRecord(2, project, "TEST", "N1", null,
                        Signature.of(t2, "test"), new IntNode(10))
        );
        records.sort(Comparator.naturalOrder());
        assertTrue(
                records.get(0).getId() == 1,
                "Newest record is first"
        );
    }

    @Test
    public void compare_on_id_needed() {
        Project project = Project.of(NameDescription.nd("P", ""));
        LocalDateTime now = Time.now();
        List<EntityDataStoreRecord> records = Arrays.asList(
                new EntityDataStoreRecord(1, project, "TEST", "N1", null,
                        Signature.of(now, "test"), new IntNode(10)),
                new EntityDataStoreRecord(2, project, "TEST", "N1", null,
                        Signature.of(now, "test"), new IntNode(10))
        );
        records.sort(Comparator.naturalOrder());
        assertTrue(
                records.get(0).getId() == 2,
                "Newest record is first by ID if dates are equal"
        );
    }

}
