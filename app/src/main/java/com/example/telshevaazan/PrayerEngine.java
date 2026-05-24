package com.example.telshevaazan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

final class PrayerEngine {
    static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final String[] HIJRI_MONTHS = {
            "محرم",
            "صفر",
            "ربيع الأول",
            "ربيع الآخر",
            "جمادى الأولى",
            "جمادى الآخرة",
            "رجب",
            "شعبان",
            "رمضان",
            "شوال",
            "ذو القعدة",
            "ذو الحجة"
    };
    private static final int[] HIJRI_MONTH_LENGTHS = {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 30};
    private static final String HIJRI_ANCHOR_DATE = "2026-05-24";
    private static final int HIJRI_ANCHOR_YEAR = 1447;
    private static final int HIJRI_ANCHOR_MONTH = 12;
    private static final int HIJRI_ANCHOR_DAY = 7;
    static final PrayerKey[] PRAYER_ORDER = {
            PrayerKey.FAJR,
            PrayerKey.DHUHR,
            PrayerKey.ASR,
            PrayerKey.MAGHRIB,
            PrayerKey.ISHA
    };
    static final PrayerKey[] DISPLAY_ORDER = {
            PrayerKey.FAJR,
            PrayerKey.SUNRISE,
            PrayerKey.DHUHR,
            PrayerKey.ASR,
            PrayerKey.MAGHRIB,
            PrayerKey.ISHA
    };

    private static final int TEL_SHEVA_OFFSET_MINUTES = 2;
    private static final int DAYLIGHT_SAVING_OFFSET_MINUTES = 60;
    private static final Map<String, Map<PrayerKey, String>> TEL_SHEVA_SCHEDULE = buildSchedule();

    private PrayerEngine() {}

    static List<String> availableDateKeys() {
        List<String> keys = new ArrayList<>(TEL_SHEVA_SCHEDULE.keySet());
        Collections.sort(keys);
        return keys;
    }

    static String defaultDateKey() {
        return defaultDateKey(new Date());
    }

    static String defaultDateKey(Date date) {
        String key = dateKey(date);
        if (TEL_SHEVA_SCHEDULE.containsKey(key)) {
            return key;
        }
        List<String> keys = availableDateKeys();
        return keys.isEmpty() ? key : keys.get(0);
    }

    static DaySchedule schedule(String dateKey) {
        String resolved = TEL_SHEVA_SCHEDULE.containsKey(dateKey) ? dateKey : defaultDateKey();
        return new DaySchedule(resolved, TEL_SHEVA_SCHEDULE.get(resolved));
    }

    static PrayerTime nextPrayer(String dateKey, Date now) {
        DaySchedule daySchedule = schedule(dateKey);
        List<PrayerTime> events = prayerEvents(daySchedule.dateKey);
        if (daySchedule.dateKey.equals(dateKey(now))) {
            for (PrayerTime event : events) {
                if (event.date.after(now)) {
                    return event;
                }
            }
            String nextDateKey = dateKey(daySchedule.dateKey, 1);
            List<PrayerTime> nextEvents = prayerEvents(nextDateKey);
            return nextEvents.isEmpty() ? null : nextEvents.get(0);
        }
        return events.isEmpty() ? null : events.get(0);
    }

    static PrayerTime previousPrayer(String dateKey, Date now) {
        DaySchedule daySchedule = schedule(dateKey);
        List<PrayerTime> events = prayerEvents(daySchedule.dateKey);
        if (daySchedule.dateKey.equals(dateKey(now))) {
            PrayerTime previous = null;
            for (PrayerTime event : events) {
                if (!event.date.after(now)) {
                    previous = event;
                }
            }
            if (previous != null) {
                return previous;
            }
            String previousDateKey = dateKey(daySchedule.dateKey, -1);
            List<PrayerTime> previousEvents = prayerEvents(previousDateKey);
            return previousEvents.isEmpty() ? null : previousEvents.get(previousEvents.size() - 1);
        }
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    static boolean canMove(String dateKey, int offset) {
        return dateKey(dateKey, offset) != null;
    }

    static String dateKey(String dateKey, int offset) {
        List<String> keys = availableDateKeys();
        int index = keys.indexOf(dateKey);
        int nextIndex = index + offset;
        if (index < 0 || nextIndex < 0 || nextIndex >= keys.size()) {
            return null;
        }
        return keys.get(nextIndex);
    }

    static Date date(String dateKey, String time) {
        String[] dateParts = dateKey.split("-");
        String[] timeParts = time.split(":");
        Calendar calendar = Calendar.getInstance(TIME_ZONE, Locale.US);
        calendar.clear();
        calendar.set(
                Integer.parseInt(dateParts[0]),
                Integer.parseInt(dateParts[1]) - 1,
                Integer.parseInt(dateParts[2]),
                Integer.parseInt(timeParts[0]),
                Integer.parseInt(timeParts[1]),
                0
        );
        return calendar.getTime();
    }

    static String dateKey(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setTimeZone(TIME_ZONE);
        return formatter.format(date);
    }

    static String longDateLabel(String dateKey) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar"));
        formatter.setTimeZone(TIME_ZONE);
        return latinDigits(formatter.format(date(dateKey, "12:00")) + " • " + hijriDateLabel(dateKey));
    }

    static String timeText(Date date, boolean withSeconds) {
        SimpleDateFormat formatter = new SimpleDateFormat(withSeconds ? "HH:mm:ss" : "HH:mm", Locale.US);
        formatter.setTimeZone(TIME_ZONE);
        return formatter.format(date);
    }

    static String durationText(long millis) {
        if (millis <= 0) {
            return "--:--:--";
        }
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    static String shortMinuteDuration(long millis) {
        if (millis <= 0) {
            return "--:--";
        }
        long minutes = (millis + 59999) / 60000;
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static int iqamaDelayMinutes(PrayerKey key) {
        switch (key) {
            case FAJR:
                return 25;
            case ASR:
                return 17;
            case MAGHRIB:
                return 8;
            case DHUHR:
            case ISHA:
            case SUNRISE:
            default:
                return 15;
        }
    }

    static String iqamaTime(PrayerTime prayer) {
        long time = prayer.date.getTime() + (long) iqamaDelayMinutes(prayer.key) * 60000L;
        return timeText(new Date(time), false);
    }

    static String calendarIdentifier(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        formatter.setTimeZone(TIME_ZONE);
        return formatter.format(date);
    }

    static String latinDigits(String text) {
        return text
                .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
                .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
                .replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3').replace('۴', '4')
                .replace('۵', '5').replace('۶', '6').replace('۷', '7').replace('۸', '8').replace('۹', '9');
    }

    private static String hijriDateLabel(String dateKey) {
        long days = (date(dateKey, "12:00").getTime() - date(HIJRI_ANCHOR_DATE, "12:00").getTime()) / 86400000L;
        int year = HIJRI_ANCHOR_YEAR;
        int month = HIJRI_ANCHOR_MONTH;
        int day = HIJRI_ANCHOR_DAY;

        while (days > 0) {
            int monthLength = hijriMonthLength(month);
            if (day < monthLength) {
                day++;
            } else {
                day = 1;
                month++;
                if (month > 12) {
                    month = 1;
                    year++;
                }
            }
            days--;
        }

        while (days < 0) {
            if (day > 1) {
                day--;
            } else {
                month--;
                if (month < 1) {
                    month = 12;
                    year--;
                }
                day = hijriMonthLength(month);
            }
            days++;
        }

        return day + " " + HIJRI_MONTHS[month - 1] + "، " + year + " هـ";
    }

    private static int hijriMonthLength(int month) {
        return HIJRI_MONTH_LENGTHS[Math.max(0, Math.min(11, month - 1))];
    }

    private static List<PrayerTime> prayerEvents(String dateKey) {
        if (dateKey == null) {
            return Collections.emptyList();
        }
        DaySchedule schedule = schedule(dateKey);
        List<PrayerTime> result = new ArrayList<>();
        for (PrayerKey key : PRAYER_ORDER) {
            String time = schedule.times.get(key);
            if (time != null) {
                result.add(new PrayerTime(key, time, date(schedule.dateKey, time)));
            }
        }
        return result;
    }

    private static Map<String, Map<PrayerKey, String>> buildSchedule() {
        Map<String, List<String>> source = new LinkedHashMap<>();
        source.put("2026-05-01", Arrays.asList("03:22", "04:50", "11:36", "15:15", "18:26", "19:49"));
        source.put("2026-05-02", Arrays.asList("03:21", "04:49", "11:36", "15:15", "18:27", "19:50"));
        source.put("2026-05-03", Arrays.asList("03:20", "04:48", "11:35", "15:15", "18:27", "19:51"));
        source.put("2026-05-04", Arrays.asList("03:19", "04:47", "11:35", "15:15", "18:28", "19:52"));
        source.put("2026-05-05", Arrays.asList("03:18", "04:46", "11:35", "15:15", "18:29", "19:53"));
        source.put("2026-05-06", Arrays.asList("03:16", "04:45", "11:35", "15:15", "18:29", "19:54"));
        source.put("2026-05-07", Arrays.asList("03:15", "04:44", "11:35", "15:15", "18:30", "19:55"));
        source.put("2026-05-08", Arrays.asList("03:14", "04:43", "11:35", "15:15", "18:31", "19:56"));
        source.put("2026-05-09", Arrays.asList("03:13", "04:43", "11:35", "15:15", "18:31", "19:57"));
        source.put("2026-05-10", Arrays.asList("03:12", "04:42", "11:35", "15:15", "18:32", "19:58"));
        source.put("2026-05-11", Arrays.asList("03:11", "04:41", "11:35", "15:15", "18:33", "19:59"));
        source.put("2026-05-12", Arrays.asList("03:10", "04:40", "11:35", "15:15", "18:34", "20:00"));
        source.put("2026-05-13", Arrays.asList("03:09", "04:40", "11:35", "15:15", "18:34", "20:01"));
        source.put("2026-05-14", Arrays.asList("03:08", "04:39", "11:35", "15:15", "18:35", "20:02"));
        source.put("2026-05-15", Arrays.asList("03:07", "04:38", "11:35", "15:15", "18:36", "20:03"));
        source.put("2026-05-16", Arrays.asList("03:06", "04:38", "11:35", "15:15", "18:36", "20:04"));
        source.put("2026-05-17", Arrays.asList("03:06", "04:37", "11:35", "15:15", "18:37", "20:04"));
        source.put("2026-05-18", Arrays.asList("03:05", "04:37", "11:35", "15:15", "18:37", "20:05"));
        source.put("2026-05-19", Arrays.asList("03:04", "04:36", "11:35", "15:16", "18:38", "20:06"));
        source.put("2026-05-20", Arrays.asList("03:03", "04:35", "11:35", "15:16", "18:39", "20:07"));
        source.put("2026-05-21", Arrays.asList("03:02", "04:35", "11:35", "15:16", "18:39", "20:08"));
        source.put("2026-05-22", Arrays.asList("03:02", "04:34", "11:35", "15:16", "18:40", "20:09"));
        source.put("2026-05-23", Arrays.asList("03:01", "04:34", "11:35", "15:16", "18:41", "20:10"));
        source.put("2026-05-24", Arrays.asList("03:00", "04:34", "11:35", "15:16", "18:41", "20:11"));
        source.put("2026-05-25", Arrays.asList("02:59", "04:33", "11:36", "15:16", "18:42", "20:11"));
        source.put("2026-05-26", Arrays.asList("02:59", "04:33", "11:36", "15:16", "18:42", "20:12"));
        source.put("2026-05-27", Arrays.asList("02:58", "04:32", "11:36", "15:16", "18:43", "20:13"));
        source.put("2026-05-28", Arrays.asList("02:58", "04:32", "11:36", "15:16", "18:44", "20:14"));
        source.put("2026-05-29", Arrays.asList("02:57", "04:32", "11:36", "15:16", "18:44", "20:15"));
        source.put("2026-05-30", Arrays.asList("02:57", "04:31", "11:36", "15:16", "18:45", "20:15"));
        source.put("2026-05-31", Arrays.asList("02:56", "04:31", "11:36", "15:17", "18:45", "20:16"));

        Map<String, Map<PrayerKey, String>> result = new LinkedHashMap<>();
        int offset = TEL_SHEVA_OFFSET_MINUTES + DAYLIGHT_SAVING_OFFSET_MINUTES;
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> times = entry.getValue();
            Map<PrayerKey, String> day = new LinkedHashMap<>();
            day.put(PrayerKey.FAJR, addMinutes(times.get(0), offset));
            day.put(PrayerKey.SUNRISE, addMinutes(times.get(1), offset));
            day.put(PrayerKey.DHUHR, addMinutes(times.get(2), offset));
            day.put(PrayerKey.ASR, addMinutes(times.get(3), offset));
            day.put(PrayerKey.MAGHRIB, addMinutes(times.get(4), offset));
            day.put(PrayerKey.ISHA, addMinutes(times.get(5), offset));
            result.put(entry.getKey(), day);
        }
        return result;
    }

    private static String addMinutes(String time, int minutes) {
        String[] parts = time.split(":");
        int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]) + minutes;
        total = (total + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }
}
