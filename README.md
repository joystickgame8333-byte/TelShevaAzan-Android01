# TelShevaAzan Android

Current app version: `0.2.1 (3)`

تطبيق Android أولي لتل السبع، بنفس فكرة نسخة iPhone:

- مواقيت اليوم
- الصلاة القادمة
- عداد تنازلي
- مضى على الصلاة السابقة
- آية قرآنية أعلى الشاشة
- أنماط نهار/ليل متعددة تتبع وضع النظام
- أزرار اليوم السابق / اليوم / اليوم التالي
- بيانات مايو 2026 كنموذج أولي
- المنطق: توقيت المسجد الأقصى الدهري + دقيقتين لتل السبع/بئر السبع + التوقيت الصيفي

## بناء APK بدون Android Studio

ارفع هذا المجلد إلى GitHub repository، ثم:

```text
Actions > Build Android APK > Run workflow
```

بعد نجاح البناء نزّل artifact:

```text
TelShevaAzan-debug-apk
```

افتحه وستجد:

```text
app-debug.apk
```

انسخ الملف لهاتف Android وثبته.
