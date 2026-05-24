package com.example.telshevaazan;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class IqamaMinutesCircleWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SalatiWidgetUpdater.updateCircle(context, appWidgetManager, appWidgetIds, SalatiWidgetUpdater.KIND_IQAMA_MINUTES);
        SalatiWidgetUpdater.scheduleNextMinute(context);
    }
}
