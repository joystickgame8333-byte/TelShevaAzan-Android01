package com.example.telshevaazan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.text.TextUtils;
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
import android.widget.Toast;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.io.IOException;
import java.util.Date;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String APP_VERSION = "0.6.54";
    private static final String APP_BUILD = "145";
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
    private NotificationPane selectedNotificationPane = NotificationPane.ADHAN;

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
        root.setPadding(
                dp(12),
                dp(8) + systemBarHeight("status_bar_height"),
                dp(12),
                dp(8) + systemBarHeight("navigation_bar_height")
        );
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
        root.addView(dock(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));

        setContentView(shell);
        if (selectedTab == Tab.QIBLA) {
            startCompass();
        }
    }

    private View scheduleContent() {
        LinearLayout root = vertical();
        root.setGravity(Gravity.RIGHT);
        root.setPadding(dp(2), 0, dp(2), dp(4));

        root.addView(timeHeaderCard(), fullWidthWithMargins(dp(36), dp(4), dp(36), dp(8)));
        root.addView(nextPrayerCard(), fullWidthWithMargins(0, 0, 0, dp(8)));

        rowsContainer = vertical();
        rowsContainer.setPadding(dp(5), dp(5), dp(5), dp(0));
        rowsContainer.setBackground(round(theme.night ? Color.argb(70, 255, 255, 255) : Color.argb(112, 225, 242, 255), 22, theme.activeBorder));
        elevate(rowsContainer, 2);
        root.addView(rowsContainer, fullWidth());

        liveStatusLabel = label("", 12, theme.secondaryText, Typeface.BOLD);
        liveStatusLabel.setGravity(Gravity.CENTER);
        liveStatusLabel.setPadding(dp(8), dp(12), dp(8), dp(12));
        liveStatusLabel.setVisibility(View.GONE);
        root.addView(liveStatusLabel, fullWidth());

        updateScheduleView();
        return root;
    }

    private View timeHeaderCard() {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(7), dp(8), dp(7));
        card.setBackground(round(theme.night ? Color.argb(82, 255, 255, 255) : Color.argb(178, 255, 255, 255), 22, theme.border));
        elevate(card, 4);

        currentTimeLabel = label("--:--:--", 19, theme.accent, Typeface.BOLD);
        currentTimeLabel.setGravity(Gravity.CENTER);
        currentTimeLabel.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        currentTimeLabel.setIncludeFontPadding(false);
        card.addView(currentTimeLabel, fullWidth());

        dateLabel = label("--", 10, theme.accent, Typeface.BOLD);
        dateLabel.setGravity(Gravity.CENTER);
        dateLabel.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        card.addView(dateLabel, fullWidthWithMargins(0, dp(2), 0, dp(5)));

        LinearLayout indicator = new LinearLayout(this);
        indicator.setOrientation(LinearLayout.HORIZONTAL);
        indicator.setGravity(Gravity.CENTER);
        indicator.addView(pillSegment(AppTheme.withAlpha(theme.accent, 0.18), dp(24)));
        indicator.addView(pillSegment(AppTheme.withAlpha(theme.accent, 0.60), dp(48)));
        indicator.addView(pillSegment(AppTheme.withAlpha(theme.accent, 0.18), dp(24)));
        card.addView(indicator, wrap());
        return card;
    }

    private View pillSegment(int color, int width) {
        View view = new View(this);
        view.setBackground(round(color, 2, Color.TRANSPARENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(3));
        params.setMargins(dp(1), 0, dp(1), 0);
        view.setLayoutParams(params);
        return view;
    }

    private View nextPrayerCard() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(round(Color.TRANSPARENT, 24, theme.night ? AppTheme.withAlpha(Color.WHITE, 0.14) : AppTheme.withAlpha(Color.WHITE, 0.72)));
        card.setClipToOutline(true);
        elevate(card, 3);

        ImageView image = new ImageView(this);
        image.setImageResource(theme.night ? R.drawable.nabawi_night : R.drawable.nabawi_day);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(238)));

        View overlay = new View(this);
        overlay.setBackgroundColor(theme.night ? Color.argb(92, 0, 0, 0) : Color.argb(44, 255, 255, 255));
        card.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(238)));

        View floorGlow = new View(this);
        floorGlow.setBackground(heroFloorFade());
        FrameLayout.LayoutParams floorGlowParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(118), Gravity.BOTTOM);
        card.addView(floorGlow, floorGlowParams);

        View textGlow = new View(this);
        textGlow.setBackground(heroTextFade());
        FrameLayout.LayoutParams textGlowParams = new FrameLayout.LayoutParams(dp(322), dp(238), Gravity.RIGHT | Gravity.TOP);
        card.addView(textGlow, textGlowParams);

        LinearLayout content = vertical();
        content.setGravity(Gravity.RIGHT);
        content.setPadding(dp(18), dp(18), dp(18), dp(12));
        card.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(238)));

        TextView caption = label("الصلاة القادمة", 13, theme.accent, Typeface.BOLD);
        content.addView(caption, fullWidth());

        nextPrayerNameLabel = label("--", 30, theme.night ? Color.WHITE : theme.primaryText, Typeface.BOLD);
        nextPrayerNameLabel.setIncludeFontPadding(false);
        content.addView(nextPrayerNameLabel, fullWidth());

        nextPrayerTimeLabel = label("--:--", 44, theme.accent, Typeface.BOLD);
        nextPrayerTimeLabel.setIncludeFontPadding(false);
        content.addView(nextPrayerTimeLabel, fullWidth());

        countdownLabel = label("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        countdownLabel.setVisibility(View.GONE);
        content.addView(countdownLabel, new LinearLayout.LayoutParams(1, 1));

        elapsedLabel = label("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        elapsedLabel.setVisibility(View.GONE);
        content.addView(elapsedLabel, new LinearLayout.LayoutParams(1, 1));

        return card;
    }

    private GradientDrawable heroTextFade() {
        if (theme.night) {
            return new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{
                            Color.argb(0, 0, 0, 0),
                            Color.argb(122, 0, 0, 0),
                            Color.argb(214, 0, 0, 0)
                    }
            );
        }
        return new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(0, 242, 247, 250),
                        Color.argb(156, 239, 244, 247),
                        Color.argb(226, 238, 243, 246)
                }
        );
    }

    private GradientDrawable heroFloorFade() {
        if (theme.night) {
            return new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.argb(0, 0, 0, 0),
                            Color.argb(62, 0, 0, 0),
                            Color.argb(170, 0, 0, 0)
                    }
            );
        }
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(0, 244, 248, 250),
                        Color.argb(82, 242, 246, 248),
                        Color.argb(184, 241, 246, 249)
                }
        );
    }

    private View dateControls() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.RIGHT);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

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
        liveStatusLabel.setText("");
        liveStatusLabel.setVisibility(View.GONE);

        rowsContainer.removeAllViews();
        for (PrayerTime item : schedule.displayTimes()) {
            rowsContainer.addView(prayerRow(item, next, previous), fullWidthWithMargins(0, 0, 0, dp(4)));
        }
    }

    private View prayerRow(PrayerTime item, PrayerTime next, PrayerTime previous) {
        boolean active = next != null && item.key == next.key;
        boolean justPassed = previous != null && item.key == previous.key && !active;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(dp(10), dp(3), dp(8), dp(3));
        row.setBackground(round(active ? theme.activeRow : theme.row, 10, active ? theme.activeBorder : theme.border));
        elevate(row, active ? 3 : 1);
        row.setOnClickListener(v -> showPrayerDetails(item));

        LinearLayout timeColumn = vertical();
        timeColumn.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = label(item.time, 20, active ? theme.accent : theme.primaryText, Typeface.BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.LEFT);
        time.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        timeColumn.addView(time, fullWidth());
        TextView iqama = label(prayerDetailText(item), 10, active ? theme.secondaryText : AppTheme.withAlpha(theme.secondaryText, 0.86), Typeface.BOLD);
        singleLine(iqama);
        iqama.setGravity(Gravity.LEFT);
        iqama.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        timeColumn.addView(iqama, fullWidth());
        row.addView(timeColumn, new LinearLayout.LayoutParams(dp(96), dp(44)));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        text.setPadding(dp(7), 0, dp(7), 0);
        TextView name = label(item.title, 18, active ? theme.accent : theme.secondaryText, Typeface.BOLD);
        name.setIncludeFontPadding(false);
        singleLine(name);
        text.addView(name, fullWidth());
        TextView detail = label("", 10, theme.secondaryText, Typeface.BOLD);
        singleLine(detail);
        if (active && next != null) {
            detail.setText("متبقي " + next.key.targetLabel + " " + PrayerEngine.durationText(next.date.getTime() - now.getTime()));
        } else if (justPassed && previous != null) {
            detail.setText("مضى على " + previous.title + " " + PrayerEngine.durationText(now.getTime() - previous.date.getTime()));
        } else {
            detail.setText("");
        }
        text.addView(detail, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, dp(44), 1));

        ImageView icon = iconImage(prayerIcon(item.key), active ? theme.accent : AppTheme.withAlpha(theme.secondaryText, 0.54), 21);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(44)));

        ImageView chevron = iconImage(R.drawable.ic_chevron_left, AppTheme.withAlpha(theme.secondaryText, 0.34), 17);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(44)));

        if (active) {
            View bar = new View(this);
            bar.setBackground(round(theme.accent, 4, Color.TRANSPARENT));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(4), dp(34));
            barParams.setMargins(dp(4), 0, 0, 0);
            row.addView(bar, barParams);
        }
        return row;
    }

    private String prayerDetailText(PrayerTime item) {
        if (item.key == PrayerKey.SUNRISE) {
            return "وقت الشروق " + item.time;
        }
        return "الإقامة " + PrayerEngine.iqamaTime(item);
    }

    private int prayerIcon(PrayerKey key) {
        switch (key) {
            case FAJR:
                return R.drawable.ic_prayer_fajr;
            case SUNRISE:
                return R.drawable.ic_prayer_sun;
            case DHUHR:
                return R.drawable.ic_prayer_dhuhr;
            case ASR:
                return R.drawable.ic_prayer_asr;
            case MAGHRIB:
                return R.drawable.ic_prayer_maghrib;
            case ISHA:
            default:
                return R.drawable.ic_prayer_moon;
        }
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
        root.addView(notificationHeader(), fullWidthWithMargins(0, dp(12), 0, dp(12)));
        root.addView(notificationSegmentedControl(), fullWidthWithMargins(dp(18), 0, dp(18), dp(12)));
        if (selectedNotificationPane == NotificationPane.THEMES) {
            root.addView(notificationThemesPanel(), fullWidthWithMargins(0, 0, 0, dp(18)));
        } else {
            root.addView(notificationMasterToggle(), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(notificationPanel("صوت الأذان", R.drawable.ic_notification_volume, soundOptions(), null), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(notificationPanel("الصلوات التي يصدر لها الأذان", R.drawable.ic_notification_bell_ring, prayerToggles(), enabledPrayerSummary()), fullWidthWithMargins(0, 0, 0, dp(18)));
        }
        return scroll;
    }

    private View notificationHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(round(theme.control, 12, theme.border));
        ImageView icon = iconImage(R.drawable.ic_tab_bell, theme.accent, 23);
        iconBox.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("التنبيه", 27, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        TextView subtitle = label("الأذان والأنماط", 12, theme.accent, Typeface.BOLD);
        subtitle.setIncludeFontPadding(false);
        text.addView(subtitle, fullWidthWithMargins(0, dp(8), 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View notificationSegmentedControl() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams leftSegment = new LinearLayout.LayoutParams(0, dp(44), 1);
        leftSegment.setMargins(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams rightSegment = new LinearLayout.LayoutParams(0, dp(44), 1);
        rightSegment.setMargins(dp(4), 0, dp(4), 0);
        row.addView(notificationSegment("الأنماط", R.drawable.ic_notification_palette, selectedNotificationPane == NotificationPane.THEMES, () -> {
            selectedNotificationPane = NotificationPane.THEMES;
            rebuildContent();
        }), leftSegment);
        row.addView(notificationSegment("الأذان", R.drawable.ic_tab_bell, selectedNotificationPane == NotificationPane.ADHAN, () -> {
            selectedNotificationPane = NotificationPane.ADHAN;
            rebuildContent();
        }), rightSegment);
        return row;
    }

    private View notificationSegment(String title, int iconResource, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(10), 0, dp(10), 0);
        item.setBackground(round(selected ? theme.accent : theme.control, 12, selected ? theme.activeBorder : Color.TRANSPARENT));
        item.setOnClickListener(v -> action.run());

        ImageView icon = iconImage(iconResource, selected ? theme.primaryText : theme.secondaryText, 18);
        item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(32)));
        TextView label = label(title, 13, selected ? theme.primaryText : theme.secondaryText, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        item.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private View notificationMasterToggle() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(round(theme.row, 14, SalatiSettings.adhanEnabled(this) ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> {
            boolean next = !SalatiSettings.adhanEnabled(this);
            SalatiSettings.prefs(this).edit().putBoolean(SalatiSettings.KEY_ADHAN_ENABLED, next).apply();
            if (next) {
                requestNotificationPermissionIfNeeded();
            }
            PrayerNotificationScheduler.scheduleAll(this);
            rebuildContent();
        });

        row.addView(androidSwitchPill(SalatiSettings.adhanEnabled(this)), new LinearLayout.LayoutParams(dp(52), dp(30)));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("تشغيل تنبيهات الأذان", 15, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        TextView subtitle = label(SalatiSettings.adhanEnabled(this) ? "نشط للصلوات المختارة" : "متوقف", 11, theme.secondaryText, Typeface.BOLD);
        text.addView(subtitle, fullWidthWithMargins(0, dp(5), 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View notificationPanel(String title, int iconResource, View content, String meta) {
        LinearLayout panel = vertical();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(round(theme.panel, 16, theme.border));
        elevate(panel, 2);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (meta != null) {
            TextView metaView = label(meta, 11, theme.secondaryText, Typeface.BOLD);
            metaView.setGravity(Gravity.LEFT);
            header.addView(metaView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        } else {
            View spacer = new View(this);
            header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        }
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = iconImage(iconResource, theme.accent, 18);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView titleView = label(title, 13, theme.accent, Typeface.BOLD);
        titleView.setGravity(Gravity.RIGHT);
        titleRow.addView(titleView, wrap());
        header.addView(titleRow, wrap());
        panel.addView(header, fullWidthWithMargins(0, 0, 0, dp(10)));
        panel.addView(content, fullWidth());
        return panel;
    }

    private View soundOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.adhanSound(this);
        list.addView(soundOptionButton("أذان محمد جازي", R.drawable.ic_notification_volume, SalatiSettings.SOUND_ADHAN, selected, () -> setAdhanSound(SalatiSettings.SOUND_ADHAN)), fullWidth());
        list.addView(soundDivider(), fullWidth());
        list.addView(soundOptionButton("رسالة إشعار", R.drawable.ic_tab_sparkle, SalatiSettings.SOUND_SOFT, selected, () -> setAdhanSound(SalatiSettings.SOUND_SOFT)), fullWidth());
        list.addView(soundDivider(), fullWidth());
        list.addView(soundOptionButton("صوت النظام", R.drawable.ic_notification_phone, SalatiSettings.SOUND_SYSTEM, selected, () -> setAdhanSound(SalatiSettings.SOUND_SYSTEM)), fullWidth());
        list.addView(previewAdhanButton(), fullWidthWithMargins(0, dp(14), 0, 0));
        return list;
    }

    private View soundOptionButton(String title, int iconResource, String value, String selectedValue, Runnable action) {
        boolean selected = value.equals(selectedValue);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(10), 0);
        row.setBackground(round(selected ? theme.activeRow : Color.TRANSPARENT, 10, selected ? theme.activeBorder : Color.TRANSPARENT));
        row.setOnClickListener(v -> action.run());

        ImageView icon = iconImage(selected ? R.drawable.ic_notification_check : iconResource, selected ? theme.accent : theme.secondaryText, 19);
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(44)));
        TextView text = label(title, 14, selected ? theme.accent : theme.primaryText, Typeface.BOLD);
        text.setGravity(Gravity.RIGHT);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View soundDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(theme.border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(10), 0, dp(10), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private View previewAdhanButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(13), dp(10), dp(13), dp(10));
        button.setBackground(round(theme.accent, 12, theme.activeBorder));
        button.setOnClickListener(v -> {
            v.animate().alpha(0.72f).setDuration(80)
                    .withEndAction(() -> v.animate().alpha(1f).setDuration(180).start())
                    .start();
            Toast.makeText(this, "سيصدر اختبار الأذان بعد 5 ثواني", Toast.LENGTH_SHORT).show();
            sendPreviewNotification();
        });

        ImageView icon = iconImage(R.drawable.ic_notification_volume, AppTheme.withAlpha(theme.primaryText, 0.26), 23);
        button.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("اختبار الأذان بعد 5 ثواني", 14, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        TextView subtitle = label("اقفل الشاشة بسرعة وتأكد من الصوت المختار", 10, AppTheme.withAlpha(theme.primaryText, 0.62), Typeface.BOLD);
        text.addView(subtitle, fullWidthWithMargins(0, dp(5), 0, 0));
        button.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return button;
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
            list.addView(prayerToggleRow(key, time == null ? "--:--" : time), fullWidthWithMargins(0, 0, 0, dp(6)));
        }
        return list;
    }

    private View prayerToggleRow(PrayerKey key, String time) {
        boolean enabled = SalatiSettings.prayerEnabled(this, key);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(round(enabled ? theme.activeRow : theme.row, 12, enabled ? theme.activeBorder : Color.TRANSPARENT));
        row.setOnClickListener(v -> {
            SalatiSettings.setPrayerEnabled(this, key, !SalatiSettings.prayerEnabled(this, key));
            PrayerNotificationScheduler.scheduleAll(this);
            rebuildContent();
        });
        row.addView(androidSwitchPill(enabled), new LinearLayout.LayoutParams(dp(52), dp(30)));
        TextView timeView = label(time, 16, theme.secondaryText, Typeface.BOLD);
        timeView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        timeView.setIncludeFontPadding(false);
        row.addView(timeView, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView title = label(key.title, 16, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        row.addView(title, new LinearLayout.LayoutParams(dp(94), dp(40)));
        return row;
    }

    private String enabledPrayerSummary() {
        int enabled = 0;
        for (PrayerKey key : PrayerEngine.PRAYER_ORDER) {
            if (SalatiSettings.prayerEnabled(this, key)) {
                enabled++;
            }
        }
        return enabled + " من " + PrayerEngine.PRAYER_ORDER.length + " مفعّلة";
    }

    private View notificationThemesPanel() {
        LinearLayout panel = vertical();
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(round(theme.panel, 12, theme.border));
        elevate(panel, 2);
        TextView header = label("أنماط التطبيق", 15, theme.accent, Typeface.BOLD);
        panel.addView(header, fullWidthWithMargins(0, 0, 0, dp(14)));

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.HORIZONTAL);
        headings.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView day = label("نهاري", 14, theme.accent, Typeface.BOLD);
        day.setGravity(Gravity.CENTER);
        TextView night = label("ليلي", 14, theme.accent, Typeface.BOLD);
        night.setGravity(Gravity.CENTER);
        headings.addView(day, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headings.addView(night, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(headings, fullWidthWithMargins(0, 0, 0, dp(8)));

        ThemePalette[] dayChoices = AppTheme.dayChoices();
        ThemePalette[] nightChoices = AppTheme.nightChoices();
        int count = Math.max(dayChoices.length, nightChoices.length);
        for (int i = 0; i < count; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            if (i < dayChoices.length) {
                row.addView(themeTile(dayChoices[i]), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            if (i < nightChoices.length) {
                row.addView(themeTile(nightChoices[i]), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            panel.addView(row, fullWidthWithMargins(0, 0, 0, dp(12)));
        }
        TextView footer = label("اختر نمطًا نهاريًا ونمطًا ليليًا، والتطبيق يبدّل بينهم تلقائيًا حسب الوقت.", 12, theme.secondaryText, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        panel.addView(footer, fullWidth());
        return panel;
    }

    private View themeTile(ThemePalette choice) {
        String selectedId = prefs.getString(choice.night ? AppTheme.NIGHT_THEME_KEY : AppTheme.DAY_THEME_KEY, choice.night ? AppTheme.DEFAULT_NIGHT : AppTheme.DEFAULT_DAY);
        boolean selected = choice.id.equals(selectedId);
        LinearLayout tile = vertical();
        tile.setPadding(dp(8), dp(8), dp(8), dp(10));
        tile.setBackground(round(selected ? theme.activeRow : theme.row, 12, selected ? theme.activeBorder : Color.TRANSPARENT));
        tile.setOnClickListener(v -> {
            AppTheme.select(this, choice);
            SalatiWidgetUpdater.updateAll(this);
            rebuildContent();
        });

        FrameLayout preview = new FrameLayout(this);
        GradientDrawable previewBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{choice.backgroundTop, choice.backgroundMiddle, choice.backgroundBottom}
        );
        previewBackground.setCornerRadius(dp(10));
        preview.setBackground(previewBackground);
        TextView previewTime = label("04:08", 15, choice.accent, Typeface.BOLD);
        previewTime.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        previewTime.setPadding(dp(10), 0, dp(10), 0);
        preview.addView(previewTime, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageView previewIcon = iconImage(choice.night ? R.drawable.ic_prayer_moon : R.drawable.ic_tab_sparkle, choice.night ? Color.WHITE : choice.accent, 20);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        preview.addView(previewIcon, iconParams);
        tile.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        text.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label(selected ? "✓" : "○", 18, selected ? theme.accent : theme.secondaryText, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        text.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(42)));
        LinearLayout labels = vertical();
        TextView title = label(choice.title, 13, theme.primaryText, Typeface.BOLD);
        singleLine(title);
        labels.addView(title, fullWidth());
        TextView subtitle = label(choice.modeTitle, 9, theme.secondaryText, Typeface.BOLD);
        singleLine(subtitle);
        labels.addView(subtitle, fullWidth());
        text.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tile.addView(text, fullWidthWithMargins(0, dp(8), 0, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        tile.setLayoutParams(params);
        return tile;
    }

    private View switchPill(boolean on) {
        FrameLayout pill = new FrameLayout(this);
        pill.setBackground(round(on ? theme.accent : theme.control, 22, on ? theme.activeBorder : theme.border));
        View knob = new View(this);
        knob.setBackground(round(Color.WHITE, 16, Color.TRANSPARENT));
        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(32), dp(32), on ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        knobParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        pill.addView(knob, knobParams);
        return pill;
    }

    private View androidSwitchPill(boolean on) {
        FrameLayout pill = new FrameLayout(this);
        pill.setBackground(round(on ? theme.accent : theme.control, 16, on ? theme.activeBorder : theme.border));
        View knob = new View(this);
        knob.setBackground(round(Color.WHITE, 11, Color.TRANSPARENT));
        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(22), dp(22), on ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        knobParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        pill.addView(knob, knobParams);
        return pill;
    }

    private View themeChoices() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
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

        root.addView(panel("وقت الأذكار", nafahatIntervalOptions()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("نوع الذكر", nafahatTextOptions()), fullWidthWithMargins(0, 0, 0, 12));
        root.addView(panel("وقت الهدوء", quietOptions()), fullWidthWithMargins(0, 0, 0, dp(16)));
        return scroll;
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
        dock.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(8), dp(6), dp(8), dp(6));
        dock.setBackground(round(theme.night ? Color.argb(146, 0, 0, 0) : Color.argb(238, 255, 255, 255), 28, theme.border));
        elevate(dock, 7);
        addDockButton(dock, Tab.RADIO);
        addDockButton(dock, Tab.QIBLA);
        addDockButton(dock, Tab.SCHEDULE);
        addDockButton(dock, Tab.ADHKAR);
        addDockButton(dock, Tab.NOTIFICATIONS);
        return dock;
    }

    private void addDockButton(LinearLayout dock, Tab tab) {
        boolean selected = selectedTab == tab;
        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(4), dp(2), dp(2));
        item.setBackground(round(selected ? theme.accent : Color.TRANSPARENT, selected ? 20 : 16, Color.TRANSPARENT));
        item.setOnClickListener(v -> {
            selectedTab = tab;
            rebuildContent();
        });

        ImageView icon = iconImage(tabIcon(tab), selected ? Color.WHITE : theme.secondaryText, selected ? 22 : 20);
        item.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        TextView title = label(tab.title, 9, selected ? Color.WHITE : theme.secondaryText, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, selected ? 1.18f : 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        dock.addView(item, params);
    }

    private int tabIcon(Tab tab) {
        switch (tab) {
            case RADIO:
                return R.drawable.ic_tab_radio;
            case QIBLA:
                return R.drawable.ic_tab_qibla;
            case SCHEDULE:
                return R.drawable.ic_tab_clock;
            case ADHKAR:
                return R.drawable.ic_tab_sparkle;
            case NOTIFICATIONS:
            default:
                return R.drawable.ic_tab_bell;
        }
    }

    private View pageHeader(String title, String subtitle, String icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(0, 0, 0, dp(12));

        LinearLayout text = vertical();
        TextView titleView = label(title, 30, theme.primaryText, Typeface.BOLD);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 13, theme.accent, Typeface.BOLD);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView iconView = label(icon, 12, theme.accent, Typeface.BOLD);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        iconView.setBackground(round(theme.control, 8, theme.border));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(54), dp(42)));
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
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(10), dp(11), dp(10), dp(11));
        row.setBackground(round(on ? theme.activeRow : theme.row, 8, on ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> action.run());

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView titleView = label(title, 15, on ? theme.accent : theme.primaryText, Typeface.BOLD);
        singleLine(titleView);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 11, theme.secondaryText, Typeface.BOLD);
        singleLine(subtitleView);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView switchView = label(on ? "تشغيل" : "إيقاف", 12, on ? Color.WHITE : theme.secondaryText, Typeface.BOLD);
        switchView.setGravity(Gravity.CENTER);
        switchView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        switchView.setSingleLine(true);
        switchView.setIncludeFontPadding(false);
        switchView.setBackground(round(on ? theme.accent : theme.control, 18, theme.border));
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(dp(88), dp(36));
        switchParams.setMargins(dp(8), 0, 0, 0);
        row.addView(switchView, switchParams);
        return row;
    }

    private View optionButton(String title, String subtitle, String value, String selectedValue, Runnable action) {
        boolean selected = value.equals(selectedValue);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(round(selected ? theme.activeRow : theme.row, 8, selected ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> action.run());
        LinearLayout text = vertical();
        TextView titleView = label(title, 14, selected ? theme.accent : theme.primaryText, Typeface.BOLD);
        singleLine(titleView);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 11, theme.secondaryText, Typeface.BOLD);
        singleLine(subtitleView);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView mark = label(selected ? "✓" : "•", 18, selected ? theme.accent : theme.secondaryText, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        row.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(48)));
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
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), 0, dp(6), 0);
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
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
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
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        view.setIncludeFontPadding(true);
        return view;
    }

    private ImageView iconImage(int resource, int color, int sizeDp) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setColorFilter(color);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = Math.max(0, dp((Math.max(24, sizeDp + 4) - sizeDp) / 2));
        image.setPadding(padding, padding, padding, padding);
        return image;
    }

    private void singleLine(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    private LinearLayout pageRoot() {
        LinearLayout root = vertical();
        root.setGravity(Gravity.RIGHT);
        root.setPadding(dp(4), 0, dp(4), dp(18));
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

    private void elevate(View view, float value) {
        if (Build.VERSION.SDK_INT >= 21) {
            view.setElevation(dp((int) value));
        }
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
        times.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (prayer.key == PrayerKey.SUNRISE) {
            times.addView(detailTile("وقت الشروق", prayer.time, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        } else {
            times.addView(detailTile("الإقامة", PrayerEngine.iqamaTime(prayer), true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            times.addView(detailTile("الأذان", prayer.time, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
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

    private enum NotificationPane {
        ADHAN,
        THEMES
    }
}
