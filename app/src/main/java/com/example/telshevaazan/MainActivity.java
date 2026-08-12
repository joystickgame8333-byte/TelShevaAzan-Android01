package com.example.telshevaazan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.view.animation.OvershootInterpolator;
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
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String APP_VERSION = "0.6.59";
    private static final String APP_BUILD = "150";
    private static final float APP_FONT_SCALE = 0.82f;
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
    private Typeface appRegularTypeface;
    private Typeface appMediumTypeface;
    private Typeface appStrongTypeface;
    private Date now = new Date();
    private String selectedDateKey;
    private boolean followsToday = true;
    private Tab selectedTab = Tab.SCHEDULE;
    private NotificationPane selectedNotificationPane = NotificationPane.ADHAN;
    private AdhkarLibrary.Category selectedAdhkarCategory;
    private int selectedAdhkarIndex;
    private AdhkarProgressStore adhkarProgress;

    private TextView currentTimeLabel;
    private TextView dateLabel;
    private TextView nextPrayerCaptionLabel;
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
    private QiblaCompassView qiblaCompass;
    private TextView qiblaInstruction;
    private TextView qiblaDifference;
    private TextView qiblaStatus;
    private TextView qiblaHeadingValue;
    private TextView qiblaDeltaValue;

    private MediaPlayer radioPlayer;
    private boolean radioPlaying;
    private boolean radioLoading;
    private TextView radioStatus;
    private ProgressBar radioProgress;
    private TextView radioButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SalatiSettings.prefs(this);
        adhkarProgress = new AdhkarProgressStore(this);
        loadAppTypeface();
        selectedDateKey = PrayerEngine.automaticScheduleDateKey(now);
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
        PalestinePrayerCalendar.refreshRemoteIfNeeded(this, didUpdate -> {
            if (didUpdate) {
                PrayerNotificationScheduler.scheduleAll(this);
                SalatiWidgetUpdater.updateAll(this);
                if (followsToday) {
                    selectedDateKey = PrayerEngine.automaticScheduleDateKey(now);
                    updateScheduleView();
                }
            }
        });
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
        // Permission and system settings can change while the app is in the
        // background. Rebuilding the rolling alarm window here keeps the adhan
        // active without requiring the user to toggle another setting first.
        PrayerNotificationScheduler.ensureChannels(this);
        PrayerNotificationScheduler.scheduleAll(this);
        SalatiWidgetUpdater.updateAll(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 922) {
            PrayerNotificationScheduler.ensureChannels(this);
            PrayerNotificationScheduler.scheduleAll(this);
            rebuildContent();
        }
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
        root.addView(dock(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));

        setContentView(shell);
        if (selectedTab == Tab.QIBLA) {
            startCompass();
        }
    }

    private View scheduleContent() {
        LinearLayout root = vertical();
        root.setGravity(Gravity.RIGHT);
        root.setPadding(0, 0, 0, dp(2));

        root.addView(timeHeaderCard(), fullWidthWithMargins(dp(36), 0, dp(36), dp(3)));
        root.addView(nextPrayerCard(), fullWidthWithMargins(0, 0, 0, dp(4)));

        liveStatusLabel = label("", 13, theme.secondaryText, Typeface.BOLD);
        liveStatusLabel.setGravity(Gravity.CENTER);
        liveStatusLabel.setTextDirection(View.TEXT_DIRECTION_RTL);
        liveStatusLabel.setPadding(dp(10), dp(9), dp(10), dp(9));
        liveStatusLabel.setBackground(round(theme.control, 13, theme.border));
        root.addView(liveStatusLabel, fullWidthWithMargins(dp(10), 0, dp(10), dp(6)));

        rowsContainer = vertical();
        rowsContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        rowsContainer.setBackground(round(scheduleOuterPanel(), 24, scheduleOuterBorder(), 1));
        elevate(rowsContainer, 2);
        root.addView(rowsContainer, fullWidth());

        updateScheduleView();
        return root;
    }

    private int scheduleOuterPanel() {
        return theme.night ? Color.argb(116, 12, 24, 34) : Color.argb(112, 225, 242, 255);
    }

    private int scheduleOuterBorder() {
        return theme.night ? Color.argb(190, 78, 148, 210) : Color.argb(200, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent));
    }

    private int scheduleTopAccentBorder() {
        return theme.night ? Color.argb(142, 78, 148, 210) : Color.argb(154, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent));
    }

    private int scheduleSoftBorder() {
        return theme.night ? Color.argb(82, 206, 222, 238) : theme.border;
    }

    private int scheduleRow() {
        return theme.night ? Color.argb(176, 35, 48, 59) : theme.row;
    }

    private int scheduleRowBorder() {
        return theme.night ? Color.argb(86, 224, 235, 246) : theme.border;
    }

    private int scheduleActiveRow() {
        return theme.night ? Color.argb(208, 24, 72, 118) : theme.activeRow;
    }

    private int scheduleActiveBorder() {
        return theme.night ? Color.argb(186, 76, 150, 255) : theme.activeBorder;
    }

    private View timeHeaderCard() {
        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(3), dp(8), dp(3));
        card.setBackground(round(theme.night ? Color.argb(168, 16, 22, 30) : Color.argb(178, 255, 255, 255), 22, scheduleTopAccentBorder()));
        elevate(card, 4);

        currentTimeLabel = label("--:--:--", 27, theme.accent, Typeface.BOLD);
        currentTimeLabel.setTypeface(strongTypeface());
        currentTimeLabel.setGravity(Gravity.CENTER);
        currentTimeLabel.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        currentTimeLabel.setIncludeFontPadding(false);
        card.addView(currentTimeLabel, fullWidth());

        dateLabel = label("--", 15, theme.accent, Typeface.BOLD);
        dateLabel.setGravity(Gravity.CENTER);
        dateLabel.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        dateLabel.setIncludeFontPadding(false);
        card.addView(dateLabel, fullWidthWithMargins(0, 0, 0, dp(4)));

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
        card.setBackground(round(Color.TRANSPARENT, 24, scheduleTopAccentBorder()));
        card.setClipToOutline(true);
        elevate(card, 3);

        ImageView image = new ImageView(this);
        image.setImageResource(theme.night ? R.drawable.nabawi_night : R.drawable.nabawi_day);
        int heroHeight = dp(190);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heroHeight));

        View overlay = new View(this);
        overlay.setBackgroundColor(theme.night ? Color.argb(18, 255, 255, 255) : Color.argb(44, 255, 255, 255));
        card.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heroHeight));

        View floorGlow = new View(this);
        floorGlow.setBackground(heroFloorFade());
        FrameLayout.LayoutParams floorGlowParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(92), Gravity.BOTTOM);
        card.addView(floorGlow, floorGlowParams);

        View textGlow = new View(this);
        textGlow.setBackground(heroTextFade());
        FrameLayout.LayoutParams textGlowParams = new FrameLayout.LayoutParams(dp(322), heroHeight, Gravity.RIGHT | Gravity.TOP);
        card.addView(textGlow, textGlowParams);

        FrameLayout content = new FrameLayout(this);
        card.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, heroHeight));

        nextPrayerCaptionLabel = label("الصلاة القادمة", 16, theme.accent, Typeface.BOLD);
        nextPrayerCaptionLabel.setIncludeFontPadding(false);
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(24), Gravity.RIGHT | Gravity.TOP);
        captionParams.setMargins(dp(18), dp(15), dp(18), 0);
        content.addView(nextPrayerCaptionLabel, captionParams);

        nextPrayerNameLabel = label("--", 36, theme.night ? Color.WHITE : theme.primaryText, Typeface.BOLD);
        nextPrayerNameLabel.setTypeface(strongTypeface());
        nextPrayerNameLabel.setIncludeFontPadding(false);
        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(48), Gravity.RIGHT | Gravity.TOP);
        nameParams.setMargins(dp(18), dp(32), dp(18), 0);
        content.addView(nextPrayerNameLabel, nameParams);

        nextPrayerTimeLabel = label("--:--", 50, theme.accent, Typeface.BOLD);
        nextPrayerTimeLabel.setTypeface(strongTypeface());
        nextPrayerTimeLabel.setIncludeFontPadding(false);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(74), Gravity.RIGHT | Gravity.TOP);
        timeParams.setMargins(dp(18), dp(58), dp(18), 0);
        content.addView(nextPrayerTimeLabel, timeParams);

        countdownLabel = label("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        countdownLabel.setVisibility(View.GONE);
        content.addView(countdownLabel, new FrameLayout.LayoutParams(1, 1));

        elapsedLabel = label("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        elapsedLabel.setVisibility(View.GONE);
        content.addView(elapsedLabel, new FrameLayout.LayoutParams(1, 1));

        return card;
    }

    private GradientDrawable heroTextFade() {
        if (theme.night) {
            return new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{
                            Color.argb(0, 0, 0, 0),
                            Color.argb(54, 0, 0, 0),
                            Color.argb(126, 0, 0, 0)
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
                            Color.argb(22, 0, 0, 0),
                            Color.argb(82, 0, 0, 0)
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
            selectedDateKey = PrayerEngine.automaticScheduleDateKey(now);
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
            selectedDateKey = PrayerEngine.automaticScheduleDateKey(now);
        }

        DaySchedule schedule = PrayerEngine.schedule(selectedDateKey);
        PrayerTime next = PrayerEngine.nextPrayer(selectedDateKey, now);
        PrayerTime previous = PrayerEngine.previousPrayer(selectedDateKey, now);
        PrayerTime activeIqama = PrayerEngine.activeIqama(now);
        currentTimeLabel.setText(PrayerEngine.timeText(now, true));
        dateLabel.setText(PrayerEngine.longDateLabel(selectedDateKey));
        PrayerTime featured = activeIqama != null ? activeIqama : next;
        nextPrayerCaptionLabel.setText(activeIqama != null ? "الإقامة القادمة" : "الصلاة القادمة");
        nextPrayerNameLabel.setText(featured == null ? "--" : featured.title);
        nextPrayerTimeLabel.setText(featured == null ? "--:--" : (activeIqama != null ? PrayerEngine.iqamaTime(featured) : featured.time));
        long featuredTarget = activeIqama != null ? PrayerEngine.iqamaDate(activeIqama).getTime() : (featured == null ? now.getTime() : featured.date.getTime());
        countdownLabel.setText(featured == null ? "--:--:--" : PrayerEngine.durationText(featuredTarget - now.getTime()));
        elapsedLabel.setText(previous == null ? "تتحدث تلقائيًا" : "مضى على " + previous.title + " " + PrayerEngine.durationText(now.getTime() - previous.date.getTime()));
        if (activeIqama != null) {
            liveStatusLabel.setText("متبقي على إقامة صلاة " + activeIqama.title + "  •  " + PrayerEngine.durationText(PrayerEngine.iqamaDate(activeIqama).getTime() - now.getTime()));
            liveStatusLabel.setTextColor(theme.accent);
            liveStatusLabel.setBackground(round(theme.activeRow, 13, theme.activeBorder));
        } else if (next != null) {
            liveStatusLabel.setText("متبقي على " + next.title + "  •  " + PrayerEngine.durationText(next.date.getTime() - now.getTime()));
            liveStatusLabel.setTextColor(theme.secondaryText);
            liveStatusLabel.setBackground(round(theme.control, 13, theme.border));
        } else {
            liveStatusLabel.setText("تتحدث المواقيت تلقائيًا");
        }

        rowsContainer.removeAllViews();
        List<PrayerTime> displayTimes = schedule.displayTimes();
        for (int i = 0; i < displayTimes.size(); i++) {
            PrayerTime item = displayTimes.get(i);
            int bottomMargin = i == displayTimes.size() - 1 ? 0 : dp(4);
            rowsContainer.addView(prayerRow(item, next, previous, activeIqama), fullWidthWithMargins(0, 0, 0, bottomMargin));
        }
    }

    private View prayerRow(PrayerTime item, PrayerTime next, PrayerTime previous, PrayerTime activeIqama) {
        boolean active = activeIqama != null ? item.key == activeIqama.key : next != null && item.key == next.key;
        boolean justPassed = previous != null && item.key == previous.key && !active;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setPadding(dp(10), dp(4), dp(8), dp(4));
        row.setBackground(round(active ? scheduleActiveRow() : scheduleRow(), 11, active ? scheduleActiveBorder() : scheduleRowBorder()));
        elevate(row, active ? 3 : 1);
        row.setClickable(false);
        int rowContentHeight = dp(54);

        LinearLayout timeColumn = vertical();
        timeColumn.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = label(item.time, 18, active ? theme.accent : theme.primaryText, Typeface.BOLD);
        time.setTypeface(strongTypeface());
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.LEFT);
        time.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        timeColumn.addView(time, fullWidth());
        TextView iqama = label(prayerDetailText(item), 14, active ? theme.secondaryText : AppTheme.withAlpha(theme.secondaryText, 0.90), Typeface.BOLD);
        iqama.setIncludeFontPadding(true);
        singleLine(iqama);
        iqama.setGravity(Gravity.LEFT);
        iqama.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        timeColumn.addView(iqama, fullWidth());
        row.addView(timeColumn, new LinearLayout.LayoutParams(dp(102), rowContentHeight));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        text.setPadding(dp(7), 0, dp(7), 0);
        TextView name = label(item.title, 16, active ? theme.accent : theme.secondaryText, Typeface.BOLD);
        name.setIncludeFontPadding(false);
        singleLine(name);
        text.addView(name, fullWidth());
        TextView detail = label("", 14, theme.secondaryText, Typeface.BOLD);
        detail.setIncludeFontPadding(true);
        singleLine(detail);
        if (activeIqama != null && item.key == activeIqama.key) {
            detail.setText("متبقي للإقامة " + PrayerEngine.durationText(PrayerEngine.iqamaDate(activeIqama).getTime() - now.getTime()));
        } else if (active && next != null) {
            detail.setText("متبقي " + next.key.targetLabel + " " + PrayerEngine.durationText(next.date.getTime() - now.getTime()));
        } else if (justPassed && previous != null) {
            detail.setText("مضى على " + previous.title + " " + PrayerEngine.durationText(now.getTime() - previous.date.getTime()));
        } else {
            detail.setText("");
        }
        text.addView(detail, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, rowContentHeight, 1));

        ImageView icon = iconImage(prayerIcon(item.key), active ? theme.accent : AppTheme.withAlpha(theme.secondaryText, 0.54), 20);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), rowContentHeight));

        ImageView chevron = iconImage(R.drawable.ic_chevron_left, AppTheme.withAlpha(theme.secondaryText, 0.34), 17);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), rowContentHeight));

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
        } else if (selectedNotificationPane == NotificationPane.ADHKAR) {
            root.addView(adhkarMasterToggle(), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(adhkarPanel("اختبار تذكير الأذكار", "إشعار صامت مع اهتزاز خفيف حسب وضع الهاتف", previewNafahatPanel()), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(adhkarPanel("وقت الأذكار", "اختر كل كم وقت يصلك ذكر خفيف", nafahatIntervalOptions()), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(adhkarPanel("نوع التذكير", "اختر مجموعة النصوص التي تناسبك", nafahatTextOptions()), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(adhkarPanel("وقت الهدوء", "لن تصل تذكيرات الأذكار أثناء فترة الهدوء", quietOptions()), fullWidthWithMargins(0, 0, 0, dp(18)));
        } else {
            root.addView(notificationMasterToggle(), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(notificationPanel("صوت الأذان", R.drawable.ic_notification_volume, soundOptions(), null), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(notificationPanel("الصلوات التي يصدر لها الأذان", R.drawable.ic_notification_bell_ring, prayerToggles(), enabledPrayerSummary()), fullWidthWithMargins(0, 0, 0, dp(8)));
            root.addView(notificationPanel("تنبيهات الإقامة", R.drawable.ic_tab_clock, iqamaNotificationOptions(), "بعد الأذان مباشرة"), fullWidthWithMargins(0, 0, 0, dp(18)));
        }
        return scroll;
    }

    private View notificationHeader() {
        return mediaPageHeader("التنبيه", "الأذان والإقامة والأذكار والأنماط", R.drawable.ic_tab_bell);
    }

    private View notificationSegmentedControl() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams leftSegment = notificationSegmentParams();
        LinearLayout.LayoutParams middleSegment = notificationSegmentParams();
        LinearLayout.LayoutParams rightSegment = notificationSegmentParams();
        row.addView(notificationSegment("الأنماط", R.drawable.ic_notification_palette, selectedNotificationPane == NotificationPane.THEMES, () -> {
            selectedNotificationPane = NotificationPane.THEMES;
            rebuildContent();
        }), leftSegment);
        row.addView(notificationSegment("الأذكار", R.drawable.ic_tab_sparkle, selectedNotificationPane == NotificationPane.ADHKAR, () -> {
            selectedNotificationPane = NotificationPane.ADHKAR;
            rebuildContent();
        }), middleSegment);
        row.addView(notificationSegment("الأذان", R.drawable.ic_tab_bell, selectedNotificationPane == NotificationPane.ADHAN, () -> {
            selectedNotificationPane = NotificationPane.ADHAN;
            rebuildContent();
        }), rightSegment);
        return row;
    }

    private LinearLayout.LayoutParams notificationSegmentParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private View iqamaNotificationOptions() {
        LinearLayout content = vertical();
        boolean enabled = SalatiSettings.iqamaEnabled(this);
        LinearLayout toggle = new LinearLayout(this);
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(dp(10), dp(8), dp(10), dp(8));
        toggle.setBackground(round(enabled ? theme.activeRow : theme.row, 12, enabled ? theme.activeBorder : theme.border));
        toggle.setOnClickListener(v -> {
            boolean next = !SalatiSettings.iqamaEnabled(this);
            prefs.edit().putBoolean(SalatiSettings.KEY_IQAMA_ENABLED, next).apply();
            if (next) requestNotificationPermissionIfNeeded();
            PrayerNotificationScheduler.scheduleAll(this);
            rebuildContent();
        });
        toggle.addView(androidSwitchPill(enabled), new LinearLayout.LayoutParams(dp(52), dp(30)));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.RIGHT);
        TextView title = label("تنبيه عند إقامة الصلاة", 15, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        copy.addView(title, fullWidth());
        TextView subtitle = label("يظهر عند انتهاء عداد الإقامة لكل صلاة مفعلة", 11, theme.secondaryText, Typeface.BOLD);
        subtitle.setGravity(Gravity.RIGHT);
        copy.addView(subtitle, fullWidthWithMargins(0, dp(4), 0, 0));
        toggle.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        content.addView(toggle, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(12), dp(10), dp(12), dp(10));
        preview.setBackground(round(theme.control, 12, theme.border));
        preview.setOnClickListener(v -> sendPreviewIqama());
        ImageView icon = iconImage(R.drawable.ic_tab_clock, theme.accent, 22);
        preview.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(42)));
        LinearLayout previewCopy = vertical();
        previewCopy.setGravity(Gravity.RIGHT);
        TextView previewTitle = label("معاينة عداد الإقامة", 14, theme.primaryText, Typeface.BOLD);
        previewTitle.setGravity(Gravity.RIGHT);
        previewCopy.addView(previewTitle, fullWidth());
        TextView delays = label("الفجر 24 · الظهر 14 · العصر 16 · المغرب 7 · العشاء 14 دقيقة", 10, theme.secondaryText, Typeface.BOLD);
        delays.setGravity(Gravity.RIGHT);
        previewCopy.addView(delays, fullWidthWithMargins(0, dp(4), 0, 0));
        preview.addView(previewCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        content.addView(preview, fullWidth());
        return content;
    }

    private void sendPreviewIqama() {
        requestNotificationPermissionIfNeeded();
        Toast.makeText(this, "ستظهر معاينة الإقامة بعد ثانيتين", Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> {
            android.content.Intent intent = new android.content.Intent(this, PrayerNotificationReceiver.class);
            intent.setAction(PrayerNotificationScheduler.ACTION_NOTIFY);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_KIND, PrayerNotificationScheduler.KIND_IQAMA);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_TITLE, "الآن تُقام صلاة الظهر");
            intent.putExtra(PrayerNotificationScheduler.EXTRA_BODY, "حيّ على الصلاة");
            sendBroadcast(intent);
        }, 2000);
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
        scroll.setFillViewport(true);
        LinearLayout root = pageRoot();
        scroll.addView(root);
        root.addView(mediaPageHeader("الأذكار", "ورد يومي بسيط ومحفوظ على جهازك", R.drawable.ic_tab_sparkle), fullWidthWithMargins(0, dp(4), 0, dp(12)));
        if (selectedAdhkarCategory == null) {
            root.addView(adhkarOverview(), fullWidthWithMargins(0, 0, 0, dp(18)));
        } else {
            root.addView(adhkarReader(), fullWidthWithMargins(0, 0, 0, dp(18)));
        }
        return scroll;
    }

    private View adhkarOverview() {
        LinearLayout content = vertical();
        content.setGravity(Gravity.RIGHT);
        AdhkarLibrary.Category suggested = AdhkarLibrary.suggestedNow();
        int complete = adhkarProgress.totalCompleted();
        int total = adhkarProgress.totalItems();

        LinearLayout suggestion = new LinearLayout(this);
        suggestion.setOrientation(LinearLayout.HORIZONTAL);
        suggestion.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        suggestion.setGravity(Gravity.CENTER_VERTICAL);
        suggestion.setPadding(dp(16), dp(14), dp(16), dp(14));
        suggestion.setBackground(round(theme.panel, 18, theme.activeBorder));
        suggestion.setOnClickListener(v -> openAdhkarCategory(suggested));
        ImageView icon = iconImage(suggested.icon, theme.accent, 26);
        suggestion.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(58)));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.RIGHT);
        TextView title = label("تابع " + suggested.title, 21, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        copy.addView(title, fullWidth());
        TextView subtitle = label("نقترح عليك الورد المناسب لهذا الوقت", 13, theme.secondaryText, Typeface.BOLD);
        subtitle.setGravity(Gravity.RIGHT);
        copy.addView(subtitle, fullWidthWithMargins(0, dp(4), 0, 0));
        suggestion.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView progress = label(complete + "/" + total, 15, theme.accent, Typeface.BOLD);
        progress.setGravity(Gravity.CENTER);
        progress.setBackground(round(theme.activeRow, 28, theme.activeBorder));
        suggestion.addView(progress, new LinearLayout.LayoutParams(dp(66), dp(66)));
        content.addView(suggestion, fullWidthWithMargins(0, 0, 0, dp(14)));

        TextView choose = label("اختر وردك", 19, theme.secondaryText, Typeface.BOLD);
        choose.setGravity(Gravity.RIGHT);
        content.addView(choose, fullWidthWithMargins(0, 0, 0, dp(10)));

        AdhkarLibrary.Category[] categories = AdhkarLibrary.Category.values();
        for (int i = 0; i < categories.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.CENTER);
            row.addView(adhkarCategoryCard(categories[i]), categoryCardParams(i + 1 < categories.length ? dp(5) : 0));
            if (i + 1 < categories.length) {
                row.addView(adhkarCategoryCard(categories[i + 1]), categoryCardParams(0));
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            }
            content.addView(row, fullWidthWithMargins(0, 0, 0, dp(10)));
        }
        return content;
    }

    private LinearLayout.LayoutParams categoryCardParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(104), 1);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private View adhkarCategoryCard(AdhkarLibrary.Category category) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(round(theme.panel, 16, theme.border));
        card.setOnClickListener(v -> openAdhkarCategory(category));
        ImageView icon = iconImage(category.icon, theme.accent, 24);
        icon.setBackground(round(AppTheme.withAlpha(theme.accent, 0.14), 30, Color.TRANSPARENT));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        int completed = adhkarProgress.completed(category);
        int total = AdhkarLibrary.items(category).size();
        TextView title = label(category.title, 18, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        text.addView(title, fullWidth());
        TextView detail = label(completed + " من " + total, 13, theme.secondaryText, Typeface.BOLD);
        detail.setGravity(Gravity.RIGHT);
        text.addView(detail, fullWidthWithMargins(0, dp(4), 0, 0));
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private void openAdhkarCategory(AdhkarLibrary.Category category) {
        selectedAdhkarCategory = category;
        List<AdhkarLibrary.Item> items = AdhkarLibrary.items(category);
        selectedAdhkarIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            if (adhkarProgress.count(items.get(i)) < items.get(i).target) {
                selectedAdhkarIndex = i;
                break;
            }
        }
        rebuildContent();
    }

    private View adhkarReader() {
        LinearLayout content = vertical();
        content.setGravity(Gravity.RIGHT);
        List<AdhkarLibrary.Item> items = AdhkarLibrary.items(selectedAdhkarCategory);
        if (items.isEmpty()) return content;
        selectedAdhkarIndex = Math.max(0, Math.min(selectedAdhkarIndex, items.size() - 1));
        AdhkarLibrary.Item item = items.get(selectedAdhkarIndex);
        int count = adhkarProgress.count(item);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(8), 0, dp(8), 0);
        TextView category = label(selectedAdhkarCategory.title, 22, theme.primaryText, Typeface.BOLD);
        category.setGravity(Gravity.RIGHT);
        navigation.addView(category, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView back = label("الأقسام  ‹", 14, theme.accent, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(theme.control, 12, theme.border));
        back.setOnClickListener(v -> { selectedAdhkarCategory = null; rebuildContent(); });
        navigation.addView(back, new LinearLayout.LayoutParams(dp(104), dp(44)));
        content.addView(navigation, fullWidthWithMargins(0, 0, 0, dp(10)));

        TextView position = label((selectedAdhkarIndex + 1) + " من " + items.size(), 13, theme.secondaryText, Typeface.BOLD);
        position.setGravity(Gravity.RIGHT);
        content.addView(position, fullWidthWithMargins(dp(8), 0, dp(8), dp(8)));

        LinearLayout card = vertical();
        card.setGravity(Gravity.RIGHT);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(round(theme.panel, 18, theme.border));
        TextView title = label(item.title, 21, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        card.addView(title, fullWidth());
        TextView source = label(item.source, 12, theme.secondaryText, Typeface.BOLD);
        source.setGravity(Gravity.RIGHT);
        card.addView(source, fullWidthWithMargins(0, dp(4), 0, dp(12)));
        View divider = new View(this);
        divider.setBackgroundColor(theme.border);
        card.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        TextView body = label(item.text, 24, theme.primaryText, Typeface.NORMAL);
        body.setTypeface(strongTypeface());
        body.setGravity(Gravity.RIGHT);
        body.setTextDirection(View.TEXT_DIRECTION_RTL);
        body.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        body.setLineSpacing(dp(7), 1f);
        body.setPadding(0, dp(18), 0, dp(16));
        card.addView(body, fullWidth());
        if (item.note != null) {
            TextView note = label(item.note, 13, theme.secondaryText, Typeface.BOLD);
            note.setGravity(Gravity.RIGHT);
            note.setBackground(round(theme.control, 10, theme.border));
            note.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.addView(note, fullWidthWithMargins(0, 0, 0, dp(12)));
        }

        TextView counter = label(count >= item.target ? "تم بحمد الله ✓" : "اضغط للعدّ  •  " + count + " / " + item.target, 18, count >= item.target ? Color.WHITE : theme.primaryText, Typeface.BOLD);
        counter.setGravity(Gravity.CENTER);
        counter.setBackground(round(count >= item.target ? theme.accent : theme.activeRow, 16, theme.activeBorder));
        counter.setOnClickListener(v -> {
            int newCount = adhkarProgress.increment(item);
            if (newCount >= item.target && selectedAdhkarIndex < items.size() - 1) selectedAdhkarIndex++;
            rebuildContent();
        });
        card.addView(counter, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        content.addView(card, fullWidthWithMargins(0, 0, 0, dp(10)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(readerControl("السابق", selectedAdhkarIndex > 0, () -> { selectedAdhkarIndex--; rebuildContent(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        controls.addView(readerControl("تراجع", count > 0, () -> { adhkarProgress.decrement(item); rebuildContent(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        controls.addView(readerControl("التالي", selectedAdhkarIndex < items.size() - 1, () -> { selectedAdhkarIndex++; rebuildContent(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        content.addView(controls, fullWidth());
        return content;
    }

    private View readerControl(String title, boolean enabled, Runnable action) {
        TextView button = label(title, 14, enabled ? theme.primaryText : AppTheme.withAlpha(theme.secondaryText, 0.42), Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(theme.control, 12, theme.border));
        if (enabled) button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private View adhkarHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(round(theme.control, 12, theme.border));
        ImageView icon = iconImage(R.drawable.ic_tab_sparkle, theme.accent, 25);
        iconBox.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("أذكار", 34, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        TextView subtitle = label("تذكير روحي خفيف خلال اليوم", 15, theme.accent, Typeface.BOLD);
        subtitle.setIncludeFontPadding(false);
        text.addView(subtitle, fullWidthWithMargins(0, dp(8), 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View adhkarMasterToggle() {
        boolean enabled = SalatiSettings.nafahatEnabled(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(18), dp(16));
        row.setBackground(round(theme.panel, 12, enabled ? theme.activeBorder : theme.border));
        row.setOnClickListener(v -> {
            boolean next = !SalatiSettings.nafahatEnabled(this);
            SalatiSettings.prefs(this).edit().putBoolean(SalatiSettings.KEY_NAFAHAT_ENABLED, next).apply();
            if (next) {
                requestNotificationPermissionIfNeeded();
            }
            PrayerNotificationScheduler.scheduleAll(this);
            rebuildContent();
        });

        row.addView(switchPill(enabled), new LinearLayout.LayoutParams(dp(74), dp(42)));
        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("تشغيل الأذكار", 21, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        String subtitle = enabled
                ? "تذكير روحي خفيف " + intervalTitle(SalatiSettings.nafahatInterval(this))
                : "تذكير الأذكار متوقف";
        TextView subtitleView = label(subtitle, 13, theme.secondaryText, Typeface.BOLD);
        text.addView(subtitleView, fullWidthWithMargins(0, dp(8), 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View adhkarPanel(String title, String subtitle, View content) {
        LinearLayout panel = vertical();
        panel.setGravity(Gravity.RIGHT);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(round(theme.panel, 12, theme.border));
        elevate(panel, 2);
        TextView header = label(title, 16, theme.accent, Typeface.BOLD);
        header.setGravity(Gravity.RIGHT);
        panel.addView(header, fullWidth());
        if (subtitle != null) {
            TextView subtitleView = label(subtitle, 12, theme.secondaryText, Typeface.BOLD);
            subtitleView.setGravity(Gravity.RIGHT);
            panel.addView(subtitleView, fullWidthWithMargins(0, dp(8), 0, dp(10)));
        } else {
            View spacer = new View(this);
            panel.addView(spacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        }
        panel.addView(content, fullWidth());
        return panel;
    }

    private View nafahatSoundOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.nafahatSound(this);
        String[][] values = {
                {SalatiSettings.SOUND_NAFAHAT_1, "رسالة إشعار 1", "نغمة خفيفة وناعمة للأذكار", String.valueOf(R.drawable.ic_notification_volume)},
                {SalatiSettings.SOUND_NAFAHAT_2, "رسالة إشعار 2", "جرس قصير بدون إزعاج", String.valueOf(R.drawable.ic_tab_bell)},
                {SalatiSettings.SOUND_NAFAHAT_3, "رسالة إشعار 3", "لمعة صوتية هادئة", String.valueOf(R.drawable.ic_tab_sparkle)},
                {SalatiSettings.SOUND_NAFAHAT_4, "رسالة إشعار 4", "تنبيه لطيف ومختصر", String.valueOf(R.drawable.ic_adhkar_alarm)},
                {SalatiSettings.SOUND_SYSTEM, "صوت النظام", "تنبيه قصير وخفيف من النظام", String.valueOf(R.drawable.ic_notification_phone)}
        };
        for (int i = 0; i < values.length; i++) {
            String[] value = values[i];
            list.addView(adhkarOptionButton(value[1], value[2], Integer.parseInt(value[3]), value[0], selected, () -> {
                prefs.edit().putString(SalatiSettings.KEY_NAFAHAT_SOUND, value[0]).apply();
                PrayerNotificationScheduler.ensureChannels(this);
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }), fullWidth());
            if (i < values.length - 1) {
                list.addView(adhkarDivider(), fullWidth());
            }
        }
        return list;
    }

    private View previewNafahatPanel() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setBackground(round(theme.accent, 10, theme.activeBorder));
        button.setOnClickListener(v -> {
            v.animate().alpha(0.72f).setDuration(80)
                    .withEndAction(() -> v.animate().alpha(1f).setDuration(180).start())
                    .start();
            Toast.makeText(this, "سيصل تذكير تجريبي بعد ثانيتين", Toast.LENGTH_SHORT).show();
            sendPreviewNafahat();
        });

        ImageView icon = iconImage(R.drawable.ic_tab_sparkle, theme.primaryText, 26);
        button.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(52)));
        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("جرّب تذكير أذكار الآن", 18, theme.primaryText, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        text.addView(title, fullWidth());
        TextView subtitle = label("يوصل تذكير روحي تجريبي بعد ثانيتين", 12, AppTheme.withAlpha(theme.primaryText, 0.62), Typeface.BOLD);
        text.addView(subtitle, fullWidthWithMargins(0, dp(8), 0, 0));
        button.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return button;
    }

    private void sendPreviewNafahat() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionIfNeeded();
        }
        Handler previewHandler = new Handler(Looper.getMainLooper());
        previewHandler.postDelayed(() -> {
            NafahatContent.Message message = NafahatContent.message(SalatiSettings.nafahatText(this), 0, now);
            android.content.Intent intent = new android.content.Intent(this, PrayerNotificationReceiver.class);
            intent.setAction(PrayerNotificationScheduler.ACTION_NOTIFY);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_KIND, PrayerNotificationScheduler.KIND_NAFAHAT);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_TITLE, message.title);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_BODY, message.body);
            intent.putExtra(PrayerNotificationScheduler.EXTRA_SOUND, SalatiSettings.nafahatSound(this));
            sendBroadcast(intent);
        }, 2000);
    }

    private View nafahatIntervalOptions() {
        LinearLayout list = vertical();
        int selected = SalatiSettings.nafahatInterval(this);
        int[] values = {30, 60, 120, 180};
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            list.addView(adhkarOptionButton(intervalTitle(value), intervalSubtitle(value), R.drawable.ic_tab_clock, String.valueOf(value), String.valueOf(selected), () -> {
                prefs.edit().putInt(SalatiSettings.KEY_NAFAHAT_INTERVAL, value).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }), fullWidth());
            if (i < values.length - 1) {
                list.addView(adhkarDivider(), fullWidth());
            }
        }
        return list;
    }

    private View nafahatTextOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.nafahatText(this);
        String[][] values = {
                {"protection", "تحصين", "أذكار تحفظ القلب وتطمئنه", String.valueOf(R.drawable.ic_adhkar_shield)},
                {"gratitude", "شكر", "تذكير بالحمد والرضا", String.valueOf(R.drawable.ic_prayer_sun)},
                {"quran", "آيات وتذكير", "آيات قصيرة ومعانٍ لطيفة", String.valueOf(R.drawable.ic_adhkar_book)},
                {"lightReminders", "إشعارات خفيفة", "عبارات إيمانية قصيرة تصل بهدوء", String.valueOf(R.drawable.ic_adhkar_alarm)},
                {"mixed", "منوّع", "يتغير بين صلاة واستغفار وتسبيح ودعاء", String.valueOf(R.drawable.ic_tab_sparkle)}
        };
        for (int i = 0; i < values.length; i++) {
            String[] value = values[i];
            list.addView(adhkarOptionButton(value[1], value[2], Integer.parseInt(value[3]), value[0], selected, () -> {
                prefs.edit().putString(SalatiSettings.KEY_NAFAHAT_TEXT, value[0]).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }), fullWidth());
            if (i < values.length - 1) {
                list.addView(adhkarDivider(), fullWidth());
            }
        }
        return list;
    }

    private View quietOptions() {
        LinearLayout list = vertical();
        String selected = SalatiSettings.quietWindow(this);
        String[][] values = {
                {"none", "بدون هدوء", "تعمل الأذكار طوال اليوم", String.valueOf(R.drawable.ic_tab_bell)},
                {"lateNight", "راحة الليل", "تتوقف من 11 ليلًا إلى 6 صباحًا", String.valueOf(R.drawable.ic_prayer_moon)},
                {"midnight", "هدوء عميق", "تتوقف من 12 ليلًا إلى 7 صباحًا", String.valueOf(R.drawable.ic_prayer_moon)}
        };
        for (int i = 0; i < values.length; i++) {
            String[] value = values[i];
            list.addView(adhkarOptionButton(value[1], value[2], Integer.parseInt(value[3]), value[0], selected, () -> {
                prefs.edit().putString(SalatiSettings.KEY_NAFAHAT_QUIET, value[0]).apply();
                PrayerNotificationScheduler.scheduleAll(this);
                rebuildContent();
            }), fullWidth());
            if (i < values.length - 1) {
                list.addView(adhkarDivider(), fullWidth());
            }
        }
        return list;
    }

    private View adhkarOptionButton(String title, String subtitle, int iconResource, String value, String selectedValue, Runnable action) {
        boolean selected = value.equals(selectedValue);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(round(selected ? theme.activeRow : theme.row, 10, selected ? theme.activeBorder : Color.TRANSPARENT));
        row.setOnClickListener(v -> action.run());

        ImageView icon = iconImage(selected ? R.drawable.ic_notification_check : iconResource, selected ? theme.accent : theme.secondaryText, 22);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(64)));
        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView titleView = label(title, 19, selected ? theme.accent : theme.primaryText, Typeface.BOLD);
        titleView.setIncludeFontPadding(false);
        singleLine(titleView);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 12, theme.secondaryText, Typeface.BOLD);
        singleLine(subtitleView);
        text.addView(subtitleView, fullWidthWithMargins(0, dp(6), 0, 0));
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View adhkarDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(theme.border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(12), 0, dp(12), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private View qiblaContent() {
        LinearLayout root = pageRoot();
        root.addView(mediaPageHeader("القبلة", "اتجاه مكة من تل السبع · " + formatDegree(QiblaCalculator.telShevaBearing()), R.drawable.ic_tab_qibla), fullWidthWithMargins(0, dp(4), 0, dp(16)));

        FrameLayout compassFrame = new FrameLayout(this);
        qiblaCompass = new QiblaCompassView();
        FrameLayout.LayoutParams compassParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        compassParams.setMargins(dp(6), 0, dp(6), 0);
        compassFrame.addView(qiblaCompass, compassParams);
        root.addView(compassFrame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300)));

        qiblaInstruction = label("المؤشر المضيء يشير للقبلة", 17, theme.accent, Typeface.BOLD);
        qiblaInstruction.setGravity(Gravity.CENTER);
        root.addView(qiblaInstruction, fullWidthWithMargins(0, dp(4), 0, dp(6)));
        qiblaDifference = label("لف الهاتف بهدوء للوصول للقبلة", 21, theme.accent, Typeface.BOLD);
        qiblaDifference.setGravity(Gravity.CENTER);
        root.addView(qiblaDifference, fullWidthWithMargins(0, 0, 0, dp(12)));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        qiblaDeltaValue = label("--", 28, theme.accent, Typeface.BOLD);
        qiblaHeadingValue = label("--", 28, theme.primaryText, Typeface.BOLD);
        metrics.addView(qiblaMetricTile("الفرق المتبقي", qiblaDeltaValue), metricParams(0));
        metrics.addView(qiblaMetricTile("اتجاه الهاتف", qiblaHeadingValue), metricParams(dp(10)));
        root.addView(metrics, fullWidthWithMargins(0, 0, 0, dp(12)));

        root.addView(qiblaInfoCard(), fullWidth());
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
        if (qiblaCompass == null) {
            return;
        }
        if (heading == null) {
            qiblaCompass.setAngles(null, 0);
            if (qiblaInstruction != null) {
                qiblaInstruction.setText("المؤشر المضيء يشير للقبلة");
                qiblaInstruction.setTextColor(theme.accent);
            }
            if (qiblaDifference != null) {
                qiblaDifference.setText("حرّك الهاتف بهدوء");
            }
            if (qiblaHeadingValue != null) {
                qiblaHeadingValue.setText("--");
            }
            if (qiblaDeltaValue != null) {
                qiblaDeltaValue.setText("--");
            }
            return;
        }
        double delta = QiblaCalculator.delta(heading, QiblaCalculator.telShevaBearing());
        qiblaCompass.setAngles(heading, delta);
        boolean aligned = Math.abs(delta) <= 5;
        long absDelta = Math.round(Math.abs(delta));
        if (qiblaInstruction != null) {
            qiblaInstruction.setText(aligned ? "أنت على اتجاه القبلة" : "المؤشر المضيء يشير للقبلة");
            qiblaInstruction.setTextColor(aligned ? Color.rgb(72, 220, 112) : theme.accent);
        }
        if (qiblaDifference != null) {
            String turn = delta > 0 ? "لف يمين" : "لف يسار";
            qiblaDifference.setText(aligned ? "اتجاهك مضبوط" : turn + " " + absDelta + "° للوصول للقبلة");
        }
        if (qiblaHeadingValue != null) {
            qiblaHeadingValue.setText(formatDegree(heading));
        }
        if (qiblaDeltaValue != null) {
            qiblaDeltaValue.setText(absDelta + "°");
        }
    }

    private View radioContent() {
        LinearLayout root = pageRoot();
        root.addView(mediaPageHeader("راديو القرآن", "إذاعة القرآن الكريم من نابلس", R.drawable.ic_tab_radio), fullWidthWithMargins(0, dp(4), 0, dp(58)));
        LinearLayout panel = vertical();
        panel.setGravity(Gravity.RIGHT);
        panel.setPadding(dp(20), dp(18), dp(20), dp(20));
        panel.setBackground(round(theme.panel, 14, theme.activeBorder));
        elevate(panel, 2);

        radioStatus = label(radioStatusText(), 15, theme.secondaryText, Typeface.BOLD);
        panel.addView(radioStatus, fullWidth());
        TextView live = label("البث المباشر", 16, theme.accent, Typeface.BOLD);
        panel.addView(live, fullWidthWithMargins(0, dp(20), 0, dp(4)));
        TextView title = label("إذاعة القرآن الكريم", 34, theme.primaryText, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        panel.addView(title, fullWidthWithMargins(0, 0, 0, dp(2)));
        TextView sub = label("الصوت القريب إلى القلوب", 15, theme.secondaryText, Typeface.BOLD);
        panel.addView(sub, fullWidth());
        radioProgress = new ProgressBar(this);
        radioProgress.setIndeterminate(true);
        radioProgress.setVisibility(radioLoading ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(18), 0, 0);
        panel.addView(radioProgress, progressParams);

        radioButton = label("", 40, Color.WHITE, Typeface.BOLD);
        radioButton.setGravity(Gravity.CENTER);
        radioButton.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        radioButton.setTextDirection(View.TEXT_DIRECTION_LTR);
        radioButton.setIncludeFontPadding(false);
        radioButton.setBackground(round(theme.accent, 56, theme.accent));
        radioButton.setOnClickListener(v -> {
            bubbleDockItem(radioButton);
            toggleRadio();
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(108), dp(108));
        playParams.gravity = Gravity.CENTER_HORIZONTAL;
        playParams.setMargins(0, dp(22), 0, 0);
        panel.addView(radioButton, playParams);
        root.addView(panel, fullWidthWithMargins(0, 0, 0, dp(18)));
        root.addView(radioSourceCard(), fullWidth());
        updateRadioLabels();
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
        updateRadioLabels(radioStatusText());
    }

    private void updateRadioLabels(String status) {
        if (radioStatus != null) {
            radioStatus.setText(status.startsWith("•") ? status : "• " + status);
        }
        if (radioProgress != null) {
            radioProgress.setVisibility(radioLoading ? View.VISIBLE : View.GONE);
        }
        if (radioButton != null) {
            radioButton.setText(radioPlaying || radioLoading ? "■" : "▶");
            radioButton.setTextSize(scaledSp(radioPlaying || radioLoading ? 32 : 42));
        }
    }

    private String radioStatusText() {
        return "• " + (radioLoading ? "جار الاتصال بالبث" : (radioPlaying ? "يعمل الآن" : "جاهز للتشغيل"));
    }

    private View mediaPageHeader(String title, String subtitle, int iconResource) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(14));

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(round(theme.control, 12, theme.border));
        ImageView icon = iconImage(iconResource, theme.accent, 30);
        iconBox.addView(icon, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        row.addView(iconBox, iconParams);

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView titleView = label(title, 34, theme.primaryText, Typeface.BOLD);
        titleView.setGravity(Gravity.RIGHT);
        singleLine(titleView);
        text.addView(titleView, fullWidth());
        TextView subtitleView = label(subtitle, 14, theme.accent, Typeface.BOLD);
        subtitleView.setGravity(Gravity.RIGHT);
        singleLine(subtitleView);
        text.addView(subtitleView, fullWidth());
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View qiblaMetricTile(String title, TextView valueView) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.RIGHT);
        tile.setPadding(dp(14), dp(10), dp(14), dp(10));
        tile.setBackground(round(theme.control, 10, theme.border));
        TextView titleView = label(title, 14, theme.secondaryText, Typeface.BOLD);
        singleLine(titleView);
        tile.addView(titleView, fullWidth());
        valueView.setGravity(Gravity.RIGHT);
        valueView.setSingleLine(true);
        tile.addView(valueView, fullWidth());
        return tile;
    }

    private LinearLayout.LayoutParams metricParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private View qiblaInfoCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(theme.panel, 12, theme.activeBorder));

        ImageView check = iconImage(R.drawable.ic_notification_check, theme.accent, 24);
        card.addView(check, new LinearLayout.LayoutParams(dp(36), dp(52)));

        LinearLayout text = vertical();
        text.setGravity(Gravity.RIGHT);
        TextView title = label("دقة جيدة، أبعد الهاتف عن المعادن", 20, theme.primaryText, Typeface.BOLD);
        singleLine(title);
        text.addView(title, fullWidth());
        qiblaStatus = label("الدقة: 14° · الشمال الحقيقي", 14, theme.secondaryText, Typeface.BOLD);
        singleLine(qiblaStatus);
        text.addView(qiblaStatus, fullWidth());
        TextView hint = label("لأفضل نتيجة أبعد الهاتف عن السماعات والمغناطيس وامسكه بشكل أفقي.", 13, theme.secondaryText, Typeface.BOLD);
        hint.setMaxLines(2);
        text.addView(hint, fullWidthWithMargins(0, dp(4), 0, 0));
        card.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private View radioSourceCard() {
        LinearLayout card = vertical();
        card.setGravity(Gravity.RIGHT);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        card.setBackground(round(theme.panel, 12, theme.border));

        TextView title = label("المصدر", 18, theme.accent, Typeface.BOLD);
        card.addView(title, fullWidth());
        TextView body = label("بث مباشر من إذاعة القرآن الكريم من نابلس. يحتاج اتصال إنترنت ويستمر في الخلفية ما دام التطبيق يعمل.", 15, theme.secondaryText, Typeface.BOLD);
        body.setMaxLines(3);
        card.addView(body, fullWidthWithMargins(0, dp(12), 0, dp(12)));

        TextView button = label("فتح موقع الإذاعة", 15, theme.accent, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setBackground(round(theme.control, 10, theme.border));
        button.setOnClickListener(v -> openRadioSite());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(178), dp(48));
        buttonParams.gravity = Gravity.RIGHT;
        card.addView(button, buttonParams);
        return card;
    }

    private void openRadioSite() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://quran-radio.org/"));
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, "تعذر فتح موقع الإذاعة", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDegree(double value) {
        return String.format(java.util.Locale.US, "%.1f°", value);
    }

    private final class QiblaCompassView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Double headingValue;
        private double deltaValue;

        QiblaCompassView() {
            super(MainActivity.this);
        }

        void setAngles(Double headingValue, double deltaValue) {
            this.headingValue = headingValue;
            this.deltaValue = deltaValue;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float size = Math.min(width, height);
            float cx = width / 2f;
            float cy = height / 2f;
            float radius = size * 0.46f;

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.night ? Color.argb(55, 255, 255, 255) : Color.argb(150, 238, 247, 255));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(theme.night ? Color.argb(80, 255, 255, 255) : Color.argb(115, 55, 130, 255));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(dp(4));
            paint.setColor(theme.accent);
            canvas.drawCircle(cx, cy, radius * 0.83f, paint);

            for (int i = 0; i < 60; i++) {
                double angle = Math.toRadians(i * 6 - 90);
                boolean major = i % 5 == 0;
                float inner = radius * (major ? 0.74f : 0.79f);
                float outer = radius * 0.83f;
                paint.setStrokeWidth(major ? dp(3) : dp(2));
                paint.setColor(major ? theme.accent : AppTheme.withAlpha(theme.secondaryText, 0.55));
                canvas.drawLine(
                        cx + (float) Math.cos(angle) * inner,
                        cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer,
                        cy + (float) Math.sin(angle) * outer,
                        paint
                );
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(AppTheme.withAlpha(theme.accent, theme.night ? 0.22 : 0.18));
            canvas.drawCircle(cx, cy, radius * 0.28f, paint);

            canvas.save();
            canvas.rotate(headingValue == null ? 0f : (float) deltaValue, cx, cy);
            Path needle = new Path();
            needle.moveTo(cx, cy - radius * 0.50f);
            needle.lineTo(cx - radius * 0.15f, cy + radius * 0.32f);
            needle.lineTo(cx, cy + radius * 0.20f);
            needle.lineTo(cx + radius * 0.15f, cy + radius * 0.32f);
            needle.close();
            paint.setShader(new LinearGradient(cx, cy - radius * 0.50f, cx, cy + radius * 0.34f,
                    theme.accent, Color.rgb(226, 198, 130), Shader.TileMode.CLAMP));
            canvas.drawPath(needle, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(AppTheme.withAlpha(Color.WHITE, theme.night ? 0.64 : 0.72));
            canvas.drawLine(cx, cy - radius * 0.42f, cx, cy + radius * 0.24f, paint);
            canvas.restore();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.accent);
            canvas.drawCircle(cx, cy, radius * 0.10f, paint);
            paint.setColor(theme.panel);
            canvas.drawCircle(cx, cy, radius * 0.055f, paint);

            RectF makkahBubble = new RectF(cx - dp(38), cy - radius * 0.52f - dp(18), cx + dp(38), cy - radius * 0.52f + dp(18));
            paint.setColor(theme.night ? Color.argb(42, 255, 255, 255) : Color.argb(38, 0, 122, 255));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(makkahBubble, dp(18), dp(18), paint);
            paint.setColor(theme.accent);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(typefaceFor(Typeface.BOLD));
            paint.setTextSize(dp(18));
            canvas.drawText("مكة", cx, cy - radius * 0.52f + dp(7), paint);

            paint.setColor(AppTheme.withAlpha(theme.primaryText, 0.76));
            paint.setTextSize(dp(15));
            canvas.drawText("حرّك الهاتف بهدوء", cx, cy + radius * 0.46f, paint);
        }
    }

    private LinearLayout dock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(8), dp(4), dp(8), dp(4));
        dock.setBackground(round(theme.night ? Color.argb(146, 0, 0, 0) : Color.argb(238, 255, 255, 255), 28, scheduleTopAccentBorder()));
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
        item.setPadding(dp(2), dp(3), dp(2), dp(2));
        item.setBackground(round(selected ? theme.accent : Color.TRANSPARENT, selected ? 20 : 16, Color.TRANSPARENT));
        item.setOnClickListener(v -> {
            if (selectedTab == tab) {
                bubbleDockItem(item);
                return;
            }
            v.animate()
                    .scaleX(0.90f)
                    .scaleY(0.90f)
                    .setDuration(70)
                    .withEndAction(() -> {
                        selectedTab = tab;
                        rebuildContent();
                    })
                    .start();
        });

        if (selected) {
            item.setScaleX(0.88f);
            item.setScaleY(0.88f);
            item.setTranslationY(dp(6));
            item.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0)
                    .setDuration(260)
                    .setInterpolator(new OvershootInterpolator(1.55f))
                    .start();
        }

        ImageView icon = iconImage(tabIcon(tab), selected ? Color.WHITE : theme.secondaryText, selected ? 20 : 18);
        item.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        TextView title = label(tab.title, 9, selected ? Color.WHITE : theme.secondaryText, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, selected ? 1.18f : 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        dock.addView(item, params);
    }

    private void bubbleDockItem(View item) {
        item.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(90)
                .withEndAction(() -> item.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(new OvershootInterpolator(1.35f))
                        .start())
                .start();
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
        button.setTextSize(scaledSp(12));
        button.setTypeface(typefaceFor(Typeface.BOLD));
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
        button.setTextSize(scaledSp(12));
        button.setTypeface(typefaceFor(Typeface.BOLD));
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
        view.setTextSize(scaledSp(sp));
        view.setTextColor(color);
        view.setTypeface(typefaceFor(style));
        view.setGravity(Gravity.RIGHT);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        view.setIncludeFontPadding(true);
        return view;
    }

    private void loadAppTypeface() {
        try {
            appRegularTypeface = Typeface.createFromAsset(getAssets(), "fonts/NotoSansArabic.ttf");
            appMediumTypeface = Typeface.create(appRegularTypeface, Typeface.NORMAL);
            appStrongTypeface = Typeface.create(appRegularTypeface, Typeface.BOLD);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appMediumTypeface = new Typeface.Builder(getAssets(), "fonts/NotoSansArabic.ttf")
                        .setFontVariationSettings("'wght' 560")
                        .build();
                appStrongTypeface = new Typeface.Builder(getAssets(), "fonts/NotoSansArabic.ttf")
                        .setFontVariationSettings("'wght' 680")
                        .build();
            }
        } catch (RuntimeException ignored) {
            appRegularTypeface = null;
            appMediumTypeface = null;
            appStrongTypeface = null;
        }
    }

    private float scaledSp(int sp) {
        if (sp <= 1) {
            return sp;
        }
        return Math.max(7.25f, sp * APP_FONT_SCALE);
    }

    private Typeface typefaceFor(int style) {
        if (appRegularTypeface == null) {
            return Typeface.create(Typeface.DEFAULT, style);
        }
        if (style == Typeface.BOLD) {
            return appMediumTypeface == null ? Typeface.create(appRegularTypeface, Typeface.BOLD) : appMediumTypeface;
        }
        return appRegularTypeface;
    }

    private Typeface strongTypeface() {
        if (appStrongTypeface != null) {
            return appStrongTypeface;
        }
        return typefaceFor(Typeface.BOLD);
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

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (Color.alpha(strokeColor) > 0 && strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
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
        ADHKAR,
        THEMES
    }
}
