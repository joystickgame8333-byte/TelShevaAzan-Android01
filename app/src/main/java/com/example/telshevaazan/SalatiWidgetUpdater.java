package com.example.telshevaazan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Date;

final class SalatiWidgetUpdater {
    static final String ACTION_UPDATE_WIDGETS = "com.example.telshevaazan.ACTION_UPDATE_WIDGETS";
    static final String KIND_FAJR = "fajr";
    static final String KIND_NEXT = "next";
    static final String KIND_IQAMA_MINUTES = "iqama_minutes";
    static final String KIND_IQAMA_TIME = "iqama_time";
    static final String KIND_SUNRISE = "sunrise";

    private SalatiWidgetUpdater() {}

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateProvider(context, manager, NextPrayerWidgetProvider.class, null);
        updateProvider(context, manager, FajrCircleWidgetProvider.class, KIND_FAJR);
        updateProvider(context, manager, NextCountdownCircleWidgetProvider.class, KIND_NEXT);
        updateProvider(context, manager, IqamaMinutesCircleWidgetProvider.class, KIND_IQAMA_MINUTES);
        updateProvider(context, manager, IqamaTimeCircleWidgetProvider.class, KIND_IQAMA_TIME);
        updateProvider(context, manager, SunriseCircleWidgetProvider.class, KIND_SUNRISE);
        updateTodayProvider(context, manager);
        updatePathProvider(context, manager);
        scheduleNextMinute(context);
    }

    static void updateNextPrayer(Context context, AppWidgetManager manager, int[] ids) {
        Date now = new Date();
        String todayKey = PrayerEngine.defaultDateKey(now);
        PrayerTime next = PrayerEngine.nextPrayer(todayKey, now);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prayer_medium);
            if (next != null) {
                views.setTextViewText(R.id.widget_title, "الصلاة القادمة");
                views.setTextViewText(R.id.widget_value, next.title);
                views.setTextViewText(R.id.widget_time, next.time);
                views.setTextViewText(R.id.widget_footer, "متبقي " + PrayerEngine.shortMinuteDuration(next.date.getTime() - now.getTime()));
            }
            manager.updateAppWidget(id, views);
        }
    }

    static void updateCircle(Context context, AppWidgetManager manager, int[] ids, String kind) {
        Date now = new Date();
        String todayKey = PrayerEngine.defaultDateKey(now);
        String displayDateKey = PrayerEngine.automaticScheduleDateKey(now);
        DaySchedule schedule = PrayerEngine.schedule(displayDateKey);
        PrayerTime next = PrayerEngine.nextPrayer(todayKey, now);
        PrayerTime fajr = find(schedule, PrayerKey.FAJR);
        PrayerTime sunrise = find(schedule, PrayerKey.SUNRISE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_circle);
            if (KIND_FAJR.equals(kind) && fajr != null) {
                setCircle(views, "الفجر", fajr.time, "أذان");
            } else if (KIND_NEXT.equals(kind) && next != null) {
                setCircle(views, next.title, PrayerEngine.shortMinuteDuration(next.date.getTime() - now.getTime()), "متبقي");
            } else if (KIND_IQAMA_MINUTES.equals(kind) && next != null) {
                setCircle(views, "الإقامة", PrayerEngine.iqamaDelayMinutes(next.key) + "د", next.title);
            } else if (KIND_IQAMA_TIME.equals(kind) && next != null) {
                setCircle(views, next.title, PrayerEngine.iqamaTime(next), "الإقامة");
            } else if (KIND_SUNRISE.equals(kind) && sunrise != null) {
                setCircle(views, "الشروق", sunrise.time, "تل السبع");
            }
            manager.updateAppWidget(id, views);
        }
    }

    static void updateTodayPrayers(Context context, AppWidgetManager manager, int[] ids) {
        DaySchedule schedule = PrayerEngine.schedule(PrayerEngine.automaticScheduleDateKey(new Date()));
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_prayers);
            views.setTextViewText(R.id.widget_today_date, PrayerEngine.longDateLabel(schedule.dateKey));
            setPrayerLine(views, R.id.widget_today_fajr, schedule, PrayerKey.FAJR);
            setPrayerLine(views, R.id.widget_today_dhuhr, schedule, PrayerKey.DHUHR);
            setPrayerLine(views, R.id.widget_today_asr, schedule, PrayerKey.ASR);
            setPrayerLine(views, R.id.widget_today_maghrib, schedule, PrayerKey.MAGHRIB);
            setPrayerLine(views, R.id.widget_today_isha, schedule, PrayerKey.ISHA);
            manager.updateAppWidget(id, views);
        }
    }

    static void updatePrayerPath(Context context, AppWidgetManager manager, int[] ids) {
        Date now = new Date();
        String displayKey = PrayerEngine.automaticScheduleDateKey(now);
        DaySchedule schedule = PrayerEngine.schedule(displayKey);
        PrayerTime next = PrayerEngine.nextPrayer(displayKey, now);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prayer_path);
            setPathLine(views, R.id.path_fajr, schedule, PrayerKey.FAJR, next);
            setPathLine(views, R.id.path_dhuhr, schedule, PrayerKey.DHUHR, next);
            setPathLine(views, R.id.path_asr, schedule, PrayerKey.ASR, next);
            setPathLine(views, R.id.path_maghrib, schedule, PrayerKey.MAGHRIB, next);
            setPathLine(views, R.id.path_isha, schedule, PrayerKey.ISHA, next);
            manager.updateAppWidget(id, views);
        }
    }

    static void scheduleNextMinute(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long nextMinute = now - (now % 60000L) + 60000L;
        Intent intent = new Intent(context, WidgetUpdateReceiver.class);
        intent.setAction(ACTION_UPDATE_WIDGETS);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                51000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC, nextMinute, pendingIntent);
            }
        } catch (SecurityException exception) {
            alarmManager.set(AlarmManager.RTC, nextMinute, pendingIntent);
        }
    }

    private static void updateProvider(Context context, AppWidgetManager manager, Class<?> provider, String kind) {
        ComponentName componentName = new ComponentName(context, provider);
        int[] ids = manager.getAppWidgetIds(componentName);
        if (ids == null || ids.length == 0) {
            return;
        }
        if (kind == null) {
            updateNextPrayer(context, manager, ids);
        } else {
            updateCircle(context, manager, ids, kind);
        }
    }

    private static void updateTodayProvider(Context context, AppWidgetManager manager) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodayPrayerWidgetProvider.class));
        if (ids != null && ids.length > 0) updateTodayPrayers(context, manager, ids);
    }

    private static void updatePathProvider(Context context, AppWidgetManager manager) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerPathWidgetProvider.class));
        if (ids != null && ids.length > 0) updatePrayerPath(context, manager, ids);
    }

    private static void setPrayerLine(RemoteViews views, int viewId, DaySchedule schedule, PrayerKey key) {
        String time = schedule.times.get(key);
        views.setTextViewText(viewId, key.title + "   " + (time == null ? "--:--" : time));
    }

    private static void setPathLine(RemoteViews views, int viewId, DaySchedule schedule, PrayerKey key, PrayerTime next) {
        String time = schedule.times.get(key);
        boolean active = next != null && next.key == key;
        views.setTextViewText(viewId, (active ? "● " : "") + key.title + "\n" + (time == null ? "--:--" : time));
        views.setTextColor(viewId, active ? 0xFF168CFF : 0xFFFFFFFF);
    }

    private static PrayerTime find(DaySchedule schedule, PrayerKey key) {
        for (PrayerTime time : schedule.displayTimes()) {
            if (time.key == key) {
                return time;
            }
        }
        return null;
    }

    private static void setCircle(RemoteViews views, String title, String value, String footer) {
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_value, value);
        views.setTextViewText(R.id.widget_footer, footer);
    }
}
