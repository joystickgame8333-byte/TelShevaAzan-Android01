package com.example.telshevaazan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

final class NafahatContent {
    static final class Message {
        final String title;
        final String body;

        Message(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    private NafahatContent() {}

    static Message message(String type, int index, Date date) {
        List<Message> messages = messages(type);
        if (messages.isEmpty()) {
            return new Message("ذكر خفيف", "اذكر الله ذكرًا خفيفًا");
        }
        int resolvedIndex = index;
        if (SalatiSettings.TEXT_MIXED.equals(type)) {
            resolvedIndex = Math.abs((int) (date.getTime() / 60000L) + index * 13);
        }
        return messages.get(resolvedIndex % messages.size());
    }

    static List<Message> messages(String type) {
        switch (type) {
            case "salawat":
                return Arrays.asList(
                        new Message("صلِّ على النبي", "اللهم صل وسلم وبارك على نبينا محمد"),
                        new Message("صلاة وسلام", "اللهم صل على محمد وعلى آل محمد"),
                        new Message("ذكر الصلاة", "صلِّ على النبي بقلب حاضر"),
                        new Message("محبة النبي", "اللهم اجعل صلاتنا عليه نورًا وطمأنينة")
                );
            case "istighfar":
                return Arrays.asList(
                        new Message("استغفار", "أستغفر الله العظيم وأتوب إليه"),
                        new Message("باب التوبة", "رب اغفر لي وتب علي إنك أنت التواب الرحيم"),
                        new Message("رجوع إلى الله", "اللهم اغفر لي ذنبي كله دقه وجله"),
                        new Message("استغفار خفيف", "أستغفر الله الذي لا إله إلا هو الحي القيوم وأتوب إليه")
                );
            case "tasbih":
                return Arrays.asList(
                        new Message("تسبيح", "سبحان الله وبحمده، سبحان الله العظيم"),
                        new Message("ذكر خفيف", "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر"),
                        new Message("حمد وتسبيح", "الحمد لله رب العالمين"),
                        new Message("ذكر طيب", "لا حول ولا قوة إلا بالله")
                );
            case "dua":
                return Arrays.asList(
                        new Message("دعاء خفيف", "اللهم أعني على ذكرك وشكرك وحسن عبادتك"),
                        new Message("يا رب", "اللهم آت نفسي تقواها وزكها أنت خير من زكاها"),
                        new Message("راحة القلب", "اللهم اجعل لي من كل هم فرجًا ومن كل ضيق مخرجًا"),
                        new Message("ثبات", "يا مقلب القلوب ثبت قلبي على دينك"),
                        new Message("نور", "اللهم اجعل في قلبي نورًا وفي سمعي نورًا وفي بصري نورًا")
                );
            case "protection":
                return Arrays.asList(
                        new Message("تحصين", "بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء"),
                        new Message("كفاية", "حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم"),
                        new Message("طمأنينة", "أعوذ بكلمات الله التامات من شر ما خلق"),
                        new Message("حفظ", "اللهم احفظني من بين يدي ومن خلفي وعن يميني وعن شمالي")
                );
            case "gratitude":
                return Arrays.asList(
                        new Message("شكر", "الحمد لله حمدًا كثيرًا طيبًا مباركًا فيه"),
                        new Message("نعمة", "اللهم لك الحمد كما ينبغي لجلال وجهك وعظيم سلطانك"),
                        new Message("رضا", "رضيت بالله ربًا وبالإسلام دينًا وبمحمد صلى الله عليه وسلم نبيًا")
                );
            case "quran":
                return Arrays.asList(
                        new Message("تذكير قرآني", "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ"),
                        new Message("واذكر ربك", "وَاذْكُر رَّبَّكَ إِذَا نَسِيتَ"),
                        new Message("نور", "اللَّهُ نُورُ السَّمَاوَاتِ وَالْأَرْضِ"),
                        new Message("سعة", "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا")
                );
            case "lightReminders":
                return Arrays.asList(
                        new Message("ذكر خفيف", "سبحان الله وبحمده"),
                        new Message("استغفار", "أستغفر الله وأتوب إليه"),
                        new Message("دعاء قصير", "اللهم أعني على ذكرك وشكرك وحسن عبادتك"),
                        new Message("طمأنينة", "لا حول ولا قوة إلا بالله"),
                        new Message("دعوة جامعة", "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة")
                );
            case "mixed":
            default:
                List<Message> mixed = new ArrayList<>();
                mixed.addAll(messages("salawat"));
                mixed.addAll(messages("istighfar"));
                mixed.addAll(messages("tasbih"));
                mixed.addAll(messages("dua"));
                mixed.addAll(messages("protection"));
                mixed.addAll(messages("gratitude"));
                mixed.addAll(messages("quran"));
                mixed.addAll(messages("lightReminders"));
                return mixed;
        }
    }
}
