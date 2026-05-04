package com.example.telshevaazan;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
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
    private static final String APP_VERSION = "0.2.1";
    private static final String APP_BUILD = "3";
    private static final String PREFS_NAME = "tel_sheva_azan_android";
    private static final String NIGHT_THEME_KEY = "night_theme";
    private static final String DAY_THEME_KEY = "day_theme";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Map<String, String>> schedule = new LinkedHashMap<>();
    private final String[] prayerOrder = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    private final String[] displayOrder = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};

    private SharedPreferences preferences;
    private VisualTheme activeTheme;
    private TextView quranVerse;
    private TextView quranSource;
    private TextView versionLabel;
    private TextView titleLabel;
    private TextView dateLabel;
    private TextView nextPrayerCaption;
    private TextView nextPrayerName;
    private TextView nextPrayerTime;
    private TextView countdownLabel;
    private TextView elapsedLabel;
    private TextView noteLabel;
    private LinearLayout nextPanel;
    private LinearLayout timeList;
    private Button themeButton;
    private Button previousButton;
    private Button todayButton;
    private Button nextButton;

    private String selectedDateKey;
    private boolean followsToday = true;
    private Runnable ticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buildSchedule();
        selectedDateKey = defaultDateKey();
        rebuildContent();

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

    private void rebuildContent() {
        activeTheme = selectedTheme();
        applySystemBars();
        setContentView(createContent());
        updateView();
    }

    private View createContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(gradient(activeTheme.backgroundTop, activeTheme.backgroundBottom));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.RIGHT);
        root.setPadding(
                dp(16),
                dp(14) + systemBarHeight("status_bar_height"),
                dp(16),
                dp(22) + systemBarHeight("navigation_bar_height")
        );
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        quranVerse = label("إِنَّ ٱلصَّلَوٰةَ كَانَتْ عَلَى ٱلْمُؤْمِنِينَ كِتَـٰبًا مَّوْقُوتًا", 15, activeTheme.accent, Typeface.NORMAL);
        quranVerse.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        quranVerse.setSingleLine(false);
        quranVerse.setMaxLines(2);
        root.addView(quranVerse, fullWidth());

        quranSource = label("النساء ١٠٣", 12, activeTheme.secondary, Typeface.BOLD);
        root.addView(quranSource, fullWidth());

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, dp(10), 0, dp(2));
        root.addView(headerRow, fullWidth());

        themeButton = smallButton(activeTheme.modeTitle + " · " + activeTheme.title);
        themeButton.setOnClickListener(v -> showThemeDialog());
        headerRow.addView(themeButton);

        versionLabel = label("مواقيت محلية v" + APP_VERSION + " (" + APP_BUILD + ")", 12, activeTheme.accent, Typeface.BOLD);
        headerRow.addView(versionLabel, weightedWidth());

        titleLabel = label("أذان تل السبع", 40, activeTheme.primary, Typeface.BOLD);
        titleLabel.setPadding(0, dp(4), 0, 0);
        root.addView(titleLabel, fullWidth());

        dateLabel = label("--", 15, activeTheme.secondary, Typeface.BOLD);
        root.addView(dateLabel, fullWidth());

        nextPanel = new LinearLayout(this);
        nextPanel.setOrientation(LinearLayout.HORIZONTAL);
        nextPanel.setGravity(Gravity.CENTER_VERTICAL);
        nextPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        nextPanel.setBackground(round(activeTheme.panel, dp(8), activeTheme.border));
        LinearLayout.LayoutParams panelParams = fullWidth();
        panelParams.setMargins(0, dp(12), 0, dp(10));
        root.addView(nextPanel, panelParams);

        LinearLayout leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        leftPanel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nextPanel.addView(leftPanel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        countdownLabel = label("--:--:--", 21, Color.WHITE, Typeface.BOLD);
        countdownLabel.setGravity(Gravity.CENTER);
        countdownLabel.setPadding(dp(12), dp(8), dp(12), dp(8));
        countdownLabel.setBackground(round(activeTheme.countdown, dp(8), 0));
        leftPanel.addView(countdownLabel, wrapWidth());

        elapsedLabel = label("تتحدث تلقائيًا", 13, activeTheme.secondary, Typeface.BOLD);
        elapsedLabel.setPadding(0, dp(8), 0, 0);
        leftPanel.addView(elapsedLabel, fullWidth());

        LinearLayout rightPanel = new LinearLayout(this);
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        rightPanel.setGravity(Gravity.RIGHT);
        nextPanel.addView(rightPanel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        nextPrayerCaption = label("الصلاة القادمة", 13, activeTheme.accent, Typeface.BOLD);
        rightPanel.addView(nextPrayerCaption, fullWidth());

        nextPrayerName = label("--", 44, activeTheme.primary, Typeface.BOLD);
        rightPanel.addView(nextPrayerName, fullWidth());

        nextPrayerTime = label("--:--", 46, activeTheme.accent, Typeface.BOLD);
        nextPrayerTime.setTypeface(Typeface.DEFAULT_BOLD);
        rightPanel.addView(nextPrayerTime, fullWidth());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.RIGHT);
        controls.setPadding(0, 0, 0, dp(10));
        root.addView(controls, fullWidth());

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

        timeList = new LinearLayout(this);
        timeList.setOrientation(LinearLayout.VERTICAL);
        root.addView(timeList, fullWidth());

        noteLabel = label("مواقيت تل السبع المحلية · تتحدث تلقائيًا", 14, activeTheme.accent, Typeface.BOLD);
        noteLabel.setPadding(0, dp(8), 0, 0);
        root.addView(noteLabel, fullWidth());

        return scrollView;
    }

    private void updateView() {
        String today = currentDateKey();
        if (followsToday && schedule.containsKey(today)) {
            selectedDateKey = today;
        }

        Date now = new Date();
        PrayerEvent next = nextPrayer(now);
        PrayerEvent previous = selectedDateKey.equals(today) ? previousPrayer(now) : null;

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

        elapsedLabel.setText(selectedDateKey.equals(today) ? elapsedText(previous, now) : "عرض تاريخ محدد");
        noteLabel.setText(selectedDateKey.equals(today)
                ? "مواقيت تل السبع المحلية · تتحدث تلقائيًا"
                : "عرض تاريخ محدد للمراجعة");

        renderTimes(next == null ? "" : next.key);
        updateButtons();
    }

    private void renderTimes(String activeKey) {
        timeList.removeAllViews();
        Map<String, String> times = schedule.get(selectedDateKey);
        if (times == null) return;

        for (String key : displayOrder) {
            boolean active = key.equals(activeKey);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackground(round(active ? activeTheme.activeRow : activeTheme.row, dp(8), active ? activeTheme.activeBorder : activeTheme.rowBorder));

            TextView time = label(times.get(key), 24, active ? activeTheme.accent : activeTheme.primary, Typeface.BOLD);
            time.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(time);

            TextView name = label(nameFor(key), 22, active ? activeTheme.accent : activeTheme.secondary, Typeface.BOLD);
            row.addView(name, weightedWidth());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(58)
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

    private PrayerEvent previousPrayer(Date now) {
        Map<String, String> times = schedule.get(selectedDateKey);
        if (times == null) return null;

        PrayerEvent previous = null;
        for (String key : prayerOrder) {
            Date date = parseDate(selectedDateKey, times.get(key));
            if (date == null) continue;
            if (!selectedDateKey.equals(currentDateKey()) || !date.after(now)) {
                previous = new PrayerEvent(key, times.get(key), date);
            }
        }

        if (previous != null) {
            return previous;
        }

        String previousDate = adjacentDate(-1);
        if (previousDate == null) return null;
        Map<String, String> previousTimes = schedule.get(previousDate);
        if (previousTimes == null) return null;
        return new PrayerEvent("isha", previousTimes.get("isha"), parseDate(previousDate, previousTimes.get("isha")));
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
        tintButton(previousButton, previousButton.isEnabled());
        tintButton(nextButton, nextButton.isEnabled());
        tintButton(todayButton, true);
        tintButton(themeButton, true);
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

    private String elapsedText(PrayerEvent previous, Date now) {
        if (previous == null || previous.date == null) {
            return "تتحدث تلقائيًا";
        }
        long totalMinutes = Math.max(0, (now.getTime() - previous.date.getTime()) / 60000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0) {
            return "مضى على " + nameFor(previous.key) + " " + hours + "س " + minutes + "د";
        }
        return "مضى على " + nameFor(previous.key) + " " + minutes + "د";
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

    private void showThemeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(round(activeTheme.panel, dp(8), activeTheme.border));
        scrollView.addView(panel);

        TextView title = label("اختيار الأنماط", 20, activeTheme.primary, Typeface.BOLD);
        panel.addView(title, fullWidth());

        addThemeSection(panel, "أنماط الليل", nightThemes(), true, dialog);
        addThemeSection(panel, "أنماط النهار", dayThemes(), false, dialog);

        dialog.setContentView(scrollView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void addThemeSection(LinearLayout panel, String title, VisualTheme[] themes, boolean night, Dialog dialog) {
        TextView header = label(title, 13, activeTheme.secondary, Typeface.BOLD);
        header.setPadding(0, dp(14), 0, dp(4));
        panel.addView(header, fullWidth());

        String selected = night
                ? preferences.getString(NIGHT_THEME_KEY, nightThemes()[0].id)
                : preferences.getString(DAY_THEME_KEY, dayThemes()[0].id);

        for (VisualTheme theme : themes) {
            Button button = smallButton((theme.id.equals(selected) ? "✓ " : "") + theme.symbol + "  " + theme.title);
            button.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            button.setOnClickListener(v -> {
                preferences.edit().putString(night ? NIGHT_THEME_KEY : DAY_THEME_KEY, theme.id).apply();
                dialog.dismiss();
                rebuildContent();
            });
            panel.addView(button, fullWidthWithBottomMargin(6));
        }
    }

    private VisualTheme selectedTheme() {
        boolean night = isSystemNight();
        String id = preferences.getString(night ? NIGHT_THEME_KEY : DAY_THEME_KEY, night ? nightThemes()[0].id : dayThemes()[0].id);
        VisualTheme[] themes = night ? nightThemes() : dayThemes();
        for (VisualTheme theme : themes) {
            if (theme.id.equals(id)) return theme;
        }
        return themes[0];
    }

    private boolean isSystemNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private VisualTheme[] nightThemes() {
        return new VisualTheme[]{
                new VisualTheme("emerald", "زمرد هادئ", "ليل", "☾", c(5, 31, 30), c(18, 42, 32), c(20, 32, 31), c(22, 35, 33), c(45, 39, 19), c(125, 90, 32), c(42, 58, 55), c(52, 67, 64), c(67, 82, 78), c(122, 87, 33), Color.WHITE, c(155, 167, 164), c(244, 207, 109)),
                new VisualTheme("midnight", "منتصف الليل", "ليل", "☽", c(6, 13, 25), c(13, 23, 42), c(15, 22, 36), c(17, 26, 44), c(27, 45, 74), c(78, 113, 170), c(42, 52, 70), c(40, 49, 64), c(53, 62, 79), c(50, 89, 137), Color.WHITE, c(155, 174, 204), c(157, 199, 255)),
                new VisualTheme("old", "حالك قديم", "ليل", "●", c(3, 3, 3), c(17, 17, 17), c(22, 22, 22), c(24, 24, 24), c(36, 31, 20), c(134, 108, 50), c(55, 55, 55), c(38, 38, 38), c(54, 54, 54), c(96, 74, 34), Color.WHITE, c(168, 168, 168), c(229, 204, 134))
        };
    }

    private VisualTheme[] dayThemes() {
        return new VisualTheme[]{
                new VisualTheme("gold", "ذهبي هادئ", "نهار", "☀", c(247, 243, 232), c(232, 240, 236), Color.WHITE, c(255, 252, 245), c(245, 239, 224), c(155, 112, 45), c(224, 216, 201), c(255, 249, 239), c(239, 232, 219), c(154, 106, 40), c(38, 31, 25), c(142, 129, 114), c(154, 106, 40)),
                new VisualTheme("dawn", "ضحى", "نهار", "◌", c(244, 255, 248), c(226, 243, 235), Color.WHITE, c(249, 255, 252), c(232, 247, 239), c(61, 136, 112), c(207, 224, 216), c(241, 249, 245), c(228, 240, 234), c(43, 128, 106), c(23, 38, 33), c(105, 129, 119), c(43, 128, 106)),
                new VisualTheme("sky", "سماء صافية", "نهار", "☁", c(239, 247, 255), c(231, 237, 247), Color.WHITE, c(249, 252, 255), c(229, 240, 255), c(74, 117, 169), c(210, 219, 232), c(241, 246, 252), c(228, 237, 248), c(67, 114, 165), c(24, 33, 45), c(109, 124, 145), c(67, 114, 165))
        };
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(activeTheme.backgroundTop);
        getWindow().setNavigationBarColor(activeTheme.backgroundBottom);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(isSystemNight() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setTextColor(activeTheme.primary);
        button.setBackground(round(activeTheme.control, dp(8), activeTheme.border));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        params.setMargins(dp(5), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void tintButton(Button button, boolean enabled) {
        button.setAlpha(enabled ? 1f : 0.42f);
        button.setTextColor(activeTheme.primary);
        button.setBackground(round(activeTheme.control, dp(8), activeTheme.border));
    }

    private GradientDrawable gradient(int startColor, int endColor) {
        return new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{startColor, endColor});
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

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWithBottomMargin(int bottomDp) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    private LinearLayout.LayoutParams weightedWidth() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams wrapWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int systemBarHeight(String resourceName) {
        int id = getResources().getIdentifier(resourceName, "dimen", "android");
        if (id <= 0) return 0;
        return getResources().getDimensionPixelSize(id);
    }

    private int c(int red, int green, int blue) {
        return Color.rgb(red, green, blue);
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

    private static class VisualTheme {
        final String id;
        final String title;
        final String modeTitle;
        final String symbol;
        final int backgroundTop;
        final int backgroundBottom;
        final int panel;
        final int row;
        final int activeRow;
        final int activeBorder;
        final int border;
        final int control;
        final int controlPressed;
        final int countdown;
        final int primary;
        final int secondary;
        final int accent;
        final int rowBorder;

        VisualTheme(String id, String title, String modeTitle, String symbol, int backgroundTop, int backgroundBottom, int panel, int row, int activeRow, int activeBorder, int border, int control, int controlPressed, int countdown, int primary, int secondary, int accent) {
            this.id = id;
            this.title = title;
            this.modeTitle = modeTitle;
            this.symbol = symbol;
            this.backgroundTop = backgroundTop;
            this.backgroundBottom = backgroundBottom;
            this.panel = panel;
            this.row = row;
            this.activeRow = activeRow;
            this.activeBorder = activeBorder;
            this.border = border;
            this.control = control;
            this.controlPressed = controlPressed;
            this.countdown = countdown;
            this.primary = primary;
            this.secondary = secondary;
            this.accent = accent;
            this.rowBorder = border;
        }
    }
}
