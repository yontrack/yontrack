package net.nemerosa.ontrack.model.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationRunTest {

    @Test
    public void of() {
        Project project = Project.of(new NameDescription("P", "Project")).withId(ID.of(1));
        Branch branch = Branch.of(project, new NameDescription("B", "Branch")).withId(ID.of(1));
        Build build = Build.of(branch, new NameDescription("1", "Build 1"), Signature.of("user")).withId(ID.of(1));
        ValidationStamp stamp = ValidationStamp.of(branch, new NameDescription("S", "Stamp")).withId(ID.of(1));
        ValidationRunStatusID statusId = ValidationRunStatusID.of(ValidationRunStatusID.PASSED, "Passed", true, true);
        ValidationRun run = ValidationRun.of(
                build,
                stamp,
                1,
                Signature.of("validator"),
                statusId,
                "Some validation"
        );
        // Checks
        Entity.isEntityNew(run, "Validation run must be new");
        assertSame(build, run.getBuild());
        assertSame(stamp, run.getValidationStamp());
        assertEquals(1, run.getRunOrder());
        List<ValidationRunStatus> statuses = run.getValidationRunStatuses();
        assertNotNull(statuses);
        assertEquals(1, statuses.size());
        ValidationRunStatus status = statuses.get(0);
        assertSame(statusId, status.getStatusID());
        assertEquals("Some validation", status.getDescription());
        assertEquals("validator", status.getSignature().getUser().getName());
    }

}
