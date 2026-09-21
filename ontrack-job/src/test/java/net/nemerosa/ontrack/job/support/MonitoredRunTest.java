package net.nemerosa.ontrack.job.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class MonitoredRunTest {

    @Test
    public void runListenerOnSuccess() {
        Runnable task = () -> System.out.println("Success");
        MonitoredRunListener runListener = mock(MonitoredRunListener.class);
        MonitoredRun run = new MonitoredRun(task, runListener);
        run.run();
        verify(runListener, times(1)).onStart();
        verify(runListener, times(1)).onSuccess(anyLong());
        verify(runListener, times(0)).onFailure(any(Exception.class));
        verify(runListener, times(1)).onCompletion();
    }

    @Test
    public void runListenerOnFailure() {
        Runnable task = () -> {
            throw new RuntimeException("Failed");
        };
        MonitoredRunListener runListener = mock(MonitoredRunListener.class);
        MonitoredRun run = new MonitoredRun(task, runListener);
        try {
            run.run();
            fail("Should have thrown an exception");
        } catch (RuntimeException ex) {
            assertEquals("Failed", ex.getMessage());
        }
        verify(runListener, times(1)).onStart();
        verify(runListener, times(0)).onSuccess(anyLong());
        verify(runListener, times(1)).onFailure(any(Exception.class));
        verify(runListener, times(1)).onCompletion();
    }

}
