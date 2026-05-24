package com.example.telshevaazan;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

final class PrayerNotificationScheduler {
    static final String ACTION_NOTIFY = "com.example.telshevaazan.ACTION_NOTIFY";
    static final String ACTION_RESCHEDULE = "com.example.telshevaazan.ACTION_RESCHEDULE";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_BODY = "body";
    static final String EXTRA_SOUND = "sound";
    static final String KIND_ADHAN = "adhan";
    static final String KIND_NAFAHAT = "nafahat";
    static final String CHANNEL_ADHAN = "salati_prayers";
    static final String CHANNEL_NAFAHAT = "salati_adhkar";
    private static final int MAX_PENDING = 60;

    private PrayerNotificationScheduler() {}

    static void scheduleAll(Context context) {
        cancelAll(context);
        ensureChannels(context);

        if (!notificationsAllowed(context)) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        List<Event> events = upcomingEvents(context);
        for (int i = 0; i < events.size() && i < MAX_PENDING; i++) {
            Event event = events.get(i);
            Intent intent = new Intent(context, PrayerNotificationReceiver.class);
            intent.setAction(ACTION_NOTIFY);
            intent.putExtra(EXTRA_KIND, event.kind);
            intent.putExtra(EXTRA_TITLE, event.title);
            intent.putExtra(EXTRA_BODY, event.body);
            intent.putExtra(EXTRA_SOUND, event.sound);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    event.requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            setAlarm(alarmManager, event.when.getTime(), pendingIntent);
        }

        SalatiWidgetUpdater.scheduleNextMinute(context);
    }

    static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        for (int requestCode = 42000; requestCode < 42180; requestCode++) {
            Intent intent = new Intent(context, PrayerNotificationReceiver.class);
            intent.setAction(ACTION_NOTIFY);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }

    static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static Uri soundUri(Context context, String sound) {
        if (SalatiSettings.SOUND_SYSTEM.equals(sound)) {
            return Settings.System.DEFAULT_NOTIFICATION_URI;
        }

        int resource;
        switch (sound) {
            case SalatiSettings.SOUND_ADHAN_FIRST:
                resource = R.raw.adhan_original_android;
                break;
            case SalatiSettings.SOUND_SOFT:
                resource = R.raw.notification_soft_01;
                break;
            case "nafahat2":
                resource = R.raw.notification_soft_02;
                break;
            case "nafahat3":
                resource = R.raw.notification_soft_03;
                break;
            case "nafahat4":
                resource = R.raw.notification_soft_04;
                break;
            case SalatiSettings.SOUND_ADHAN_SECOND:
            default:
                resource = R.raw.adhan_mohamed_jazi_android;
                break;
        }
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + resource);
    }

    static void ensureChannels(Context context) {
        ensureChannel(context, KIND_ADHAN, SalatiSettings.adhanSound(context));
        ensureChannel(context, KIND_NAFAHAT, "nafahat2");
    }

    static void ensureChannel(Context context, String kind, String sound) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        if (KIND_NAFAHAT.equals(kind)) {
            NotificationChannel nafahat = new NotificationChannel(channelId(kind, sound), "أذكار ونفحات", NotificationManager.IMPORTANCE_DEFAULT);
            nafahat.setDescription("تذكير روحي خفيف خلال اليوم");
            nafahat.setSound(soundUri(context, "nafahat2"), attributes);
            manager.createNotificationChannel(nafahat);
            return;
        }

        String resolvedSound = sound == null ? SalatiSettings.SOUND_ADHAN_SECOND : sound;
        NotificationChannel adhan = new NotificationChannel(channelId(kind, resolvedSound), "تنبيهات الأذان", NotificationManager.IMPORTANCE_HIGH);
        adhan.setDescription("تنبيهات مواقيت الصلاة المختارة");
        adhan.setSound(soundUri(context, resolvedSound), attributes);
        manager.createNotificationChannel(adhan);
    }

    static String channelId(String kind, String sound) {
        if (KIND_NAFAHAT.equals(kind)) {
            return CHANNEL_NAFAHAT;
        }
        String resolvedSound = sound == null ? SalatiSettings.SOUND_ADHAN_SECOND : sound;
        return CHANNEL_ADHAN + "_" + resolvedSound.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static void setAlarm(AlarmManager alarmManager, long when, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, when, pendingIntent);
            }
        } catch (SecurityException exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, when, pendingIntent);
        }
    }

    private static List<Event> upcomingEvents(Context context) {
        Date now = new Date();
        List<Event> events = new ArrayList<>();
        if (SalatiSettings.adhanEnabled(context)) {
            Set<String> enabledPrayerIDs = SalatiSettings.enabledPrayerIDs(context);
            for (String dateKey : PrayerEngine.availableDateKeys()) {
                DaySchedule schedule = PrayerEngine.schedule(dateKey);
                for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
                    if (!enabledPrayerIDs.contains(key.rawValue)) {
                        continue;
                    }
                    String time = schedule.times.get(key);
                    if (time == null) {
                        continue;
                    }
                    PrayerTime prayer = new PrayerTime(key, time, PrayerEngine.date(dateKey, time));
                    if (prayer.date.after(now)) {
                        events.add(Event.adhan(prayer, SalatiSettings.adhanSound(context)));
                    }
                }
            }
        }
        events.addAll(nafahatEvents(context, now));
        events.sort((left, right) -> left.when.compareTo(right.when));
        return events;
    }

    private static List<Event> nafahatEvents(Context context, Date now) {
        List<Event> events = new ArrayList<>();
        if (!SalatiSettings.nafahatEnabled(context)) {
            return events;
        }

        int interval = SalatiSettings.nafahatInterval(context);
        String textType = SalatiSettings.nafahatText(context);
        Calendar calendar = Calendar.getInstance(PrayerEngine.TIME_ZONE);
        calendar.setTime(now);
        calendar.add(Calendar.MINUTE, interval);
        Date end = new Date(now.getTime() + 3L * 24L * 60L * 60L * 1000L);
        int index = 0;

        while (calendar.getTime().before(end) && events.size() < 24) {
            Date date = calendar.getTime();
            if (!isWithinQuietWindow(context, date) && !isNearPrayerTime(date)) {
                events.add(Event.nafahat(date, NafahatContent.message(textType, index, date)));
                index++;
            }
            calendar.add(Calendar.MINUTE, interval);
        }
        return events;
    }

    private static boolean isWithinQuietWindow(Context context, Date date) {
        String quiet = SalatiSettings.quietWindow(context);
        if ("none".equals(quiet)) {
            return false;
        }
        Calendar calendar = Calendar.getInstance(PrayerEngine.TIME_ZONE);
        calendar.setTime(date);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if ("midnight".equals(quiet)) {
            return hour >= 0 && hour < 7;
        }
        return hour >= 23 || hour < 6;
    }

    private static boolean isNearPrayerTime(Date date) {
        String dateKey = PrayerEngine.defaultDateKey(date);
        DaySchedule schedule = PrayerEngine.schedule(dateKey);
        for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
            String time = schedule.times.get(key);
            if (time == null) {
                continue;
            }
            Date prayerDate = PrayerEngine.date(dateKey, time);
            if (Math.abs(date.getTime() - prayerDate.getTime()) <= 10L * 60L * 1000L) {
                return true;
            }
        }
        return false;
    }

    private static final class Event {
        final String kind;
        final String title;
        final String body;
        final String sound;
        final Date when;
        final int requestCode;

        Event(String kind, String title, String body, String sound, Date when, int requestCode) {
            this.kind = kind;
            this.title = title;
            this.body = body;
            this.sound = sound;
            this.when = when;
            this.requestCode = requestCode;
        }

        static Event adhan(PrayerTime prayer, String sound) {
            return new Event(
                    KIND_ADHAN,
                    "حان وقت صلاة " + prayer.title,
                    "صلاتي • " + prayer.time,
                    sound,
                    prayer.date,
                    42000 + Math.abs((PrayerEngine.calendarIdentifier(prayer.date) + prayer.key.rawValue).hashCode() % 160)
            );
        }

        static Event nafahat(Date date, NafahatContent.Message message) {
            return new Event(
                    KIND_NAFAHAT,
                    message.title,
                    message.body,
                    "nafahat2",
                    date,
                    42160 + Math.abs(PrayerEngine.calendarIdentifier(date).hashCode() % 20)
            );
        }
    }
}
