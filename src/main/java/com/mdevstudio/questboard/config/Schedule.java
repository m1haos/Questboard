package com.mdevstudio.questboard.config;

import com.mdevstudio.questboard.model.Period;
import java.time.DayOfWeek;
import java.time.ZoneId;

public record Schedule(ZoneId zone, int resetHour, DayOfWeek weekStart, int dailyCount, int weeklyCount) {

    public int count(Period period) {
        return period == Period.DAILY ? dailyCount : weeklyCount;
    }
}
