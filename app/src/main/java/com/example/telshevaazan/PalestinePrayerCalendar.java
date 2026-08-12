package com.example.telshevaazan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The official perpetual Palestine prayer calendar used by the iOS app.
 *
 * Source times are Jerusalem winter-standard times. Tel Sheva's two-minute
 * offset and the real daylight-saving offset for each concrete date are added
 * only when a schedule is requested.
 */
final class PalestinePrayerCalendar {
    private static final String ASSET_NAME = "prayer-calendar-v1.json";
    private static final String CITY_KEY = "telSheva";
    private static final String PREFS_NAME = "palestine_prayer_calendar";
    private static final String CACHE_KEY = "remote_payload_v1";
    private static final String LAST_REFRESH_KEY = "last_refresh_v1";
    private static final long REFRESH_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;
    private static final String REMOTE_URL =
            "https://raw.githubusercontent.com/joystickgame8333-byte/TelShevaAzan-iOS01/main/"
                    + "TelShevaAzan/Resources/PrayerCalendar/prayer-calendar-v1.json";

    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile Payload activePayload;

    private PalestinePrayerCalendar() {}

    static void initialize(Context context) {
        if (activePayload != null) {
            return;
        }
        synchronized (LOCK) {
            if (activePayload != null) {
                return;
            }

            Context appContext = context.getApplicationContext();
            Payload bundled = loadBundled(appContext);
            Payload cached = loadCached(appContext);
            if (cached != null && (bundled == null || cached.revision >= bundled.revision)) {
                activePayload = cached;
            } else {
                activePayload = bundled;
            }
        }
    }

    static Map<PrayerKey, String> schedule(Date date) {
        Payload payload = activePayload;
        if (payload == null || date == null) {
            return Collections.emptyMap();
        }

        Calendar calendar = Calendar.getInstance(PrayerEngine.TIME_ZONE, Locale.US);
        calendar.setTime(date);
        String recurringKey = String.format(
                Locale.US,
                "%02d-%02d",
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        Map<PrayerKey, String> sourceDay = payload.days.get(recurringKey);
        if (sourceDay == null) {
            return Collections.emptyMap();
        }

        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int daylightSavingOffset = PrayerEngine.TIME_ZONE.inDaylightTime(calendar.getTime()) ? 60 : 0;
        int totalOffset = payload.telShevaOffsetMinutes + daylightSavingOffset;

        Map<PrayerKey, String> result = new EnumMap<>(PrayerKey.class);
        for (PrayerKey key : PrayerEngine.DISPLAY_ORDER) {
            String sourceTime = sourceDay.get(key);
            if (sourceTime != null) {
                result.put(key, offset(sourceTime, totalOffset));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static int dataRevision() {
        Payload payload = activePayload;
        return payload == null ? 0 : payload.revision;
    }

    static void refreshRemoteIfNeeded(Context context, RefreshCallback callback) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0L);
        if (now - lastRefresh < REFRESH_INTERVAL_MILLIS) {
            callback.onComplete(false);
            return;
        }

        prefs.edit().putLong(LAST_REFRESH_KEY, now).apply();
        EXECUTOR.execute(() -> {
            boolean updated = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    String json = readString(connection.getInputStream());
                    Payload remote = decode(json);
                    synchronized (LOCK) {
                        if (remote != null && (activePayload == null || remote.revision > activePayload.revision)) {
                            activePayload = remote;
                            prefs.edit().putString(CACHE_KEY, json).apply();
                            updated = true;
                        }
                    }
                }
            } catch (IOException ignored) {
                // The bundled perpetual calendar remains fully functional offline.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            boolean didUpdate = updated;
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(didUpdate));
        });
    }

    private static Payload loadBundled(Context context) {
        try (InputStream stream = context.getAssets().open(ASSET_NAME)) {
            return decode(readString(stream));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Payload loadCached(Context context) {
        String json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(CACHE_KEY, null);
        return json == null ? null : decode(json);
    }

    private static Payload decode(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schemaVersion", 0) != 1
                    || root.optInt("revision", 0) <= 0
                    || !"jerusalem".equals(root.optString("baseLocation"))
                    || !"winter".equals(root.optString("baseTimeStandard"))) {
                return null;
            }

            JSONObject offsets = root.getJSONObject("cityOffsetsMinutes");
            int telShevaOffset = offsets.getInt(CITY_KEY);
            if (telShevaOffset != 2) {
                return null;
            }

            JSONObject daysObject = root.getJSONObject("days");
            Set<String> expectedKeys = expectedDateKeys();
            Set<String> actualKeys = new HashSet<>();
            Map<String, Map<PrayerKey, String>> days = new HashMap<>();

            java.util.Iterator<String> keys = daysObject.keys();
            while (keys.hasNext()) {
                String dateKey = keys.next();
                actualKeys.add(dateKey);
                JSONObject dayObject = daysObject.getJSONObject(dateKey);
                Map<PrayerKey, String> day = new EnumMap<>(PrayerKey.class);
                for (PrayerKey key : PrayerEngine.DISPLAY_ORDER) {
                    String time = dayObject.getString(key.rawValue);
                    if (!isValidTime(time)) {
                        return null;
                    }
                    day.put(key, time);
                }
                days.put(dateKey, Collections.unmodifiableMap(day));
            }

            if (!actualKeys.equals(expectedKeys)) {
                return null;
            }
            return new Payload(
                    root.getInt("revision"),
                    telShevaOffset,
                    Collections.unmodifiableMap(days)
            );
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static Set<String> expectedDateKeys() {
        int[] monthLengths = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        Set<String> keys = new HashSet<>();
        for (int month = 1; month <= monthLengths.length; month++) {
            for (int day = 1; day <= monthLengths[month - 1]; day++) {
                keys.add(String.format(Locale.US, "%02d-%02d", month, day));
            }
        }
        return keys;
    }

    private static boolean isValidTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return false;
        }
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour >= 0 && hour < 24 && minute >= 0 && minute < 60;
    }

    private static String offset(String time, int minutes) {
        String[] parts = time.split(":");
        int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]) + minutes;
        total = (total % 1440 + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private static String readString(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, count);
            }
            return builder.toString();
        }
    }

    interface RefreshCallback {
        void onComplete(boolean didUpdate);
    }

    private static final class Payload {
        final int revision;
        final int telShevaOffsetMinutes;
        final Map<String, Map<PrayerKey, String>> days;

        Payload(int revision, int telShevaOffsetMinutes, Map<String, Map<PrayerKey, String>> days) {
            this.revision = revision;
            this.telShevaOffsetMinutes = telShevaOffsetMinutes;
            this.days = days;
        }
    }
}
