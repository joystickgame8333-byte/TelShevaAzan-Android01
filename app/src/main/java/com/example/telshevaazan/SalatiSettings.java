package com.example.telshevaazan;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class SalatiSettings {
    static final String KEY_ADHAN_ENABLED = "prayer_notifications_enabled";
    static final String KEY_ENABLED_PRAYERS = "prayer_notifications_enabled_prayers";
    static final String KEY_ADHAN_SOUND = "prayer_notifications_selected_sound";
    static final String KEY_NAFAHAT_ENABLED = "prayer_notifications_nafahat_enabled";
    static final String KEY_NAFAHAT_INTERVAL = "prayer_notifications_nafahat_interval_minutes";
    static final String KEY_NAFAHAT_TEXT = "prayer_notifications_nafahat_text";
    static final String KEY_NAFAHAT_QUIET = "prayer_notifications_nafahat_quiet_window";
    static final String KEY_NAFAHAT_SOUND = "prayer_notifications_nafahat_sound";
    static final String SOUND_ADHAN = "mohamedJaziAdhan";
    static final String SOUND_ADHAN_SECOND = "bundledAdhan";
    static final String SOUND_ADHAN_FIRST = "originalAdhan";
    static final String SOUND_NAFAHAT_1 = "nafahat1";
    static final String SOUND_NAFAHAT_2 = "nafahat2";
    static final String SOUND_NAFAHAT_3 = "nafahat3";
    static final String SOUND_NAFAHAT_4 = "nafahat4";
    static final String SOUND_SOFT = "softDhikr";
    static final String SOUND_SYSTEM = "system";
    static final String TEXT_MIXED = "mixed";
    static final String QUIET_LATE_NIGHT = "lateNight";

    private SalatiSettings() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AppTheme.PREFS, Context.MODE_PRIVATE);
    }

    static boolean adhanEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ADHAN_ENABLED, false);
    }

    static boolean nafahatEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NAFAHAT_ENABLED, false);
    }

    static int nafahatInterval(Context context) {
        int value = prefs(context).getInt(KEY_NAFAHAT_INTERVAL, 120);
        return value == 0 ? 120 : value;
    }

    static String nafahatText(Context context) {
        String value = prefs(context).getString(KEY_NAFAHAT_TEXT, TEXT_MIXED);
        if ("protection".equals(value) || "gratitude".equals(value) || "quran".equals(value)
                || "lightReminders".equals(value) || TEXT_MIXED.equals(value)) {
            return value;
        }
        return TEXT_MIXED;
    }

    static String quietWindow(Context context) {
        return prefs(context).getString(KEY_NAFAHAT_QUIET, QUIET_LATE_NIGHT);
    }

    static String nafahatSound(Context context) {
        String value = prefs(context).getString(KEY_NAFAHAT_SOUND, SOUND_NAFAHAT_1);
        if (SOUND_NAFAHAT_1.equals(value) || SOUND_NAFAHAT_2.equals(value)
                || SOUND_NAFAHAT_3.equals(value) || SOUND_NAFAHAT_4.equals(value)
                || SOUND_SYSTEM.equals(value)) {
            return value;
        }
        return SOUND_NAFAHAT_1;
    }

    static String adhanSound(Context context) {
        String value = prefs(context).getString(KEY_ADHAN_SOUND, SOUND_ADHAN);
        if (SOUND_ADHAN_FIRST.equals(value) || SOUND_ADHAN_SECOND.equals(value)) {
            return SOUND_ADHAN;
        }
        return value;
    }

    static Set<String> enabledPrayerIDs(Context context) {
        Set<String> defaults = new HashSet<>();
        for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
            defaults.add(key.rawValue);
        }
        return new HashSet<>(prefs(context).getStringSet(KEY_ENABLED_PRAYERS, defaults));
    }

    static boolean prayerEnabled(Context context, PrayerKey key) {
        return enabledPrayerIDs(context).contains(key.rawValue);
    }

    static void setPrayerEnabled(Context context, PrayerKey key, boolean enabled) {
        Set<String> values = enabledPrayerIDs(context);
        if (enabled) {
            values.add(key.rawValue);
        } else {
            values.remove(key.rawValue);
        }
        prefs(context).edit().putStringSet(KEY_ENABLED_PRAYERS, values).apply();
    }

    static void ensureWelcomeDefaults(Context context) {
        Set<String> prayerIDs = new HashSet<>();
        for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
            prayerIDs.add(key.rawValue);
        }
        prefs(context).edit()
                .putBoolean(KEY_ADHAN_ENABLED, true)
                .putStringSet(KEY_ENABLED_PRAYERS, prayerIDs)
                .putBoolean(KEY_NAFAHAT_ENABLED, true)
                .putInt(KEY_NAFAHAT_INTERVAL, 120)
                .putString(KEY_NAFAHAT_TEXT, TEXT_MIXED)
                .putString(KEY_NAFAHAT_QUIET, QUIET_LATE_NIGHT)
                .putString(KEY_NAFAHAT_SOUND, SOUND_NAFAHAT_1)
                .apply();
    }
}
