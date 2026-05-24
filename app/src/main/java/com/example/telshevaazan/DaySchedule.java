package com.example.telshevaazan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DaySchedule {
    final String dateKey;
    final Map<PrayerKey, String> times;

    DaySchedule(String dateKey, Map<PrayerKey, String> times) {
        this.dateKey = dateKey;
        this.times = times;
    }

    List<PrayerTime> displayTimes() {
        List<PrayerTime> result = new ArrayList<>();
        for (PrayerKey key : PrayerEngine.DISPLAY_ORDER) {
            String time = times.get(key);
            if (time == null) {
                continue;
            }
            result.add(new PrayerTime(key, time, PrayerEngine.date(dateKey, time)));
        }
        return result;
    }
}
