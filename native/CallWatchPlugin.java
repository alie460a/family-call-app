package com.familycall.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "CallWatch")
public class CallWatchPlugin extends Plugin {

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("callwatch", Context.MODE_PRIVATE);
    }

    // start({name, id, apiKey, dbUrl}) saves the login and starts the background check.
    // start({}) just makes sure it is running with the login saved earlier.
    @PluginMethod
    public void start(PluginCall call) {
        Context c = getContext();
        String name = call.getString("name", "");
        if (name != null && !name.isEmpty()) {
            prefs().edit()
                    .putString("name", name)
                    .putString("id", call.getString("id", ""))
                    .putString("apiKey", call.getString("apiKey", ""))
                    .putString("dbUrl", call.getString("dbUrl", ""))
                    .apply();
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (prefs().getString("name", null) != null) {
            ContextCompat.startForegroundService(c, new Intent(c, CallService.class));
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        prefs().edit().clear().apply();
        getContext().stopService(new Intent(getContext(), CallService.class));
        call.resolve();
    }

    @PluginMethod
    public void batteryHelp(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }
}
