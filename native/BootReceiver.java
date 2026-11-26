package com.familycall.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

// Restarts the background call check after the phone restarts.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context.getSharedPreferences("callwatch", Context.MODE_PRIVATE).getString("name", null) != null) {
            ContextCompat.startForegroundService(context, new Intent(context, CallService.class));
        }
    }
}
