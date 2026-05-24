package com.example.telshevaazan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

final class AppTheme {
    static final String PREFS = "salati_android";
    static final String NIGHT_THEME_KEY = "selectedNightTheme";
    static final String DAY_THEME_KEY = "selectedDayTheme";
    static final String DEFAULT_NIGHT = "night_salati_glass";
    static final String DEFAULT_DAY = "day_salati_glass";

    private AppTheme() {}

    static ThemePalette selected(Context context) {
        boolean night = isSystemNight(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(night ? NIGHT_THEME_KEY : DAY_THEME_KEY, night ? DEFAULT_NIGHT : DEFAULT_DAY);
        ThemePalette theme = find(id, night);
        return theme != null ? theme : (night ? nightChoices()[0] : dayChoices()[0]);
    }

    static ThemePalette[] nightChoices() {
        return new ThemePalette[]{
                nightSalati(),
                nightApple(),
                nightSakina()
        };
    }

    static ThemePalette[] dayChoices() {
        return new ThemePalette[]{
                daySalati(),
                dayApple(),
                dayOasis()
        };
    }

    static void select(Context context, ThemePalette theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(theme.night ? NIGHT_THEME_KEY : DAY_THEME_KEY, theme.id)
                .apply();
    }

    static boolean isSystemNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static GradientDrawable backgroundGradient(ThemePalette theme) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                new int[]{theme.backgroundTop, theme.backgroundMiddle, theme.backgroundBottom}
        );
    }

    static GradientDrawable rounded(int color, float radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (Color.alpha(strokeColor) > 0) {
            drawable.setStroke(1, strokeColor);
        }
        return drawable;
    }

    static int rgb(double red, double green, double blue) {
        return Color.rgb((int) Math.round(red * 255), (int) Math.round(green * 255), (int) Math.round(blue * 255));
    }

    static int argb(double alpha, double red, double green, double blue) {
        return Color.argb(
                (int) Math.round(alpha * 255),
                (int) Math.round(red * 255),
                (int) Math.round(green * 255),
                (int) Math.round(blue * 255)
        );
    }

    static int withAlpha(int color, double alpha) {
        return Color.argb((int) Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static ThemePalette find(String id, boolean night) {
        ThemePalette[] choices = night ? nightChoices() : dayChoices();
        for (ThemePalette theme : choices) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return null;
    }

    private static ThemePalette nightSalati() {
        return new ThemePalette(
                "night_salati_glass",
                "ليل صلاتي",
                "نمط الليل",
                rgb(0.002, 0.010, 0.018),
                rgb(0.014, 0.036, 0.062),
                rgb(0.018, 0.084, 0.112),
                rgb(0.050, 0.540, 1.000),
                Color.WHITE,
                rgb(0.800, 0.885, 0.950),
                withAlpha(Color.WHITE, 0.82),
                argb(0.108, 1, 1, 1),
                argb(0.074, 1, 1, 1),
                argb(0.235, 0.050, 0.540, 1.000),
                argb(0.150, 1, 1, 1),
                argb(0.660, 0.360, 0.730, 1.000),
                argb(0.130, 1, 1, 1),
                argb(0.075, 1, 1, 1),
                argb(0.940, 0.020, 0.300, 0.660),
                true
        );
    }

    private static ThemePalette nightApple() {
        return new ThemePalette(
                "night_apple_glass",
                "نبوي ليلي",
                "نمط الليل",
                rgb(0.010, 0.020, 0.034),
                rgb(0.020, 0.050, 0.086),
                rgb(0.012, 0.030, 0.052),
                rgb(0.050, 0.520, 1.000),
                Color.WHITE,
                rgb(0.780, 0.860, 0.930),
                withAlpha(Color.WHITE, 0.80),
                argb(0.124, 1, 1, 1),
                argb(0.086, 1, 1, 1),
                argb(0.230, 0.050, 0.520, 1.000),
                argb(0.180, 1, 1, 1),
                argb(0.620, 0.300, 0.690, 1.000),
                argb(0.138, 1, 1, 1),
                argb(0.080, 1, 1, 1),
                argb(0.960, 0.030, 0.320, 0.660),
                true
        );
    }

    private static ThemePalette nightSakina() {
        return new ThemePalette(
                "night_sakina_glass",
                "نبوي دافئ",
                "نمط الليل",
                rgb(0.018, 0.026, 0.028),
                rgb(0.040, 0.062, 0.060),
                rgb(0.018, 0.030, 0.032),
                rgb(1.000, 0.780, 0.360),
                Color.WHITE,
                rgb(0.870, 0.840, 0.740),
                withAlpha(Color.WHITE, 0.80),
                argb(0.118, 1, 1, 1),
                argb(0.080, 1, 1, 1),
                argb(0.180, 1.000, 0.780, 0.360),
                argb(0.150, 1, 1, 1),
                argb(0.520, 1.000, 0.780, 0.360),
                argb(0.130, 1, 1, 1),
                argb(0.080, 1, 1, 1),
                argb(0.920, 0.420, 0.280, 0.080),
                true
        );
    }

    private static ThemePalette daySalati() {
        return new ThemePalette(
                "day_salati_glass",
                "زجاج صلاتي",
                "نمط النهار",
                rgb(0.965, 0.987, 1.000),
                rgb(0.995, 0.998, 1.000),
                rgb(0.900, 0.955, 0.988),
                rgb(0.000, 0.478, 1.000),
                rgb(0.018, 0.030, 0.045),
                rgb(0.255, 0.325, 0.405),
                argb(0.880, 0.290, 0.355, 0.430),
                argb(0.720, 1, 1, 1),
                argb(0.580, 1, 1, 1),
                argb(0.135, 0.000, 0.478, 1.000),
                argb(0.860, 1, 1, 1),
                argb(0.500, 0.000, 0.478, 1.000),
                argb(0.760, 1, 1, 1),
                argb(0.560, 1, 1, 1),
                argb(0.920, 0.000, 0.478, 1.000),
                false
        );
    }

    private static ThemePalette dayApple() {
        return new ThemePalette(
                "day_apple_glass",
                "نبوي أبيض",
                "نمط النهار",
                rgb(0.986, 0.988, 0.992),
                rgb(0.930, 0.958, 1.000),
                rgb(1.000, 1.000, 1.000),
                rgb(0.000, 0.478, 1.000),
                rgb(0.020, 0.024, 0.030),
                rgb(0.260, 0.320, 0.390),
                argb(0.880, 0.300, 0.350, 0.420),
                argb(0.760, 1, 1, 1),
                argb(0.620, 1, 1, 1),
                argb(0.122, 0.000, 0.478, 1.000),
                argb(0.880, 1, 1, 1),
                argb(0.460, 0.000, 0.478, 1.000),
                argb(0.780, 1, 1, 1),
                argb(0.580, 1, 1, 1),
                argb(0.920, 0.000, 0.478, 1.000),
                false
        );
    }

    private static ThemePalette dayOasis() {
        return new ThemePalette(
                "day_oasis_glass",
                "نبوي سماوي",
                "نمط النهار",
                rgb(0.900, 0.985, 0.980),
                rgb(0.990, 0.980, 0.900),
                rgb(0.740, 0.900, 0.955),
                rgb(0.020, 0.345, 0.405),
                rgb(0.000, 0.075, 0.088),
                rgb(0.105, 0.355, 0.375),
                argb(0.880, 0.090, 0.250, 0.285),
                argb(0.700, 1, 1, 1),
                argb(0.580, 1, 1, 1),
                argb(0.180, 0.050, 0.410, 0.475),
                argb(0.820, 1, 1, 1),
                argb(0.500, 0.020, 0.345, 0.405),
                argb(0.720, 1, 1, 1),
                argb(0.540, 1, 1, 1),
                argb(0.950, 0.025, 0.315, 0.390),
                false
        );
    }
}
