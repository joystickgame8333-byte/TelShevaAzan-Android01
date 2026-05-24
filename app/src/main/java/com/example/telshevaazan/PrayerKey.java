package com.example.telshevaazan;

enum PrayerKey {
    FAJR("fajr", "الفجر", "للفجر", "الشروق"),
    SUNRISE("sunrise", "الشروق", "للشروق", "الشمس"),
    DHUHR("dhuhr", "الظهر", "للظهر", "الظهر"),
    ASR("asr", "العصر", "للعصر", "العصر"),
    MAGHRIB("maghrib", "المغرب", "للمغرب", "الغروب"),
    ISHA("isha", "العشاء", "للعشاء", "الليل");

    final String rawValue;
    final String title;
    final String targetLabel;
    final String iconLabel;

    PrayerKey(String rawValue, String title, String targetLabel, String iconLabel) {
        this.rawValue = rawValue;
        this.title = title;
        this.targetLabel = targetLabel;
        this.iconLabel = iconLabel;
    }

    static PrayerKey fromRawValue(String value) {
        for (PrayerKey key : values()) {
            if (key.rawValue.equals(value)) {
                return key;
            }
        }
        return FAJR;
    }
}
