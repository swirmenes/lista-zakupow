package pl.listazakupow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Przycisk "Ukryj" w powiadomieniu: wyłącza listę na ekranie blokady. */
public class HideReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ListStore.setShowOnLock(context, false);
        ListNotification.update(context);
    }
}
