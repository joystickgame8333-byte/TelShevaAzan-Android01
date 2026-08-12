package com.example.telshevaazan;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class PrayerNotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (PrayerNotificationScheduler.ACTION_RESCHEDULE.equals(intent.getAction())
                || Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_DATE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            PrayerNotificationScheduler.scheduleAll(context);
            SalatiWidgetUpdater.updateAll(context);
            return;
        }

        String title = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_TITLE);
        String body = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_BODY);
        String kind = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_KIND);
        String sound = intent.getStringExtra(PrayerNotificationScheduler.EXTRA_SOUND);
        if (title == null) {
            title = "صلاتي";
        }
        if (body == null) {
            body = "";
        }

        boolean silentKind = PrayerNotificationScheduler.KIND_NAFAHAT.equals(kind)
                || PrayerNotificationScheduler.KIND_IQAMA.equals(kind);
        String resolvedSound = silentKind ? null : (sound == null ? SalatiSettings.SOUND_ADHAN : sound);

        PrayerNotificationScheduler.ensureChannel(context, kind, resolvedSound);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                104,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channel = PrayerNotificationScheduler.channelId(kind, resolvedSound);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);

        Uri soundUri = silentKind ? null : PrayerNotificationScheduler.soundUri(context, resolvedSound);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT < 26) {
            if (silentKind) {
                builder.setSound(null);
                builder.setVibrate(new long[]{0, 180});
            } else {
                builder.setSound(soundUri);
            }
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());
        }

        PrayerNotificationScheduler.scheduleAll(context);
        SalatiWidgetUpdater.updateAll(context);
    }
}
