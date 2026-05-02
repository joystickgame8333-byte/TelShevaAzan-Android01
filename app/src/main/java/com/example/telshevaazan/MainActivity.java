package com.example.telshevaazan;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity {
    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Asia/Jerusalem");
    private static final int TEL_SHEVA_OFFSET_MINUTES = 2;
    private static final int DAYLIGHT_SAVING_OFFSET_MINUTES = 60;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Map<String, String>> schedule = new LinkedHashMap<>();
    private final String[] prayerOrder = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private final String[] displayOrder = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};

    private TextView dateLabel;
    private TextView nextPrayerName;
    private TextView nextPrayerTime;
    private TextView countdownLabel;
    private TextView noteLabel;
    private LinearLayout timeList;
    private Button previousButton;
    private Button todayButton;
    private Button nextButton;

    private String selectedDateKey;
    private boolean followsToday = true;
    private Runnable ticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buildSchedule();
        selectedDateKey = defaultDateKey();
        setContentView(createContent());
        updateView();

        ticker = new Runnable() {
            @Override
            public void run() {
                updateView();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View createContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(244, 240, 232));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView eyebrow = label("نموذج Android", 13, Color.rgb(10, 79, 73), Typeface.BOLD);
        root.addView(eyebrow);

        TextView title = label("أذان تل السبع", 40, Color.rgb(23, 32, 29), Typeface.BOLD);
        title.setPadding(0, dp(2), 0, dp(6));
        root.addView(title);

        TextView subtitle = label("يعرض اليوم تلقائيًا حسب التوقيت الدهري للمسجد الأقصى مع فرق بئر السبع.", 14, Color.rgb(97, 112, 107), Typeface.NORMAL);
        subtitle.setPadding(0, 0, 0, dp(14));
        root.addView(subtitle);

        dateLabel = pill("--");
        root.addView(dateLabel);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.RIGHT);
        controls.setPadding(0, dp(12), 0, dp(14));
        root.addView(controls);

        nextButton = smallButton("اليوم التالي");
        nextButton.setOnClickListener(v -> moveDay(1));
        controls.addView(nextButton);

        todayButton = smallButton("اليوم");
        todayButton.setOnClickListener(v -> {
            selectedDateKey = defaultDateKey();
            followsToday = true;
            updateView();
        });
        controls.addView(todayButton);

        previousButton = smallButton("اليوم السابق");
        previousButton.setOnClickListener(v -> moveDay(-1));
        controls.addView(previousButton);

        LinearLayout nextPanel = card();
        nextPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(nextPanel);

        nextPanel.addView(label("الصلاة القادمة", 13, Color.rgb(10, 79, 73), Typeface.BOLD));

        nextPrayerName = label("--", 42, Color.rgb(23, 32, 29), Typeface.BOLD);
        nextPrayerName.setPadding(0, dp(6), 0, 0);
        nextPanel.addView(nextPrayerName);

        nextPrayerTime = label("--:--", 56, Color.rgb(15, 118, 110), Typeface.BOLD);
        nextPrayerTime.setPadding(0, dp(2), 0, dp(10));
        nextPanel.addView(nextPrayerTime);

        countdownLabel = label("--:--:--", 24, Color.WHITE, Typeface.BOLD);
        countdownLabel.setGravity(Gravity.CENTER);
        countdownLabel.setPadding(dp(16), dp(8), dp(16), dp(8));
        countdownLabel.setBackground(round(Color.rgb(10, 79, 73), dp(8), 0));
        nextPanel.addView(countdownLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        noteLabel = label("القدس الدهري + دقيقتين لتل السبع + التوقيت الصيفي", 13, Color.rgb(97, 112, 107), Typeface.NORMAL);
        noteLabel.setPadding(0, dp(12), 0, 0);
        nextPanel.addView(noteLabel);

        TextView listTitle = label("مواقيت اليوم", 20, Color.rgb(23, 32, 29), Typeface.BOLD);
        listTitle.setPadding(0, dp(18), 0, dp(10));
        root.addView(listTitle);

        timeList = new LinearLayout(this);
        timeList.setOrientation(LinearLayout.VERTICAL);
        root.addView(timeList);

        return scrollView;
    }

    private void updateView() {
        String today = currentDateKey();
        if (followsToday && schedule.containsKey(today)) {
            selectedDateKey = today;
        }

        PrayerEvent next = nextPrayer(new Date());
        dateLabel.setText(formatLongDate(selectedDateKey));
        nextPrayerName.setText(next == null ? "--" : nameFor(next.key));
        nextPrayerTime.setText(next == null ? "--:--" : next.time);

        if (next == null) {
            countdownLabel.setText("--:--:--");
        } else if (isPastDate(selectedDateKey)) {
            countdownLabel.setText("تاريخ سابق");
        } else {
            countdownLabel.setText(formatDuration(next.date.getTime() - System.currentTimeMillis()));
        }

        noteLabel.setText(selectedDateKey.equals(today)
                ? "يعرض اليوم تلقائيًا ويتبدل عند منتصف الليل"
                : "عرض تاريخ محدد للمراجعة");

        renderTimes(next == null ? "" : next.key);
        updateButtons();
    }

    private void renderTimes(String activeKey) {
        timeList.removeAllViews();
        Map<String, String> times = schedule.get(selectedDateKey);
        if (times == null) return;

        for (String key : displayOrder) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setBackground(round(key.equals(activeKey) ? Color.rgb(237, 248, 244) : Color.WHITE, dp(8), key.equals(activeKey) ? Color.rgb(15, 118, 110) : Color.rgb(221, 213, 200)));

            TextView time = label(times.get(key), 22, key.equals(activeKey) ? Color.rgb(15, 118, 110) : Color.rgb(23, 32, 29), Typeface.BOLD);
            row.addView(time);

            TextView name = label(nameFor(key), 18, key.equals(activeKey) ? Color.rgb(15, 118, 110) : Color.rgb(97, 112, 107), Typeface.BOLD);
            name.setGravity(Gravity.RIGHT);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            timeList.addView(row, params);
        }
    }

    private PrayerEvent nextPrayer(Date now) {
        Map<String, String> times = schedule.get(selectedDateKey);
        if (times == null) return null;

        for (String key : prayerOrder) {
            Date date = parseDate(selectedDateKey, times.get(key));
            if (date == null) continue;
            if (!selectedDateKey.equals(currentDateKey()) || date.after(now)) {
                return new PrayerEvent(key, times.get(key), date);
            }
        }

        String nextDate = adjacentDate(1);
        if (nextDate != null) {
            Map<String, String> nextTimes = schedule.get(nextDate);
            if (nextTimes != null) {
                return new PrayerEvent("fajr", nextTimes.get("fajr"), parseDate(nextDate, nextTimes.get("fajr")));
            }
        }

        return new PrayerEvent("fajr", times.get("fajr"), parseDate(selectedDateKey, times.get("fajr")));
    }

    private void moveDay(int offset) {
        String nextDate = adjacentDate(offset);
        if (nextDate == null) return;
        selectedDateKey = nextDate;
        followsToday = false;
        updateView();
    }

    private String adjacentDate(int offset) {
        List<String> dates = new ArrayList<>(schedule.keySet());
        int index = dates.indexOf(selectedDateKey);
        int nextIndex = index + offset;
        if (nextIndex < 0 || nextIndex >= dates.size()) return null;
        return dates.get(nextIndex);
    }

    private void updateButtons() {
        previousButton.setEnabled(adjacentDate(-1) != null);
        nextButton.setEnabled(adjacentDate(1) != null);
    }

    private String defaultDateKey() {
        String today = currentDateKey();
        return schedule.containsKey(today) ? today : new ArrayList<>(schedule.keySet()).get(0);
    }

    private String currentDateKey() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setTimeZone(TIME_ZONE);
        return formatter.format(new Date());
    }

    private Date parseDate(String dateKey, String time) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        formatter.setTimeZone(TIME_ZONE);
        try {
            return formatter.parse(dateKey + " " + time);
        } catch (ParseException exception) {
            return null;
        }
    }

    private String formatLongDate(String dateKey) {
        Date date = parseDate(dateKey, "12:00");
        if (date == null) return dateKey;

        SimpleDateFormat formatter = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar"));
        formatter.setTimeZone(TIME_ZONE);
        return formatter.format(date);
    }

    private boolean isPastDate(String dateKey) {
        Date endOfDay = parseDate(dateKey, "23:59");
        return endOfDay != null && endOfDay.before(new Date());
    }

    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) return "--:--:--";
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String nameFor(String key) {
        switch (key) {
            case "fajr":
                return "الفجر";
            case "sunrise":
                return "الشروق";
            case "dhuhr":
                return "الظهر";
            case "asr":
                return "العصر";
            case "maghrib":
                return "المغرب";
            case "isha":
                return "العشاء";
            default:
                return key;
        }
    }

    private void buildSchedule() {
        addDay("2026-05-01", "03:22", "04:50", "11:36", "15:15", "18:26", "19:49");
        addDay("2026-05-02", "03:21", "04:49", "11:36", "15:15", "18:27", "19:50");
        addDay("2026-05-03", "03:20", "04:48", "11:35", "15:15", "18:27", "19:51");
        addDay("2026-05-04", "03:19", "04:47", "11:35", "15:15", "18:28", "19:52");
        addDay("2026-05-05", "03:18", "04:46", "11:35", "15:15", "18:29", "19:53");
        addDay("2026-05-06", "03:16", "04:45", "11:35", "15:15", "18:29", "19:54");
        addDay("2026-05-07", "03:15", "04:44", "11:35", "15:15", "18:30", "19:55");
        addDay("2026-05-08", "03:14", "04:43", "11:35", "15:15", "18:31", "19:56");
        addDay("2026-05-09", "03:13", "04:43", "11:35", "15:15", "18:31", "19:57");
        addDay("2026-05-10", "03:12", "04:42", "11:35", "15:15", "18:32", "19:58");
        addDay("2026-05-11", "03:11", "04:41", "11:35", "15:15", "18:33", "19:59");
        addDay("2026-05-12", "03:10", "04:40", "11:35", "15:15", "18:34", "20:00");
        addDay("2026-05-13", "03:09", "04:40", "11:35", "15:15", "18:34", "20:01");
        addDay("2026-05-14", "03:08", "04:39", "11:35", "15:15", "18:35", "20:02");
        addDay("2026-05-15", "03:07", "04:38", "11:35", "15:15", "18:36", "20:03");
        addDay("2026-05-16", "03:06", "04:38", "11:35", "15:15", "18:36", "20:04");
        addDay("2026-05-17", "03:06", "04:37", "11:35", "15:15", "18:37", "20:04");
        addDay("2026-05-18", "03:05", "04:37", "11:35", "15:15", "18:37", "20:05");
        addDay("2026-05-19", "03:04", "04:36", "11:35", "15:16", "18:38", "20:06");
        addDay("2026-05-20", "03:03", "04:35", "11:35", "15:16", "18:39", "20:07");
        addDay("2026-05-21", "03:02", "04:35", "11:35", "15:16", "18:39", "20:08");
        addDay("2026-05-22", "03:02", "04:34", "11:35", "15:16", "18:40", "20:09");
        addDay("2026-05-23", "03:01", "04:34", "11:35", "15:16", "18:41", "20:10");
        addDay("2026-05-24", "03:00", "04:34", "11:35", "15:16", "18:41", "20:11");
        addDay("2026-05-25", "02:59", "04:33", "11:36", "15:16", "18:42", "20:11");
        addDay("2026-05-26", "02:59", "04:33", "11:36", "15:16", "18:42", "20:12");
        addDay("2026-05-27", "02:58", "04:32", "11:36", "15:16", "18:43", "20:13");
        addDay("2026-05-28", "02:58", "04:32", "11:36", "15:16", "18:44", "20:14");
        addDay("2026-05-29", "02:57", "04:32", "11:36", "15:16", "18:44", "20:15");
        addDay("2026-05-30", "02:57", "04:31", "11:36", "15:16", "18:45", "20:15");
        addDay("2026-05-31", "02:56", "04:31", "11:36", "15:17", "18:45", "20:16");
    }

    private void addDay(String date, String fajr, String sunrise, String dhuhr, String asr, String maghrib, String isha) {
        int offset = TEL_SHEVA_OFFSET_MINUTES + DAYLIGHT_SAVING_OFFSET_MINUTES;
        Map<String, String> times = new LinkedHashMap<>();
        times.put("fajr", addMinutes(fajr, offset));
        times.put("sunrise", addMinutes(sunrise, offset));
        times.put("dhuhr", addMinutes(dhuhr, offset));
        times.put("asr", addMinutes(asr, offset));
        times.put("maghrib", addMinutes(maghrib, offset));
        times.put("isha", addMinutes(isha, offset));
        schedule.put(date, times);
    }

    private String addMinutes(String time, int minutesToAdd) {
        String[] parts = time.split(":");
        int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]) + minutesToAdd;
        total = (total + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setGravity(Gravity.RIGHT);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private TextView pill(String text) {
        TextView textView = label(text, 14, Color.rgb(10, 79, 73), Typeface.BOLD);
        textView.setPadding(dp(12), dp(8), dp(12), dp(8));
        textView.setBackground(round(Color.rgb(232, 243, 240), dp(30), 0));
        return textView;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(23, 32, 29));
        button.setBackground(round(Color.rgb(255, 249, 239), dp(8), Color.rgb(221, 213, 200)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(dp(5), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(round(Color.WHITE, dp(8), Color.rgb(221, 213, 200)));
        return layout;
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class PrayerEvent {
        final String key;
        final String time;
        final Date date;

        PrayerEvent(String key, String time, Date date) {
            this.key = key;
            this.time = time;
            this.date = date;
        }
    }
}
