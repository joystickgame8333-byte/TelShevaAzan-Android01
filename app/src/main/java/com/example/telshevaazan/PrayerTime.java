package com.example.telshevaazan;

import java.util.Date;

final class PrayerTime {
    final PrayerKey key;
    final String title;
    final String time;
    final Date date;

    PrayerTime(PrayerKey key, String time, Date date) {
        this.key = key;
        this.title = key.title;
        this.time = time;
        this.date = date;
    }
}
