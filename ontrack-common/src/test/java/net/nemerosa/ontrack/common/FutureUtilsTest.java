package net.nemerosa.ontrack.common;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FutureUtilsTest {

    private static class TestException extends BaseException {
        public TestException() {
            super("");
        }
    }

    private static class TestTask implements Callable<String> {

        private final String value;
        private final long sleep;
        private final Supplier<Exception> exceptionSupplier;

        private TestTask(String value) {
            this(value, 500);
        }

        public TestTask(String value, long sleep) {
            this(value, sleep, null);
        }

        public TestTask(String value, long sleep, Supplier<Exception> exceptionSupplier) {
            this.value = value;
            this.sleep = sleep;
            this.exceptionSupplier = exceptionSupplier;
        }

        @Override
        public String call() throws Exception {
            Thread.sleep(sleep);
            if (exceptionSupplier != null) {
                throw exceptionSupplier.get();
            } else {
                return value.toUpperCase();
            }
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(
            2,
            BasicThreadFactory.builder().daemon(true).build()
    );

    @Test
    public void taskOptionalEmpty() {
        assertThrows(TaskNotScheduledException.class, () ->
                FutureUtils.wait("Test ok", Optional.empty())
        );
    }

    @Test
    public void taskOptionalCompleted() {
        TestTask task = new TestTask("ok");
        Future<String> future = executor.submit(task);
        String value = FutureUtils.wait("Test ok", Optional.of(future));
        assertEquals("OK", value);
    }

    @Test
    public void taskCompleted() {
        TestTask task = new TestTask("ok");
        Future<String> future = executor.submit(task);
        String value = FutureUtils.wait("Test ok", future);
        assertEquals("OK", value);
    }

    @Test
    public void taskTimeout() {
        TestTask task = new TestTask("timeout", 2000);
        Future<String> future = executor.submit(task);
        assertThrows(TaskTimeoutException.class, () -> FutureUtils.wait("exception", future, 1));
    }

    @Test
    public void taskBaseException() {
        TestTask task = new TestTask("exception", 500, TestException::new);
        Future<String> future = executor.submit(task);
        assertThrows(TestException.class, () -> FutureUtils.wait("Test exception", future, 1));
    }

    @Test
    public void taskCheckedException() {
        TestTask task = new TestTask("exception", 500, IOException::new);
        Future<String> future = executor.submit(task);
        assertThrows(TaskExecutionException.class, () ->
                FutureUtils.wait("exception", future, 1)
        );
    }

    @Test
    public void taskCancelled() {
        TestTask task = new TestTask("cancelled", 2000);
        Future<String> future = executor.submit(task);
        // Schedules an interruption
        executor.submit(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            future.cancel(true);
        });
        // Waits for the cancellation
        assertThrows(TaskCancelledException.class, () ->
                FutureUtils.wait("Test cancelled", future, 3)
        );
    }

}
