package com.example.telshevaazan;

import android.app.Application;

public final class SalatiApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PalestinePrayerCalendar.initialize(this);
    }
}
