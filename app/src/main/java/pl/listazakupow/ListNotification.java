package pl.listazakupow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** Stałe powiadomienie z listą, widoczne na ekranie blokady. */
public final class ListNotification {
    private static final String CHANNEL = "lista_zakupow";
    private static final int ID = 1;

    private ListNotification() {}

    public static void update(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(c, nm);

        List<String> todo = new ArrayList<>();
        for (ListStore.Item it : ListStore.load(c)) {
            if (!it.done) todo.add(it.text);
        }
        if (todo.isEmpty()) {
            nm.cancel(ID);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        StringBuilder big = new StringBuilder();
        for (int i = 0; i < todo.size(); i++) {
            if (i > 0) big.append('\n');
            big.append("• ").append(todo.get(i));
        }
        String shortText = String.join(", ", todo);

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(c.getString(R.string.notif_title, todo.size()))
                .setContentText(shortText)
                .setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(pi)
                .build();
        nm.notify(ID, n);
    }

    private static void ensureChannel(Context c, NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        // Ważność domyślna (nie "cicha"), bo część telefonów ukrywa ciche
        // powiadomienia na ekranie blokady. Dźwięk i wibracje wyłączone.
        NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.channel_desc));
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }
}
