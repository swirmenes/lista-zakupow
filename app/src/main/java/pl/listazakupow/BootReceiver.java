package pl.listazakupow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Przywraca powiadomienie z listą po restarcie telefonu i po aktualizacji aplikacji. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ListNotification.update(context);
    }
}
