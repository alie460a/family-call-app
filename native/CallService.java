package com.familycall.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import org.json.JSONObject;

/**
 * Runs in the background (with a small "Waiting for calls" notification) and checks the
 * database every 5 seconds for an incoming call. When one arrives and the app is not on
 * screen, it shows an "Incoming call" notification.
 */
public class CallService extends Service {
    static volatile boolean appVisible = false;

    private volatile boolean running = false;
    private Thread worker;
    private String idToken = null;
    private String refreshToken = null;
    private long tokenExpiry = 0;
    private String lastKey = "";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannels();
        Notification n = new NotificationCompat.Builder(this, "watch")
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("Family Call")
                .setContentText("Waiting for calls")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        if (!running) {
            running = true;
            worker = new Thread(this::loop, "call-watch");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("watch", "Waiting for calls", NotificationManager.IMPORTANCE_MIN));
        NotificationChannel calls = new NotificationChannel("calls", "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
        calls.enableVibration(true);
        nm.createNotificationChannel(calls);
    }

    private void loop() {
        while (running) {
            try {
                poll();
            } catch (Exception e) {
                // network problem or similar: just try again in 5 seconds
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void poll() throws Exception {
        SharedPreferences p = getSharedPreferences("callwatch", MODE_PRIVATE);
        String name = p.getString("name", null);
        if (name == null) {
            running = false;
            stopSelf();
            return;
        }
        String id = p.getString("id", "");
        String apiKey = p.getString("apiKey", "");
        String dbUrl = p.getString("dbUrl", "");

        ensureToken(apiKey, name, id);

        URL url = new URL(dbUrl + "/rooms/main/inbox/" + name + ".json?auth=" + URLEncoder.encode(idToken, "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        int code = c.getResponseCode();
        if (code == 401) {
            idToken = null;
            return;
        }
        if (code != 200) return;
        long serverNow = c.getDate();
        if (serverNow == 0) serverNow = System.currentTimeMillis();
        String body = read(c.getInputStream());
        if (body.equals("null") || body.isEmpty()) return;

        JSONObject all = new JSONObject(body);
        Iterator<String> keys = all.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject m = all.optJSONObject(key);
            if (m == null || !"offer".equals(m.optString("type"))) continue;
            long ts = m.optLong("ts", 0);
            if (ts == 0 || serverNow - ts > 45000) continue; // too old
            if (key.equals(lastKey)) continue; // already notified
            lastKey = key;
            if (!appVisible) notifyCall(m.optString("from", "Someone"));
        }
    }

    private void notifyCall(String from) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, "calls")
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("Incoming call")
                .setContentText(from + " is calling you")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setTimeoutAfter(45000)
                .build();
        try {
            NotificationManagerCompat.from(this).notify(2, n);
        } catch (SecurityException e) {
            // notifications are not allowed for this app
        }
    }

    private String read(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private JSONObject post(String address, String contentType, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", contentType);
        OutputStream o = c.getOutputStream();
        o.write(body.getBytes("UTF-8"));
        o.close();
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return new JSONObject(read(c.getInputStream()));
    }

    private void ensureToken(String apiKey, String name, String id) throws Exception {
        long now = System.currentTimeMillis();
        if (idToken != null && now < tokenExpiry - 60000) return;
        if (refreshToken != null) {
            try {
                JSONObject r = post("https://securetoken.googleapis.com/v1/token?key=" + apiKey,
                        "application/x-www-form-urlencoded",
                        "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8"));
                idToken = r.getString("id_token");
                refreshToken = r.getString("refresh_token");
                tokenExpiry = now + Long.parseLong(r.getString("expires_in")) * 1000;
                return;
            } catch (Exception e) {
                refreshToken = null; // fall back to a fresh sign-in
            }
        }
        JSONObject b = new JSONObject();
        b.put("email", name + "@familycall.app");
        b.put("password", id);
        b.put("returnSecureToken", true);
        JSONObject r = post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + apiKey,
                "application/json", b.toString());
        idToken = r.getString("idToken");
        refreshToken = r.getString("refreshToken");
        tokenExpiry = now + Long.parseLong(r.getString("expiresIn")) * 1000;
    }
}
