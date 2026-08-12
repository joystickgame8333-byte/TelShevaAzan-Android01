package com.example.telshevaazan;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class TodayPrayerWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SalatiWidgetUpdater.updateTodayPrayers(context, appWidgetManager, appWidgetIds);
        SalatiWidgetUpdater.scheduleNextMinute(context);
    }
}
