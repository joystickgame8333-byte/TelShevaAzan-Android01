package com.example.telshevaazan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

final class AdhkarLibrary {
    enum Category {
        MORNING("morning", "الصباح", "ابدأ يومك بذكر الله", R.drawable.ic_prayer_fajr),
        EVENING("evening", "المساء", "اختم يومك بالطمأنينة", R.drawable.ic_prayer_maghrib),
        AFTER_PRAYER("afterPrayer", "بعد الصلاة", "أذكار ما بعد الفريضة", R.drawable.ic_adhkar_shield),
        SLEEP("sleep", "النوم", "أذكار قبل النوم", R.drawable.ic_prayer_moon),
        WAKING("waking", "الاستيقاظ", "ما يقال عند الاستيقاظ", R.drawable.ic_prayer_sun);

        final String id;
        final String title;
        final String subtitle;
        final int icon;

        Category(String id, String title, String subtitle, int icon) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    static final class Item {
        final String id;
        final String title;
        final String text;
        final int target;
        final String source;
        final String note;

        Item(String id, String title, String text, int target, String source) {
            this(id, title, text, target, source, null);
        }

        Item(String id, String title, String text, int target, String source, String note) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.target = target;
            this.source = source;
            this.note = note;
        }
    }

    private static final String AYAT_KURSI = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ، لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ، لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ، مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ، يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ، وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ، وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ، وَلَا يَئُودُهُ حِفْظُهُمَا، وَهُوَ الْعَلِيُّ الْعَظِيمُ.";
    private static final String IKHLAS = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ.";
    private static final String FALAQ = "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِنْ شَرِّ مَا خَلَقَ، وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ.";
    private static final String NAS = "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَٰهِ النَّاسِ، مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ.";
    private static final String SAYYID = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي؛ فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.";

    private AdhkarLibrary() {}

    static Category suggestedNow() {
        int hour = Calendar.getInstance(PrayerEngine.TIME_ZONE).get(Calendar.HOUR_OF_DAY);
        if (hour >= 4 && hour < 12) return Category.MORNING;
        if (hour >= 16) return Category.EVENING;
        if (hour < 4) return Category.SLEEP;
        return Category.AFTER_PRAYER;
    }

    static List<Item> items(Category category) {
        switch (category) {
            case MORNING: return morning();
            case EVENING: return evening();
            case AFTER_PRAYER: return afterPrayer();
            case SLEEP: return sleep();
            case WAKING: return waking();
            default: return Collections.emptyList();
        }
    }

    private static List<Item> shared(String prefix) {
        return Arrays.asList(
                new Item(prefix + "ayat-kursi", "آية الكرسي", AYAT_KURSI, 1, "سورة البقرة · الآية 255"),
                new Item(prefix + "ikhlas", "سورة الإخلاص", IKHLAS, 3, "أبو داود والترمذي"),
                new Item(prefix + "falaq", "سورة الفلق", FALAQ, 3, "أبو داود والترمذي"),
                new Item(prefix + "nas", "سورة الناس", NAS, 3, "أبو داود والترمذي"),
                new Item(prefix + "sayyid", "سيد الاستغفار", SAYYID, 1, "رواه البخاري"),
                new Item(prefix + "protection", "حفظ من الضرر", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "رواه أبو داود والترمذي"),
                new Item(prefix + "raditu", "رضيت بالله ربًا", "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا.", 3, "رواه أحمد وأبو داود والترمذي"),
                new Item(prefix + "subhanallah", "تسبيح اليوم", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "رواه مسلم")
        );
    }

    private static List<Item> morning() {
        List<Item> result = new ArrayList<>();
        result.add(new Item("morning-opening", "أصبحنا والملك لله", "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ. رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.", 1, "رواه مسلم"));
        result.addAll(shared("morning-"));
        return result;
    }

    private static List<Item> evening() {
        List<Item> result = new ArrayList<>();
        result.add(new Item("evening-opening", "أمسينا والملك لله", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا. رَبِّ أَعُوذُ بِكَ مِنَ الْكَسَلِ وَسُوءِ الْكِبَرِ، رَبِّ أَعُوذُ بِكَ مِنْ عَذَابٍ فِي النَّارِ وَعَذَابٍ فِي الْقَبْرِ.", 1, "رواه مسلم"));
        result.addAll(shared("evening-"));
        return result;
    }

    private static List<Item> afterPrayer() {
        return Arrays.asList(
                new Item("prayer-istighfar", "الاستغفار", "أَسْتَغْفِرُ اللَّهَ.", 3, "رواه مسلم"),
                new Item("prayer-salam", "اللهم أنت السلام", "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ.", 1, "رواه مسلم"),
                new Item("prayer-ayat-kursi", "آية الكرسي", AYAT_KURSI, 1, "سورة البقرة · الآية 255"),
                new Item("prayer-tahlil", "التهليل", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ، وَلَا مُعْطِيَ لِمَا مَنَعْتَ، وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ.", 1, "متفق عليه"),
                new Item("prayer-subhanallah", "التسبيح", "سُبْحَانَ اللَّهِ.", 33, "رواه مسلم"),
                new Item("prayer-alhamdulillah", "التحميد", "الْحَمْدُ لِلَّهِ.", 33, "رواه مسلم"),
                new Item("prayer-allahu-akbar", "التكبير", "اللَّهُ أَكْبَرُ.", 33, "رواه مسلم", "ثم تمام المئة: لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير.")
        );
    }

    private static List<Item> sleep() {
        return Arrays.asList(
                new Item("sleep-name", "باسمك أموت وأحيا", "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا.", 1, "رواه البخاري"),
                new Item("sleep-ayat-kursi", "آية الكرسي", AYAT_KURSI, 1, "رواه البخاري"),
                new Item("sleep-muawwidhat", "المعوذات", IKHLAS + "\n\n" + FALAQ + "\n\n" + NAS, 3, "رواه البخاري", "اجمع كفيك، وانفث فيهما، واقرأ السور ثم امسح ما استطعت من جسدك."),
                new Item("sleep-surrender", "دعاء النوم", "اللَّهُمَّ أَسْلَمْتُ نَفْسِي إِلَيْكَ، وَفَوَّضْتُ أَمْرِي إِلَيْكَ، وَوَجَّهْتُ وَجْهِي إِلَيْكَ، وَأَلْجَأْتُ ظَهْرِي إِلَيْكَ، رَغْبَةً وَرَهْبَةً إِلَيْكَ، لَا مَلْجَأَ وَلَا مَنْجَا مِنْكَ إِلَّا إِلَيْكَ، آمَنْتُ بِكِتَابِكَ الَّذِي أَنْزَلْتَ، وَبِنَبِيِّكَ الَّذِي أَرْسَلْتَ.", 1, "متفق عليه"),
                new Item("sleep-tasbih", "تسبيح فاطمة", "سُبْحَانَ اللَّهِ 33، وَالْحَمْدُ لِلَّهِ 33، وَاللَّهُ أَكْبَرُ 34.", 1, "متفق عليه")
        );
    }

    private static List<Item> waking() {
        return Arrays.asList(
                new Item("waking-praise", "الحمد لله الذي أحيانا", "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ.", 1, "متفق عليه"),
                new Item("waking-body", "الحمد لله الذي عافاني", "الْحَمْدُ لِلَّهِ الَّذِي عَافَانِي فِي جَسَدِي، وَرَدَّ عَلَيَّ رُوحِي، وَأَذِنَ لِي بِذِكْرِهِ.", 1, "رواه الترمذي"),
                new Item("waking-tawhid", "ذكر الاستيقاظ ليلًا", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ. الْحَمْدُ لِلَّهِ، وَسُبْحَانَ اللَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ. اللَّهُمَّ اغْفِرْ لِي.", 1, "رواه البخاري")
        );
    }
}
