package com.example.telshevaazan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String APP_VERSION = "0.6.41";
    private static final String APP_BUILD = "132";
    private static final String WELCOME_KEY = "welcomeActivationPromptCompleted";
    private static final String RADIO_URL = "https://quran-radio.org:8899/;?type=http&nocache=29";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            now = new Date();
            if (selectedTab == Tab.SCHEDULE) {
                updateScheduleView();
            }
            if (selectedTab == Tab.QIBLA) {
                updateQiblaText();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private SharedPreferences prefs;
    private ThemePalette theme;
    private Date now = new Date();
    private String selectedDateKey;
    private boolean followsToday = true;
    private Tab selectedTab = Tab.SCHEDULE;

    private TextView currentTimeLabel;
    private TextView dateLabel;
    private TextView nextPrayerNameLabel;
    private TextView nextPrayerTimeLabel;
    private TextView countdownLabel;
    private TextView elapsedLabel;
    private TextView liveStatusLabel;
    private LinearLayout rowsContainer;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] gravityValues = new float[3];
    private final float[] magneticValues = new float[3];
    private boolean hasGravity;
    private boolean hasMagnetic;
    private Double heading;
    private TextView qiblaNeedle;
    private TextView qiblaInstruction;
    private TextView qiblaDifference;
    private TextView qiblaStatus;

    private MediaPlayer radioPlayer;
    private boolean radioPlaying;
    private boolean radioLoading;
    private TextView radioStatus;
    private ProgressBar radioProgress;
    private Button radioButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SalatiSettings.prefs(this);
        selectedDateKey = PrayerEngine.defaultDateKey();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        if (SalatiSettings.adhanEnabled(this) || SalatiSettings.nafahatEnabled(this)) {
            requestNotificationPermissionIfNeeded();
        }
        PrayerNotificationScheduler.ensureChannels(this);
        PrayerNotificationScheduler.scheduleAll(this);
        SalatiWidgetUpdater.updateAll(this);
        rebuildContent();
        maybeShowWelcome();
        handler.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedTab == Tab.QIBLA) {
            startCompass();
        }
        SalatiWidgetUpdater.updateAll(this);
    }

    @Override
    protected void onPause() {
        stopCompass();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopCompass();
        if (radioPlayer != null) {
            radioPlayer.release();
            radioPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravityValues, 0, gravityValues.length);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticValues, 0, magneticValues.length);
            hasMagnetic = true;
        }

        if (hasGravity && hasMagnetic) {
            float[] rotation = new float[9];
            float[] orientation = new float[3];
            if (SensorManager.getRotationMatrix(rotation, null, gravityValues, magneticValues)) {
                SensorManager.getOrientation(rotation, orientation);
                double azimuth = Math.toDegrees(orientation[0]);
                heading = (azimuth + 360) % 360;
                updateQiblaText();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (qiblaStatus == null) {
            return;
        }
        if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            qiblaStatus.setText("دقة ممتازة");
        } else if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            qiblaStatus.setText("دقة جيدة، أبعد الهاتف عن المعادن");
        } else {
            qiblaStatus.setText("حرّك الهاتف على شكل 8 لمعايرة البوصلة");
        }
    }

    private void rebuildContent() {
        theme = AppTheme.selected(this);
        applySystemBars();
        stopCompass();

        FrameLayout shell = new FrameLayout(this);
        shell.setBackground(AppTheme.backgroundGradient(theme));
        shell.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(12), dp(10) + systemBarHeight("status_bar_height"), dp(12), dp(10));
        shell.addView(root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View content;
        switch (selectedTab) {
            case RADIO:
                content = radioContent();
                break;
            case QIBLA:
                content = qiblaContent();
                break;
            case ADHKAR:
                content = adhkarContent();
                break;
            case NOTIFICATIONS:
                content = notificationsContent();
                break;
            case SCHEDULE:
            default:
                content = scheduleContent();
                break;
        }
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(dock(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)));

        setContentView(shell);
        if (selectedTab == Tab.QIBLA) {
            startCompass();
        }
    }

    private View scheduleContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = vertical();
        root.setGravity(Gravity.RIGHT);
        root.setPadding(dp(4), 0, dp(4), dp(8));
        scroll.addView(root);

        currentTimeLabel = label("--:--:--", 18, theme.accent, Typeface.BOLD);
        currentTimeLabel.setGravity(Gravity.CENTER);
        root.addView(currentTimeLabel, fullWidth());

        dateLabel = label("--", 14, theme.accent, Typeface.BOLD);
        dateLabel.setGravity(Gravity.CENTER);
        root.addView(dateLabel, fullWidth());

        root.addView(nextPrayerCard(), fullWidthWithMargins(0, 10, 0, 10));
        root.addView(dateControls(), fullWidthWithMargins(0, 0, 0, 8));

        rowsContainer = vertical();
        rowsContainer.setPadding(dp(7), dp(7), dp(7), dp(0));
        rowsContainer.setBackground(round(theme.panel, 14, theme.border));
        root.addView(rowsContainer, fullWidth());

        liveStatusLabel = label("", 12, theme.secondaryText, Typeface.BOLD);
        liveStatusLabel.setGravity(Gravity.CENTER);
        liveStatusLabel.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(liveStatusLabel, fullWidth());

        updateScheduleView();
        return scroll;
    }

    private View nextPrayerCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(round(Color.TRANSPARENT, 22, theme.night ? AppTheme.withAlpha(Color.WHITE, 0.14) : AppTheme.withAlpha(Color.WHITE, 0.72)));

        ImageView image = new ImageView(this);
        image.setImageResource(theme.night ? R.drawable.nabawi_night : R.drawable.nabawi_day);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(214)));

        View overlay = new View(this);
        overlay.setBackgroundColor(theme.night ? Color.argb(122, 0, 0, 0) : Color.argb(148, 255, 255, 255));
        card.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(214)));

        LinearLayout content = vertical();
        content.setGravity(Gravity.RIGHT);
        content.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(214)));

        TextView caption = label("الصلاة القادمة", 12, theme.accent, Typeface.BOLD);
        content.addView(caption, fullWidth());

        nextPrayerNameLabel = label("--", 30, theme.night ? Color.WHITE : theme.primaryText, Typeface.BOLD);
        content.addView(nextPrayerNameLabel, fullWidth());

        nextPrayerTimeLabel = label("--:--", 46, theme.accent, Typeface.BOLD);
        nextPrayerTimeLabel.setIncludeFontPadding(false);
        content.addView(nextPrayerTimeLabel, fullWidth());

        countdownLabel = label("--:--:--", 20, Color.WHITE, Typeface.BOLD);
        countdownLabel.setGravity(Gravity.CENTER);
        countdownLabel.setPadding(dp(12), dp(8), dp(12), dp(8));
        countdownLabel.setBackground(round(theme.countdown, 12, Color.TRANSPARENT));
        LinearLayout.LayoutParams countdownParams = wrap();
        countdownParams.gravity = Gravity.RIGHT;
        countdownParams.setMargins(0, dp(10), 0, 0);
        content.addView(countdownLabel, countdownParams);

        elapsedLabel = label("", 12, theme.night ? AppTheme.withAlpha(Color.WHITE, 0.82) : theme.secondaryText, Typeface.BOLD);
        elapsedLabel.setPadding(0, dp(8), 0, 0);
        content.addView(elapsedLabel, fullWidth());

        return card;
    }

    private View dateControls() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(smallButton("اليوم التالي", v -> moveDay(1)));
        row.addView(smallButton("اليوم", v -> {
            selectedDateKey = PrayerEngine.defaultDateKey(now);
            followsToday = true;
            updateScheduleView();
        }));
        row.addView(smallButton("اليوم السابق", v -> moveDay(-1)));
        return row;
    }

    private void updateScheduleView() {
        if (currentTimeLabel == null) {
            return;
        }
        if (followsToday) {
            selectedDateKey = PrayerEngine.defaultDateKey(now);
        }

        DaySchedule schedule = PrayerEngine.schedule(selectedDateKey);
        PrayerTime next = PrayerEngine.nextPrayer(selectedDateKey, now);
        PrayerTime previous = PrayerEngine.previousPrayer(selectedDateKey, now);
        currentTimeLabel.setText(PrayerEngine.timeText(now, true));
        dateLabel.setText(PrayerEngine.longDateLabel(selectedDateKey));
        nextPrayerNameLabel.setText(next == null ? "--" : next.title);
        nextPrayerTimeLabel.setText(next == null ? "--:--" : next.time);
        countdownLabel.setText(next == null ? "--:--:--" : PrayerEngine.durationText(next.date.getTime() - now.getTime()));
        elapsedLabel.setText(previous == null ? "تتحدث تلقائيًا" : "مضى على " + previous.title + " " + PrayerEngine.durationText(now.getTime() - previous.date.getTime()));
        liveStatusLabel.setText(next == null ? "تنبيهات Android تنتظر جدول الصلاة" : liveStatusText(next));

        rowsContainer.removeAllViews();
        for (PrayerTime item : schedule.displayTimes()) {
            rowsContainer.addView(prayerRow(item, next, previous), fullWidthWithMargins(0, 0, 0, 7));
        }
    }

    private View prayerRow(PrayerTime item, PrayerTime next, PrayerTime previous) {
        boolean active = next != null && item.key == next.key;
        boolean justPassed = previous != null && item.key == previous.key && !active;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(11), 0, dp(11), 0);
        row.setBackground(round(active ? theme.activeRow : theme.row, 8, active ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> showPrayerDetails(item));

        TextView time = label(item.time, 22, active ? theme.accent : theme.primaryText, Typeface.BOLD);
        row.addView(time, wrap());

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView name = label(item.title, 18, active ? theme.accent : theme.secondaryText, Typeface.BOLD);
        text.addView(name, fullWidth());
        TextView detail = label("الإقامة " + PrayerEngine.iqamaTime(item), 11, theme.secondaryText, Typeface.BOLD);
        if (active && next != null) {
            detail.setText("متبقي " + next.key.targetLabel + " " + PrayerEngine.durationText(next.date.getTime() - now.getTime()));
        } else if (justPassed && previous != null) {
            detail.setText("مضى على " + previous.title + " " + PrayerEngine.durationText(now.getTime() - previous.date.getTime()));
        }
        text.addView(detail, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView icon = label(item.key.iconLabel, 12, active ? theme.accent : theme.secondaryText, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(58)));
        return row;
    }

    private String liveStatusText(PrayerTime next) {
        long seconds = (next.date.getTime() - now.getTime()) / 1000;
        if (seconds > 5 * 60) {
            return "تنبيه الصلاة جاهز، وسيظهر إشعار Android عند وقت الأذان";
        }
        if (seconds > 0) {
            return "اقترب الأذان، تابع العدّاد أو أضف ويدجت صلاتي للشاشة";
        }
        return "حان الأذان";
    }

    private View notificationsContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(pageHeader("التنبيه", "الأذان والأنماط", "تنبيه"), fullWidth());

        root.addView(togglePanel(
                "تشغيل تنبيهات الأذان",
                SalatiSettings.adhanEnabled(this) ? "التنبيهات مفعّلة للصلوات المختارة" : "التنبيهات غير مفعلة",
                SalatiSettings.adhanEnabled(this),
                () -> {
                    boolean next = !SalatiSettings.adhanEnabled(this);
                    SalatiSettings.prefs(this).edit().putBoolean(SalatiSettings.KEY_ADHAN_ENABLED, next).apply();
                    if (next) {
                        requestNotificationPermissionIfNeeded();
                    }
                    PrayerNotificationScheduler.scheduleAll(this);
                    rebuildContent();
                }
        ), fullWidthWithMargins(0, 0, 0, 12));

        root.addView(panel("صوت الأذان", soundOptions()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("الصلوات التي يصدر لها الأذان", prayerToggles()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("أنماط التطبيق", themeChoices()), fullWidthWithMargins(0, 0, 0, 88));
        return scroll;
    }

    private View soundOptions() {
        LinearLayout list = vertical();
        list.addView(optionButton("الأذان الثاني", "المقطع الأساسي من نسخة iPhone", SalatiSettings.SOUND_ADHAN_SECOND, SalatiSettings.adhanSound(this), () -> setAdhanSound(SalatiSettings.SOUND_ADHAN_SECOND)));
        list.addView(optionButton("الأذان الأول", "الصوت السابق ضمن المجموعة", SalatiSettings.SOUND_ADHAN_FIRST, SalatiSettings.adhanSound(this), () -> setAdhanSound(SalatiSettings.SOUND_ADHAN_FIRST)));
        list.addView(optionButton("رسالة إشعار", "تنبيه أخف لمن لا يريد الأذان الكامل", SalatiSettings.SOUND_SOFT, SalatiSettings.adhanSound(this), () -> setAdhanSound(SalatiSettings.SOUND_SOFT)));
        list.addView(optionButton("صوت النظام", "تنبيه Android الافتراضي", SalatiSettings.SOUND_SYSTEM, SalatiSettings.adhanSound(this), () -> setAdhanSound(SalatiSettings.SOUND_SYSTEM)));
        list.addView(smallFullButton("اختبار التنبيه بعد 5 ثواني", v -> sendPreviewNotification()), fullWidthWithMargins(0, 10, 0, 0));
        return list;
    }

    private void setAdhanSound(String sound) {
        SalatiSettings.prefs(this).edit().putString(SalatiSettings.KEY_ADHAN_SOUND, sound).apply();
        PrayerNotificationScheduler.ensureChannels(this);
        PrayerNotificationScheduler.scheduleAll(this);
        rebuildContent();
    }

    private void sendPreviewNotification() {
        Handler previewHandler = new Handler(Looper.getMainLooper());
        previewHandler.postDelayed(() -> {
            android.content.Intent intent = new android.content.Intent(this, PrayerNotificationReceiver.class);
            intent.setAction(PrayerNotificationScheduler.ACTION_NOTIFY);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_KIND, PrayerNotificationScheduler.KIND_ADHAN);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_TITLE, "معاينة صوت الأذان");
            intent.putExtra(PrayerNotificationScheduler.EXTRA_BODY, "هذا الصوت سيعمل مع الصلوات التي تختارها");
            intent.putExtra(PrayerNotificationScheduler.EXTRA_SOUND, SalatiSettings.adhanSound(this));
            sendBroadcast(intent);
        }, 5000);
    }

    private View prayerToggles() {
        LinearLayout list = vertical();
        DaySchedule today = PrayerEngine.schedule(PrayerEngine.defaultDateKey());
        for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
            String time = today.times.get(key);
            list.addView(togglePanel(
                    key.title + "  " + (time == null ? "--:--" : time),
                    "تنبيه الأذان لهذه الصلاة",
                    SalatiSettings.prayerEnabled(this, key),
                    () -> {
                        SalatiSettings.setPrayerEnabled(this, key, !SalatiSettings.prayerEnabled(this, key));
                        PrayerNotificationScheduler.scheduleAll(this);
                        rebuildContent();
                    }
            ), fullWidthWithMargins(0, 0, 0, 8));
        }
        return list;
    }

    private View themeChoices() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.TOP);
        root.addView(themeColumn("نهاري", AppTheme.dayChoices()), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(themeColumn("ليلي", AppTheme.nightChoices()), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return root;
    }

    private View themeColumn(String title, ThemePalette[] choices) {
        LinearLayout column = vertical();
        TextView header = label(title, 12, theme.accent, Typeface.BOLD);
        column.addView(header, fullWidthWithMargins(0, 0, 0, 8));
        for (ThemePalette choice : choices) {
            boolean selected = choice.id.equals(theme.id);
            column.addView(optionButton(choice.title, choice.modeTitle, choice.id, selected ? choice.id : "", () -> {
                AppTheme.select(this, choice);
                SalatiWidgetUpdater.updateAll(this);
                rebuildContent();
            }), fullWidthWithMargins(dp(4), 0, dp(4), 8));
        }
        return column;
    }

    private View adhkarContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(pageHeader("أذكار", "تذكير روحي خفيف خلال اليوم", "أذكار"), fullWidth());

        root.addView(togglePanel(
                "تشغيل الأذكار",
                SalatiSettings.nafahatEnabled(this) ? "تذكير روحي خفيف " + intervalTitle(SalatiSettings.nafahatInterval(this)) : "تذكير الأذكار متوقف",
                SalatiSettings.nafahatEnabled(this),
                () -> {
                    boolean next = !SalatiSettings.nafahatEnabled(this);
                    SalatiSettings.prefs(this).edit().putBoolean(SalatiSettings.KEY_NAFAHAT_ENABLED, next).apply();
                    if (next) {
                        requestNotificationPermissionIfNeeded();
                    }
                    PrayerNotificationScheduler.scheduleAll(this);
                    rebuildContent();
                }
        ), fullWidthWithMargins(0, 0, 0, 12));

        root.addView(panel("ختمة مصغرة", miniKhatmahPanel()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("وقت الأذكار", nafahatIntervalOptions()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("نوع الذكر", nafahatTextOptions()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("وقت الهدوء", quietOptions()), fullWidthWithMargins(0, 0, 0, 88));
        return scroll;
    }

    private View miniKhatmahPanel() {
        LinearLayout root = vertical();
        boolean enabled = prefs.getBoolean(SalatiSettings.KEY_MINI_KHATMAH_ENABLED, false);
        String portion = prefs.getString(SalatiSettings.KEY_MINI_KHATMAH_PORTION, "halfPage");
        long start = prefs.getLong(SalatiSettings.KEY_MINI_KHATMAH_START, 0);
        int totalSteps = "fullPage".equals(portion) ? 604 : 1208;
        int currentStep = 1;
        if (start > 0) {
            long days = Math.max(0, (System.currentTimeMillis() - start) / (24L * 60L * 60L * 1000L));
            currentStep = (int) Math.min(totalSteps, days + 1);
        }
        int currentPage = "fullPage".equals(portion) ? currentStep : (int) Math.ceil(currentStep * 0.5);
        int percent = (int) Math.round((currentStep * 100.0) / totalSteps);
        root.addView(togglePanel(
                enabled ? "الختمة تعمل بهدوء" : "تشغيل الختمة المصغرة",
                enabled ? "صفحة " + currentPage + " • اليوم " + currentStep + " من " + totalSteps : "اختر نصف صفحة أو صفحة يوميًا",
                enabled,
                () -> {
                    boolean next = !prefs.getBoolean(SalatiSettings.KEY_MINI_KHATMAH_ENABLED, false);
                    SharedPreferences.Editor editor = prefs.edit().putBoolean(SalatiSettings.KEY_MINI_KHATMAH_ENABLED, next);
                    if (next && prefs.getLong(SalatiSettings.KEY_MINI_KHATMAH_START, 0) == 0) {
                        editor.putLong(SalatiSettings.KEY_MINI_KHATMAH_START, System.currentTimeMillis());
                    }
                    editor.apply();
                    rebuildContent();
                }
        ), fullWidthWithMargins(0, 0, 0, 8));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.addView(choiceButton("نصف صفحة", "halfPage".equals(portion), () -> setMiniKhatmahPortion("halfPage")), new LinearLayout.LayoutParams(0, dp(42), 1));
        choices.addView(choiceButton("صفحة يوميًا", "fullPage".equals(portion), () -> setMiniKhatmahPortion("fullPage")), new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(choices, fullWidthWithMargins(0, 0, 0, 8));
        TextView progress = label("التقدم " + percent + "%", 15, theme.accent, Typeface.BOLD);
        root.addView(progress, fullWidth());
        return root;
    }

    private void setMiniKhatmahPortion(String portion) {
        SharedPreferences.Editor editor = prefs.edit().putString(SalatiSettings.KEY_MINI_KHATMAH_PORTION, portion);
        if (prefs.getLong(SalatiSettings.KEY_MINI_KHATMAH_START, 0) == 0) {
            editor.putLong(SalatiSettings.KEY_MINI_KHATMAH_START, System.currentTimeMillis());
        }
        editor.apply();
        rebuildContent();
    }

    private View nafahatIntervalOptions() {
        LinearLayout list = vertical();
        int selected = SalatiSettings.nafahatInterval(this);
        int[] values = {30, 60, 120, 180};
        for (int value : values) {
            list.addView(optionButton(intervalTitle(value), intervalSubtitle(value), String.valueOf(value), String.valueOf(selected), () -> {
                prefs.edit().putInt(SalatiSettings.KEY_NAFAHAT_INTERVAL, value).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }));
        }
        return list;
    }

    private View nafahatTextOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.nafahatText(this);
        String[][] values = {
                {"mixed", "منوّع", "يتغير بين صلاة واستغفار وتسبيح ودعاء"},
                {"salawat", "الصلاة على النبي", "اللهم صل وسلم على نبينا محمد"},
                {"istighfar", "استغفار", "أستغفر الله وأتوب إليه"},
                {"tasbih", "تسبيح", "سبحان الله وبحمده"},
                {"dua", "أدعية قصيرة", "أدعية خفيفة كل فترة"},
                {"quran", "آيات وتذكير", "آيات قصيرة ومعانٍ لطيفة"}
        };
        for (String[] value : values) {
            list.addView(optionButton(value[1], value[2], value[0], selected, () -> {
                prefs.edit().putString(SalatiSettings.KEY_NAFAHAT_TEXT, value[0]).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }));
        }
        return list;
    }

    private View quietOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.quietWindow(this);
        String[][] values = {
                {"none", "بدون هدوء", "تعمل الأذكار طوال اليوم"},
                {"lateNight", "راحة الليل", "تتوقف من 11 ليلًا إلى 6 صباحًا"},
                {"midnight", "هدوء عميق", "تتوقف من 12 ليلًا إلى 7 صباحًا"}
        };
        for (String[] value : values) {
            list.addView(optionButton(value[1], value[2], value[0], selected, () -> {
                prefs.edit().putString(SalatiSettings.KEY_NAFAHAT_QUIET, value[0]).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }));
        }
        return list;
    }

    private View qiblaContent() {
        LinearLayout root = pageRoot();
        root.addView(pageHeader("القبلة", "اتجاه مكة من تل السبع · " + Math.round(QiblaCalculator.telShevaBearing()) + "°", "قبلة"), fullWidth());
        qiblaNeedle = label("▲", 112, theme.accent, Typeface.BOLD);
        qiblaNeedle.setGravity(Gravity.CENTER);
        qiblaNeedle.setPadding(0, dp(28), 0, dp(28));
        qiblaNeedle.setBackground(round(theme.panel, 160, theme.activeBorder));
        root.addView(qiblaNeedle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(270)));
        qiblaInstruction = label("حرّك الهاتف بهدوء", 24, theme.accent, Typeface.BOLD);
        qiblaInstruction.setGravity(Gravity.CENTER);
        root.addView(qiblaInstruction, fullWidthWithMargins(0, 12, 0, 8));
        qiblaDifference = label("الفرق المتبقي --", 18, theme.primaryText, Typeface.BOLD);
        qiblaDifference.setGravity(Gravity.CENTER);
        root.addView(qiblaDifference, fullWidthWithMargins(0, 0, 0, 8));
        qiblaStatus = label("شغّل البوصلة ووجّه أعلى الهاتف", 14, theme.secondaryText, Typeface.BOLD);
        qiblaStatus.setGravity(Gravity.CENTER);
        root.addView(qiblaStatus, fullWidth());
        updateQiblaText();
        return root;
    }

    private void startCompass() {
        if (sensorManager == null || accelerometer == null || magnetometer == null) {
            if (qiblaStatus != null) {
                qiblaStatus.setText("البوصلة غير متوفرة على هذا الجهاز");
            }
            return;
        }
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopCompass() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void updateQiblaText() {
        if (qiblaNeedle == null) {
            return;
        }
        if (heading == null) {
            qiblaInstruction.setText("المؤشر المضيء يشير للقبلة");
            qiblaDifference.setText("الفرق المتبقي --");
            return;
        }
        double delta = QiblaCalculator.delta(heading, QiblaCalculator.telShevaBearing());
        qiblaNeedle.setRotation((float) delta);
        boolean aligned = Math.abs(delta) <= 5;
        qiblaInstruction.setText(aligned ? "أنت على اتجاه القبلة" : (delta > 0 ? "لف يمين قليلًا" : "لف يسار قليلًا"));
        qiblaDifference.setText("الفرق المتبقي " + Math.round(Math.abs(delta)) + "°");
        qiblaInstruction.setTextColor(aligned ? Color.rgb(72, 220, 112) : theme.accent);
    }

    private View radioContent() {
        LinearLayout root = pageRoot();
        root.addView(pageHeader("راديو القرآن", "إذاعة القرآن الكريم من نابلس", "راديو"), fullWidth());
        LinearLayout panel = vertical();
        panel.setGravity(Gravity.RIGHT);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(round(theme.panel, 8, theme.border));

        radioStatus = label(radioLoading ? "جار الاتصال بالبث" : (radioPlaying ? "يعمل الآن" : "جاهز للتشغيل"), 14, theme.secondaryText, Typeface.BOLD);
        panel.addView(radioStatus, fullWidth());
        TextView title = label("إذاعة القرآن الكريم", 31, theme.primaryText, Typeface.BOLD);
        panel.addView(title, fullWidthWithMargins(0, dp(16), 0, dp(4)));
        TextView sub = label("الصوت القريب إلى القلوب", 13, theme.secondaryText, Typeface.BOLD);
        panel.addView(sub, fullWidth());
        radioProgress = new ProgressBar(this);
        radioProgress.setVisibility(radioLoading ? View.VISIBLE : View.GONE);
        panel.addView(radioProgress, fullWidthWithMargins(0, dp(18), 0, dp(8)));
        radioButton = smallFullButton(radioPlaying ? "إيقاف البث" : "تشغيل البث", v -> toggleRadio());
        panel.addView(radioButton, fullWidthWithMargins(0, dp(18), 0, 0));
        root.addView(panel, fullWidthWithMargins(0, dp(24), 0, dp(14)));
        TextView source = label("بث مباشر يحتاج اتصال إنترنت وقد يستمر في الخلفية ما دام التطبيق يعمل.", 13, theme.secondaryText, Typeface.BOLD);
        source.setPadding(dp(14), dp(14), dp(14), dp(14));
        source.setBackground(round(theme.control, 8, theme.border));
        root.addView(source, fullWidth());
        return root;
    }

    private void toggleRadio() {
        if (radioPlaying || radioLoading) {
            stopRadio();
        } else {
            startRadio();
        }
    }

    private void startRadio() {
        try {
            radioLoading = true;
            updateRadioLabels();
            if (radioPlayer != null) {
                radioPlayer.release();
            }
            radioPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= 21) {
                radioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                radioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            radioPlayer.setDataSource(RADIO_URL);
            radioPlayer.setOnPreparedListener(player -> {
                if (player != radioPlayer) {
                    return;
                }
                radioLoading = false;
                radioPlaying = true;
                player.start();
                updateRadioLabels();
            });
            radioPlayer.setOnErrorListener((player, what, extra) -> {
                if (player != radioPlayer) {
                    return true;
                }
                radioLoading = false;
                radioPlaying = false;
                updateRadioLabels("تعذر تشغيل البث");
                return true;
            });
            radioPlayer.prepareAsync();
        } catch (IOException exception) {
            radioLoading = false;
            radioPlaying = false;
            updateRadioLabels("تعذر الاتصال بالبث");
        }
    }

    private void stopRadio() {
        if (radioPlayer != null) {
            try {
                if (radioPlaying) {
                    radioPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
                // MediaPlayer can still be preparing when the user taps stop.
            }
            radioPlayer.release();
            radioPlayer = null;
        }
        radioLoading = false;
        radioPlaying = false;
        updateRadioLabels("متوقف مؤقتًا");
    }

    private void updateRadioLabels() {
        updateRadioLabels(radioLoading ? "جار الاتصال بالبث" : (radioPlaying ? "يعمل الآن" : "جاهز للتشغيل"));
    }

    private void updateRadioLabels(String status) {
        if (radioStatus != null) {
            radioStatus.setText(status);
        }
        if (radioProgress != null) {
            radioProgress.setVisibility(radioLoading ? View.VISIBLE : View.GONE);
        }
        if (radioButton != null) {
            radioButton.setText(radioPlaying || radioLoading ? "إيقاف البث" : "تشغيل البث");
        }
    }

    private LinearLayout dock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(6), dp(8), dp(6), dp(8));
        dock.setBackground(round(theme.night ? Color.argb(112, 0, 0, 0) : Color.argb(176, 255, 255, 255), 26, theme.border));
        addDockButton(dock, Tab.RADIO);
        addDockButton(dock, Tab.QIBLA);
        addDockButton(dock, Tab.SCHEDULE);
        addDockButton(dock, Tab.ADHKAR);
        addDockButton(dock, Tab.NOTIFICATIONS);
        return dock;
    }

    private void addDockButton(LinearLayout dock, Tab tab) {
        boolean selected = selectedTab == tab;
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(tab.title);
        button.setTextSize(selected ? 12 : 10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(selected ? Color.WHITE : theme.secondaryText);
        button.setBackground(round(selected ? theme.accent : Color.TRANSPARENT, selected ? 20 : 14, Color.TRANSPARENT));
        button.setOnClickListener(v -> {
            selectedTab = tab;
            rebuildContent();
        });
        dock.addView(button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private View pageHeader(String title, String subtitle, String icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView iconView = label(icon, 13, theme.accent, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(round(theme.control, 8, theme.border));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(42)));
        LinearLayout text = vertical();
        TextView titleView = label(title, 33, theme.primaryText, Typeface.BOLD);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 12, theme.accent, Typeface.BOLD);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View panel(String title, View content) {
        LinearLayout panel = vertical();
        panel.setGravity(Gravity.RIGHT);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(round(theme.panel, 8, theme.border));
        TextView header = label(title, 12, theme.accent, Typeface.BOLD);
        panel.addView(header, fullWidthWithMargins(0, 0, 0, 10));
        panel.addView(content, fullWidth());
        return panel;
    }

    private View togglePanel(String title, String subtitle, boolean on, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(10), dp(11));
        row.setBackground(round(on ? theme.activeRow : theme.row, 8, on ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> action.run());

        TextView switchView = label(on ? "تشغيل" : "إيقاف", 12, on ? Color.WHITE : theme.secondaryText, Typeface.BOLD);
        switchView.setGravity(Gravity.CENTER);
        switchView.setBackground(round(on ? theme.accent : theme.control, 20, theme.border));
        row.addView(switchView, new LinearLayout.LayoutParams(dp(76), dp(34)));

        LinearLayout text = vertical();
        TextView titleView = label(title, 15, on ? theme.accent : theme.primaryText, Typeface.BOLD);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 11, theme.secondaryText, Typeface.BOLD);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View optionButton(String title, String subtitle, String value, String selectedValue, Runnable action) {
        boolean selected = value.equals(selectedValue);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(round(selected ? theme.activeRow : theme.row, 8, selected ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> action.run());
        TextView mark = label(selected ? "✓" : "•", 18, selected ? theme.accent : theme.secondaryText, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(48)));
        LinearLayout text = vertical();
        text.addView(label(title, 14, selected ? theme.accent : theme.primaryText, Typeface.BOLD), fullWidth());
        text.addView(label(subtitle, 11, theme.secondaryText, Typeface.BOLD), fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private Button choiceButton(String text, boolean selected, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(selected ? Color.WHITE : theme.primaryText);
        button.setBackground(round(selected ? theme.accent : theme.control, 8, selected ? theme.activeBorder : theme.border));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(theme.primaryText);
        button.setBackground(round(theme.control, 8, theme.border));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(dp(5), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button smallFullButton(String text, View.OnClickListener listener) {
        Button button = smallButton(text, listener);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(theme.countdown, 8, theme.activeBorder));
        return button;
    }

    private TextView label(String text, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setGravity(Gravity.RIGHT);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        view.setIncludeFontPadding(true);
        return view;
    }

    private LinearLayout pageRoot() {
        LinearLayout root = vertical();
        root.setGravity(Gravity.RIGHT);
        root.setPadding(dp(6), 0, dp(6), dp(8));
        return root;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        return AppTheme.rounded(color, dp(radiusDp), strokeColor);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = fullWidth();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int systemBarHeight(String resourceName) {
        int id = getResources().getIdentifier(resourceName, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(theme.backgroundTop);
            getWindow().setNavigationBarColor(theme.backgroundBottom);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(theme.night ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void moveDay(int offset) {
        String next = PrayerEngine.dateKey(selectedDateKey, offset);
        if (next == null) {
            return;
        }
        selectedDateKey = next;
        followsToday = false;
        updateScheduleView();
    }

    private String intervalTitle(int value) {
        switch (value) {
            case 30:
                return "كل نصف ساعة";
            case 60:
                return "كل ساعة";
            case 180:
                return "كل 3 ساعات";
            case 120:
            default:
                return "كل ساعتين";
        }
    }

    private String intervalSubtitle(int value) {
        switch (value) {
            case 30:
                return "تذكير قريب وخفيف";
            case 60:
                return "توازن جميل خلال اليوم";
            case 180:
                return "تنبيهات قليلة جدًا";
            case 120:
            default:
                return "هادئ ومناسب للبداية";
        }
    }

    private void maybeShowWelcome() {
        if (prefs.getBoolean(WELCOME_KEY, false)) {
            return;
        }
        handler.postDelayed(() -> new AlertDialog.Builder(this)
                .setTitle("خلّي الأذان حاضر معك")
                .setMessage("فعّل الإشعارات والأذكار لتظهر لك الصلاة القادمة في وقتها.")
                .setNegativeButton("لاحقًا", (dialog, which) -> prefs.edit().putBoolean(WELCOME_KEY, true).apply())
                .setPositiveButton("تفعيل الآن", (dialog, which) -> {
                    prefs.edit().putBoolean(WELCOME_KEY, true).apply();
                    SalatiSettings.ensureWelcomeDefaults(this);
                    requestNotificationPermissionIfNeeded();
                    PrayerNotificationScheduler.scheduleAll(this);
                    rebuildContent();
                })
                .show(), 600);
    }

    private void showPrayerDetails(PrayerTime prayer) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setGravity(Gravity.RIGHT);
        root.setBackground(round(theme.panel, 20, theme.border));
        root.addView(label("تفاصيل الصلاة", 13, theme.accent, Typeface.BOLD), fullWidth());
        root.addView(label("صلاة " + prayer.title, 30, theme.primaryText, Typeface.BOLD), fullWidth());
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.addView(detailTile("الإقامة", PrayerEngine.iqamaTime(prayer), true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        times.addView(detailTile("الأذان", prayer.time, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(times, fullWidthWithMargins(0, dp(14), 0, dp(12)));
        String status = prayer.date.after(now)
                ? "متبقي " + prayer.key.targetLabel + " " + PrayerEngine.durationText(prayer.date.getTime() - now.getTime())
                : "مضى على " + prayer.title + " " + PrayerEngine.durationText(now.getTime() - prayer.date.getTime());
        root.addView(label(status, 16, theme.primaryText, Typeface.BOLD), fullWidth());
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private View detailTile(String title, String value, boolean highlighted) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.RIGHT);
        tile.setPadding(dp(12), dp(12), dp(12), dp(12));
        tile.setBackground(round(highlighted ? theme.activeRow : theme.control, 16, highlighted ? theme.activeBorder : theme.border));
        tile.addView(label(title, 12, theme.secondaryText, Typeface.BOLD), fullWidth());
        tile.addView(label(value, 30, highlighted ? theme.accent : theme.primaryText, Typeface.BOLD), fullWidth());
        return tile;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 922);
        }
    }

    private enum Tab {
        RADIO("الراديو"),
        QIBLA("القبلة"),
        SCHEDULE("مواقيت"),
        ADHKAR("أذكار"),
        NOTIFICATIONS("تنبيه");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }
}
