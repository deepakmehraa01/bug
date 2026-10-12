# Activity Monitor — two-phone consent-based lab

This repository contains **two separate Android apps** and a Spring Boot server:

- **Phone A — Activity Monitor** (`app/`): visible, opt-in Android app-usage reporting. With explicit optional opt-in, sends URL history **only from its own built-in browser**, not Chrome or other browsers.
- **Phone B — Activity Viewer** (`viewer/`): read-only viewer for authorized reports. Requires no sensitive Android permissions. Its viewer token is kept in memory for the current app session, not saved.
- **Server** (`server/`): HTTPS-facing relay API plus a simple web dashboard. Both phones communicate with the server independently; they **do not need to share a Wi-Fi/network**.

## Getting started

1. Run the Java 17 Spring Boot server with **two different random secrets**: `AGENT_TOKEN` and `VIEWER_TOKEN`, each at least 24 characters. Do not commit credentials or use the same token for both roles. Example generation: `openssl rand -hex 32`.
2. Expose the server through a trusted HTTPS hostname (valid TLS certificate) reachable from both phones. Localhost/LAN-only URLs will **not** work when phones are on different networks. Do not disable certificate validation or use plaintext HTTP.
3. In GitHub **Actions**, run **Build Android APKs and Test Server**. Download the `activity-monitor-debug` and `activity-viewer-debug` artifacts separately (ZIP files containing APKs).
4. Install Activity Monitor on Phone A. Grant Android **Usage Access**, provide the HTTPS server URL and **agent token**, review what is being shared, and explicitly enable sync. Use **Sync now** to send the first report.
5. Install Activity Viewer on Phone B. Provide the **same HTTPS server URL** and the **viewer token** (not the agent token). Tap **Fetch authorized reports**. This is an on-demand, read-only operation. Browser history is hidden in the viewer unless separately checked.
6. To stop sending, use **Stop sync** on Phone A. Clearing the viewer session erases the token and report display on Phone B, not server-side data.

## Build locally

Android requires an Android SDK (API 35), Java 17, Gradle 8.9:
```sh
gradle :app:assembleDebug :viewer:assembleDebug
```

Server requires Java 17 and Maven:
```sh
cd server
AGENT_TOKEN="<your-long-agent-secret>" VIEWER_TOKEN="<your-different-long-viewer-secret>" mvn spring-boot:run
```

## Security, privacy, and limitations

- The Phone A user must consent and enable Usage Access; no covert operation or hidden permission elevation is supported.
- Android app-usage statistics are approximate and depend on Android's Usage Access API. This is **not** screen capture, WhatsApp tracking, call/SMS access, camera/mic capture, global browsing history, or GPS.
- The server currently holds only the latest report for each device **in memory**; reports disappear if the server restarts. Use a trusted private deployment: the data can be sensitive, and tokens grant access to it. For production, add encrypted persistent storage, per-device identities, token rotation, and retention policies.
- HTTPS hosting is required for cross-network communication. **Building APKs does not deploy the server**.
- This is a development/debug build. Validate consent flows, Android versions, battery restrictions, and actual end-to-end behavior on your own devices before wider use.
