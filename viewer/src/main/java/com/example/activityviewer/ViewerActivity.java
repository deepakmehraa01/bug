package com.example.activityviewer;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Read-only viewer for reports from devices that explicitly enabled reporting.
 * No usage, contacts, SMS, location, or accessibility permissions are requested.
 */
public class ViewerActivity extends Activity {
    private EditText serverField;
    private EditText tokenField;
    private CheckBox showHistory;
    private TextView output;
    private Button refresh;
    private JSONObject cachedReports;
    private boolean loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(28, 30, 28, 30);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);

        TextView heading = new TextView(this);
        heading.setText("Activity Viewer");
        heading.setTextSize(26);
        root.addView(heading);

        TextView description = new TextView(this);
        description.setText("View reports only from your devices with reporting enabled. A reachable HTTPS server is required; phones can use different networks. Your viewer token is not saved.");
        description.setTextSize(15);
        description.setPadding(0, 12, 0, 18);
        root.addView(description);

        serverField = new EditText(this);
        serverField.setHint("https://your-server.example");
        serverField.setSingleLine(true);
        serverField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverField.setText(getPreferences(MODE_PRIVATE).getString("server", ""));
        root.addView(serverField);

        tokenField = new EditText(this);
        tokenField.setHint("Viewer token (not agent token)");
        tokenField.setSingleLine(true);
        tokenField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(tokenField);

        showHistory = new CheckBox(this);
        showHistory.setText("Show opted-in in-app browser history");
        showHistory.setChecked(false);
        root.addView(showHistory);
        showHistory.setOnCheckedChangeListener((button, checked) -> {
            if (cachedReports != null) render(cachedReports);
        });

        refresh = button(root, "Fetch authorized reports");
        refresh.setOnClickListener(v -> fetchReports());

        Button clear = button(root, "Clear viewer session");
        clear.setOnClickListener(v -> {
            tokenField.setText("");
            cachedReports = null;
            output.setText("Viewer session cleared. Stored reports on the server are unchanged.");
        });

        output = new TextView(this);
        output.setTextSize(15);
        output.setTextIsSelectable(true);
        output.setPadding(0, 20, 0, 24);
        output.setText("Enter the server URL and viewer token, then tap Fetch. No monitoring permissions are needed on this phone.");
        root.addView(output);
        setContentView(scroll);
    }

    private Button button(LinearLayout root, String label) {
        Button button = new Button(this);
        button.setText(label);
        root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private void fetchReports() {
        if (loading) return;
        String base = serverField.getText().toString().trim().replaceAll("/+$", "");
        String token = tokenField.getText().toString().trim();
        if (!base.startsWith("https://") || base.contains("?") || base.contains("#")
                || base.contains("@") || token.length() < 24) {
            output.setText("Use a valid HTTPS server URL and the viewer token (24+ characters).");
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("server", base).apply();
        cachedReports = null;
        loading = true;
        refresh.setEnabled(false);
        output.setText("Connecting securely...");
        new Thread(() -> {
            HttpURLConnection connection = null;
            String result = null;
            String error = null;
            try {
                URL url = new URL(base + "/api/reports");
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("HTTPS is required");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("X-Viewer-Token", token);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-store");
                int code = connection.getResponseCode();
                if (code == 401 || code == 403) throw new IllegalStateException("Viewer token rejected by server");
                if (code != 200) throw new IllegalStateException("Server returned HTTP " + code);
                try (InputStream stream = connection.getInputStream();
                     ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (bytes.size() + count > 1024 * 1024)
                            throw new IllegalStateException("Response too large");
                        bytes.write(buffer, 0, count);
                    }
                    result = bytes.toString("UTF-8");
                }
            } catch (Exception ex) {
                error = ex.getMessage() == null ? "Network or TLS error" : ex.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String json = result;
            final String message = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                refresh.setEnabled(true);
                if (message != null) {
                    output.setText("Unable to load reports: " + message + "\nCheck HTTPS, server availability, and viewer token.");
                    return;
                }
                try {
                    cachedReports = new JSONObject(json);
                    render(cachedReports);
                } catch (Exception ex) {
                    output.setText("Server returned invalid report JSON.");
                }
            });
        }).start();
    }

    private void render(JSONObject reports) {
        ArrayList<String> ids = new ArrayList<>();
        for (Iterator<String> it = reports.keys(); it.hasNext();) ids.add(it.next());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            output.setText("No reports yet. Enable reporting and sync once from Phone A.");
            return;
        }
        StringBuilder result = new StringBuilder("Devices reporting: " + ids.size() + "\n");
        for (String id : ids) {
            try {
                JSONObject report = new JSONObject(reports.getString(id));
                result.append("\n━━━━━━━━━━━━━━━━\nDevice: ").append(id);
                long time = report.optLong("recordedAt", 0);
                if (time > 0) result.append("\nLast report: ").append(DateFormat.getDateTimeInstance().format(new Date(time)));
                JSONArray apps = report.optJSONArray("apps");
                List<JSONObject> sorted = new ArrayList<>();
                if (apps != null) for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.optJSONObject(i);
                    if (app != null) sorted.add(app);
                }
                sorted.sort(Comparator.comparingLong((JSONObject app) -> app.optLong("foregroundMs", 0)).reversed());
                result.append("\nApps reported: ").append(sorted.size()).append("\n");
                for (int i = 0; i < Math.min(sorted.size(), 25); i++) {
                    JSONObject app = sorted.get(i);
                    long minutes = Math.round(app.optLong("foregroundMs", 0) / 60000.0);
                    result.append("• ").append(app.optString("package", "unknown"))
                            .append(": ").append(minutes).append(" min\n");
                }
                if (sorted.size() > 25) result.append("... and ").append(sorted.size() - 25).append(" more apps\n");
                JSONArray history = report.optJSONArray("browserHistory");
                if (showHistory.isChecked()) {
                    result.append("\nIn-app browser history:\n");
                    if (history == null || history.length() == 0) result.append("(none shared)\n");
                    else for (int i = 0; i < Math.min(history.length(), 30); i++) {
                        result.append("• ").append(history.optString(i)).append("\n");
                    }
                } else {
                    result.append("\nBrowser history hidden in viewer.\n");
                }
            } catch (Exception ex) {
                result.append("\nDevice ").append(id).append(": invalid report format\n");
            }
        }
        output.setText(result.toString());
    }
}
