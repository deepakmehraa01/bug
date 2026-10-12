package com.example.activitymonitor;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        String base = Store.get(context, "server");
        String token = Store.get(context, "token");
        if (!"true".equals(Store.get(context, "syncEnabled"))
                || !base.startsWith("https://") || token.length() < 24)
            return Result.failure();
        HttpURLConnection connection = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("deviceId", Store.get(context, "deviceId"));
            payload.put("recordedAt", System.currentTimeMillis());
            payload.put("apps", UsageReader.today(context));
            // Built-in browser history is sensitive and MUST be explicitly opted into.
            if ("true".equals(Store.get(context, "sendBrowserHistory"))) {
                String history = Store.get(context, "history");
                payload.put("browserHistory", new org.json.JSONArray(
                        history.isEmpty() ? "[]" : history));
            }

            URL url = new URL(base.replaceAll("/+$", "") + "/api/report");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Agent-Token", token);
            connection.setDoOutput(true);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code == 200) {
                Store.put(context, "lastSync", String.valueOf(System.currentTimeMillis()));
                Store.put(context, "lastError", "");
                return Result.success();
            }
            Store.put(context, "lastError", "Server HTTP " + code);
            return code >= 500 || code == 429 ? Result.retry() : Result.failure();
        } catch (SecurityException ex) {
            Store.put(context, "lastError", "Usage Access revoked");
            return Result.failure();
        } catch (Exception ex) {
            Store.put(context, "lastError", "Connection unavailable or TLS failed");
            return Result.retry();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
