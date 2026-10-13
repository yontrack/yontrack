package net.nemerosa.ontrack.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScheduleTest {

    @Test
    public void display_manual() {
        assertEquals("Manually", Schedule.everyMinutes(0).getPeriodText());
    }

    @Test
    public void display_one_minute() {
        assertEquals("Every minute", Schedule.everyMinutes(1).getPeriodText());
    }

    @Test
    public void display_several_minute() {
        assertEquals("Every 10 minutes", Schedule.everyMinutes(10).getPeriodText());
    }

    @Test
    public void initial_delay_as_a_duration_in_the_unit_of_the_schedule() {
        Schedule schedule = Schedule.EVERY_WEEK.after(Duration.ofDays(7));
        assertEquals(7, schedule.getInitialPeriod());
        assertEquals(7, schedule.getPeriod());
        assertEquals(TimeUnit.DAYS, schedule.getUnit());
    }

}
