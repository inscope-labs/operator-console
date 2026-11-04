# Operator connectivity

How the console relates to the gateway, in operator terms.

## First launch

On a fresh install the gateway base URL is empty (or a scaffold placeholder). The shell
banner shows **Not configured**. Dashboard and plan lists stay on **Idle** — data has not
been loaded yet. Nothing is fetched until you set a URL and either test the connection or
pull to refresh.

1. Open **Settings**.
2. Enter the gateway base URL (lab debug builds may default to `http://10.0.2.2:8787`).
3. Tap **Test connection**. A successful health probe updates the banner to
   **Connected (test)** or **Connected (production)**.
4. Return to the dashboard and refresh.

## Session is not connectivity

Unlocking a local session (biometrics) proves a human is present for privileged actions.
It does **not** mean the gateway is reachable. Conversely, a connected banner does **not**
grant write authority — privileged ops still need a live gateway and a capability the
gateway accepts.

## When something fails

| Banner / message | What to do |
|------------------|------------|
| Not configured | Set the base URL in Settings. |
| Unreachable | Check host, network, DNS; use Test connection. |
| Connected but read fails | Credential, path, or server error — see the on-screen message. |
| Scope refused / replayed / head drifted / halt | Follow the specific remedy; these are not generic "try again" cases. |

Diagnostics (Settings) can record recent gateway method/path/outcome lines for support.
Nothing there includes tokens or capabilities.
