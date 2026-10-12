package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.config.Schedule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class Rotation {

    private final Schedule schedule;

    public Rotation(Schedule schedule) {
        this.schedule = schedule;
    }

    public String day(Instant now) {
        return shifted(now).toString();
    }

    public String week(Instant now) {
        return weekStart(shifted(now)).toString();
    }

    public String previousDay(String day) {
        return LocalDate.parse(day).minusDays(1).toString();
    }

    public Duration untilDaily(Instant now) {
        return Duration.between(now, resetAt(shifted(now).plusDays(1)));
    }

    public Duration untilWeekly(Instant now) {
        return Duration.between(now, resetAt(weekStart(shifted(now)).plusWeeks(1)));
    }

    // The quest day starts at the reset hour, so 02:00 on Tuesday still belongs to Monday's board.
    private LocalDate shifted(Instant now) {
        return ZonedDateTime.ofInstant(now, schedule.zone()).minusHours(schedule.resetHour()).toLocalDate();
    }

    private LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(schedule.weekStart()));
    }

    private Instant resetAt(LocalDate date) {
        return date.atStartOfDay(schedule.zone()).plusHours(schedule.resetHour()).toInstant();
    }
}
