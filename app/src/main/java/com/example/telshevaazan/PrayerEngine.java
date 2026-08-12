package com.example.telshevaazan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
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

    private PrayerEngine() {}

    static String defaultDateKey() {
        return defaultDateKey(new Date());
    }

    static String defaultDateKey(Date date) {
        return dateKey(date);
    }

    static String automaticScheduleDateKey() {
        return automaticScheduleDateKey(new Date());
    }

    static String automaticScheduleDateKey(Date now) {
        String todayKey = dateKey(now);
        DaySchedule today = schedule(todayKey);
        String ishaTime = today.times.get(PrayerKey.ISHA);
        if (ishaTime != null && !now.before(date(todayKey, ishaTime))) {
            String tomorrowKey = dateKey(todayKey, 1);
            return tomorrowKey == null ? todayKey : tomorrowKey;
        }
        return todayKey;
    }

    static DaySchedule schedule(String dateKey) {
        if (!isValidDateKey(dateKey)) {
            return new DaySchedule(dateKey == null ? "" : dateKey, Collections.emptyMap());
        }
        Date midday = date(dateKey, "12:00");
        return new DaySchedule(dateKey, PalestinePrayerCalendar.schedule(midday));
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
        int relationToToday = daySchedule.dateKey.compareTo(dateKey(now));
        if (relationToToday > 0) {
            return null;
        }
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
        if (!isValidDateKey(dateKey)) {
            return null;
        }
        Calendar calendar = Calendar.getInstance(TIME_ZONE, Locale.US);
        calendar.setTime(date(dateKey, "12:00"));
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        return dateKey(calendar.getTime());
    }

    static List<String> upcomingDateKeys(Date from, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        Calendar calendar = Calendar.getInstance(TIME_ZONE, Locale.US);
        calendar.setTime(from);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(dateKey(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return result;
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

    private static boolean isValidDateKey(String dateKey) {
        if (dateKey == null || !dateKey.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            formatter.setTimeZone(TIME_ZONE);
            formatter.setLenient(false);
            Date parsed = formatter.parse(dateKey);
            return parsed != null && dateKey.equals(formatter.format(parsed));
        } catch (java.text.ParseException ignored) {
            return false;
        }
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
                return 24;
            case ASR:
                return 16;
            case MAGHRIB:
                return 7;
            case DHUHR:
            case ISHA:
            case SUNRISE:
            default:
                return 14;
        }
    }

    static Date iqamaDate(PrayerTime prayer) {
        return new Date(prayer.date.getTime() + (long) iqamaDelayMinutes(prayer.key) * 60000L);
    }

    static String iqamaTime(PrayerTime prayer) {
        return timeText(iqamaDate(prayer), false);
    }

    static PrayerTime activeIqama(Date now) {
        for (PrayerTime prayer : prayerEvents(dateKey(now))) {
            Date iqama = iqamaDate(prayer);
            if (!now.before(prayer.date) && now.before(iqama)) {
                return prayer;
            }
        }
        return null;
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

}
