package com.example.activitymonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private TextView output;
    private EditText server, token;
    private CheckBox sendHistory;

    private void show(String text) { output.setText(text); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Store.get(this, "deviceId").isEmpty())
            Store.put(this, "deviceId", UUID.randomUUID().toString());

        LinearLayout root = new LinearLayout(this);
        root.setPadding(28, 28, 28, 28);
        root.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Activity Monitor — Phone A");
        title.setTextSize(24);
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Only enable reporting on a device you own or are authorized to manage. Usage Access and your explicit approval are required. You can stop reporting here.");
        explanation.setPadding(0, 14, 0, 16);
        root.addView(explanation);

        button("Open Android Usage Access settings", root)
                .setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        output = new TextView(this);
        output.setTextSize(15);
        output.setTextIsSelectable(true);
        output.setPadding(0, 14, 0, 14);
        root.addView(output);

        button("Show usage on this phone", root).setOnClickListener(v -> {
            try {
                show("Today's app usage (foreground milliseconds):\n" + UsageReader.today(this).toString(2));
            } catch (Exception ex) {
                show("Usage Access is required: " + ex.getMessage());
            }
        });

        button("Open built-in browser", root)
                .setOnClickListener(v -> startActivity(new Intent(this, BrowserActivity.class)));
        button("Show local browser history", root)
                .setOnClickListener(v -> show(Store.get(this, "history")));

        server = new EditText(this);
        server.setHint("HTTPS server URL");
        server.setSingleLine(true);
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(Store.get(this, "server"));
        root.addView(server);

        token = new EditText(this);
        token.setHint("Agent token (different from viewer token)");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(Store.get(this, "token"));
        root.addView(token);

        sendHistory = new CheckBox(this);
        sendHistory.setText("Also share history from this app's built-in browser (optional)");
        sendHistory.setChecked("true".equals(Store.get(this, "sendBrowserHistory")));
        root.addView(sendHistory);

        button("Review consent and enable reporting", root).setOnClickListener(v -> {
            String url = server.getText().toString().trim();
            String secret = token.getText().toString().trim();
            if (!url.startsWith("https://") || url.contains("?") || url.contains("#")
                    || url.contains("@") || secret.length() < 24 || !UsageReader.allowed(this)) {
                show("A valid HTTPS URL, agent token (24+ characters), and Android Usage Access are required.");
                return;
            }
            String shared = "App package names, foreground usage times, last-used timestamps and device identifier.";
            if (sendHistory.isChecked())
                shared += "\n\nAdditionally: URLs visited inside Activity Monitor's built-in browser.";
            new AlertDialog.Builder(this)
                    .setTitle("Enable periodic device reporting?")
                    .setMessage("Data sent to " + url + ":\n\n" + shared
                            + "\n\nReporting runs periodically when network is available. Stop it any time here. The server's viewer token holders can read reports.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("I agree — enable", (dialog, which) -> {
                        Store.put(this, "server", url);
                        Store.put(this, "token", secret);
                        Store.put(this, "sendBrowserHistory", sendHistory.isChecked() ? "true" : "false");
                        Store.put(this, "syncEnabled", "true");
                        Constraints constraints = new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED).build();
                        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                                SyncWorker.class, 15, TimeUnit.MINUTES)
                                .setConstraints(constraints).build();
                        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                                "usage_sync", ExistingPeriodicWorkPolicy.UPDATE, request);
                        showStatus();
                    }).show();
        });

        button("Sync now", root).setOnClickListener(v -> {
            if (!"true".equals(Store.get(this, "syncEnabled"))
                    || Store.get(this, "token").isEmpty()
                    || !UsageReader.allowed(this)) {
                show("Enable reporting and Usage Access first.");
                return;
            }
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(constraints).build();
            WorkManager.getInstance(this).enqueueUniqueWork(
                    "usage_sync_once", ExistingWorkPolicy.REPLACE, request);
            show("One-time report queued. Android executes it when network is available.");
        });

        button("Stop reporting and revoke local agent token", root).setOnClickListener(v -> {
            WorkManager.getInstance(this).cancelUniqueWork("usage_sync");
            WorkManager.getInstance(this).cancelUniqueWork("usage_sync_once");
            Store.put(this, "syncEnabled", "false");
            Store.put(this, "token", "");
            token.setText("");
            showStatus();
        });

        button("Clear local in-app browser history", root).setOnClickListener(v -> {
            Store.put(this, "history", "[]");
            show("Local browser history cleared. Already submitted reports must be removed separately from the server.");
        });
        button("Refresh reporting status", root).setOnClickListener(v -> showStatus());
        showStatus();
        setContentView(scroll);
    }

    private void showStatus() {
        String enabled = "true".equals(Store.get(this, "syncEnabled")) ? "ENABLED" : "OFF";
        String last = Store.get(this, "lastSync");
        String lastError = Store.get(this, "lastError");
        String lastText = "never";
        try {
            if (!last.isEmpty())
                lastText = DateFormat.getDateTimeInstance().format(new Date(Long.parseLong(last)));
        } catch (NumberFormatException ignored) {}
        show("Device ID: " + Store.get(this, "deviceId")
                + "\nPeriodic reporting: " + enabled
                + "\nUsage Access: " + (UsageReader.allowed(this) ? "granted" : "not granted")
                + "\nBrowser URL sharing: "
                + ("true".equals(Store.get(this, "sendBrowserHistory")) ? "opted in" : "off")
                + "\nLast successful sync: " + lastText
                + (lastError.isEmpty() ? "" : "\nLast sync issue: " + lastError));
    }

    private Button button(String text, LinearLayout root) {
        Button button = new Button(this);
        button.setText(text);
        root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }
}
