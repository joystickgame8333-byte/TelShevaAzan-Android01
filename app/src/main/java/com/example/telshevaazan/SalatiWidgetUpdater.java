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
        scheduleNextMinute(context);
    }

    static void updateNextPrayer(Context context, AppWidgetManager manager, int[] ids) {
        Date now = new Date();
        String dateKey = PrayerEngine.defaultDateKey(now);
        PrayerTime next = PrayerEngine.nextPrayer(dateKey, now);
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
        String dateKey = PrayerEngine.defaultDateKey(now);
        DaySchedule schedule = PrayerEngine.schedule(dateKey);
        PrayerTime next = PrayerEngine.nextPrayer(dateKey, now);
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
