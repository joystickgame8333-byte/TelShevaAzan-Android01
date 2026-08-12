package com.example.telshevaazan;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class AdhkarProgressStore {
    private static final String PREFS = "salati_adhkar_progress";
    private static final String KEY_DAY = "day";
    private final SharedPreferences prefs;

    AdhkarProgressStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        refreshDay();
    }

    private void refreshDay() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(PrayerEngine.TIME_ZONE);
        String day = format.format(new Date());
        if (!day.equals(prefs.getString(KEY_DAY, ""))) {
            prefs.edit().clear().putString(KEY_DAY, day).apply();
        }
    }

    int count(AdhkarLibrary.Item item) {
        refreshDay();
        return Math.min(item.target, Math.max(0, prefs.getInt("item_" + item.id, 0)));
    }

    int increment(AdhkarLibrary.Item item) {
        int value = Math.min(item.target, count(item) + 1);
        prefs.edit().putInt("item_" + item.id, value).apply();
        return value;
    }

    void decrement(AdhkarLibrary.Item item) {
        prefs.edit().putInt("item_" + item.id, Math.max(0, count(item) - 1)).apply();
    }

    int completed(AdhkarLibrary.Category category) {
        int count = 0;
        for (AdhkarLibrary.Item item : AdhkarLibrary.items(category)) {
            if (count(item) >= item.target) count++;
        }
        return count;
    }

    int totalCompleted() {
        int count = 0;
        for (AdhkarLibrary.Category category : AdhkarLibrary.Category.values()) count += completed(category);
        return count;
    }

    int totalItems() {
        int count = 0;
        for (AdhkarLibrary.Category category : AdhkarLibrary.Category.values()) count += AdhkarLibrary.items(category).size();
        return count;
    }
}
