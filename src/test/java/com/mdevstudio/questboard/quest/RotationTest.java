package com.mdevstudio.questboard.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mdevstudio.questboard.config.Schedule;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RotationTest {

    private final Rotation rotation =
            new Rotation(new Schedule(ZoneId.of("UTC"), 4, DayOfWeek.MONDAY, 3, 2));

    @Test
    void nightBeforeTheResetHourBelongsToThePreviousDay() {
        assertEquals("2026-10-12", rotation.day(Instant.parse("2026-10-13T03:59:00Z")));
        assertEquals("2026-10-13", rotation.day(Instant.parse("2026-10-13T04:00:00Z")));
    }

    @Test
    void weekStartsOnTheConfiguredDay() {
        assertEquals("2026-10-12", rotation.week(Instant.parse("2026-10-13T10:00:00Z")));
        assertEquals("2026-10-05", rotation.week(Instant.parse("2026-10-11T10:00:00Z")));
        assertEquals("2026-10-05", rotation.week(Instant.parse("2026-10-12T03:00:00Z")));
    }

    @Test
    void countdownsPointAtTheNextReset() {
        assertEquals(Duration.ofHours(1), rotation.untilDaily(Instant.parse("2026-10-13T03:00:00Z")));
        assertEquals(Duration.ofHours(18), rotation.untilWeekly(Instant.parse("2026-10-18T10:00:00Z")));
    }

    @Test
    void previousDayCrossesMonthEnds() {
        assertEquals("2026-09-30", rotation.previousDay("2026-10-01"));
    }
}
