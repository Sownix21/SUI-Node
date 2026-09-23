<div align="center">
  <img src="design/sonix-switchline.svg" width="112" height="112" alt="S-UI Node — SONIX Switchline icon">
  <h1>S-UI Node</h1>
  <p><strong>Your panels. One Android workspace.</strong></p>
  <p>A Material 3–based glass interface for managing s-ui through APIv2.</p>
  <p>Android 8.0+ · Kotlin / Jetpack Compose · APIv2 only · GPLv3</p>
  <p><a href="#get-connected">Connect</a> · <a href="docs/VERIFICATION.md">Verification</a> · <a href="https://github.com/Sownix21">SONIX on GitHub</a></p>
</div>

---

S-UI Node is an independent Android management client for
[alireza0/s-ui](https://github.com/alireza0/s-ui), a sing-box management panel.
Manage multiple panels, edit clients and protocol configurations, inspect server status,
and opt into local reminders without using the web-session API.

**This is a management app, not a VPN connection app.** It does not route your phone's
traffic through a VPN. Android 8.0 or later, a reachable s-ui server and its API token are required.

## At a glance

| Area | What the app provides |
| --- | --- |
| Panel workspace | Multiple profiles, switching, groups, favorites, provider memos, local read-only mode and optional encrypted offline summaries |
| Clients | Search, filters, sorting, complete-record editing, bulk tools, renewal previews, subscription links and QR display |
| Configuration | Inbounds, outbounds, endpoints, services, TLS templates, certificate providers, routing, DNS and HTTP-client configuration |
| Conditional editors | Protocol-dependent transport, TLS, multiplexing, listen and nested option controls; structured JSON/Clash subscription settings |
| Operations | Server/core status, traffic views, logs, database backup/restore and supported restart/maintenance operations |
| Optional monitoring | Client expiry/quota, panel-unavailable and unexpected-core-stop alerts, recovery notices, quiet hours and encrypted history |
| Optional VPS tools | Monthly traffic estimates, directional accounting, reset dates/timezones and billing renewal reminders |
| Appearance | Light, Dark and true-black AMOLED, accent palettes, optional dynamic colors, glass surfaces, English/Persian interface options |
| Local protection | Android Keystore vault, biometric/device authentication, optional 8–32 digit app PIN, automatic-lock presets and configurable screenshot/Recents protection |

Available operations depend on the panel/core version, platform and APIv2 support.
A visible editor or successful unit test does not prove every configuration runs on every
server. See [Compatibility](#compatibility-and-limits).

## Get connected

1. In the s-ui web panel, open **Admin** and create an API token.
2. Add a panel profile in S-UI Node.
3. Enter the complete panel URL, including its configured web path, for example:
   `https://panel.example.com:2095/app/`.
4. Paste the API token, leave certificate verification enabled, and test the connection.
5. Save the profile and open it. Allow the initial synchronization to finish before editing.

Use the panel address, **not** its subscription address. Do not append `/apiv2` yourself.
The token is sent in the `Token` HTTP header, never as a query parameter.
Username/password web login and legacy `/api` fallback are not supported.

HTTPS with normal certificate and hostname verification is the expected setup.
HTTP and explicitly insecure TLS are compatibility options, not secure alternatives.
Redirects are intentionally not followed; enter the final correct panel URL directly.

### Reverse proxies and subscription links

For a panel behind nginx or another proxy, use its externally reachable URL and web path.
The proxy must forward APIv2 requests and the `Token` header to the panel.

**Public subscription URI** is the panel's override for the subscription base URL.
For example, if the internal subscription listener is
`http://127.0.0.1:2096/sub/` but nginx exposes it as
`https://sub.example.com/sub/`, use that public address in the setting.
This supplies the public base for generated subscription links/QRs without exposing the
internal port. It does not configure nginx, create DNS records, or change the management URL.
No separate port-stripping feature is needed.

## Compatibility and limits

The implementation was reviewed against the supplied s-ui backend and frontend sources,
including the 1.6.1 changes and the official 1.6.2 settings fix.

| Reference | Scope |
| --- | --- |
| Local backend snapshot | Reports 1.6.1; retained separately as a reference, not shipped in this Android repository |
| Frontend | `ab6ed5148c02d311b19e1fe17e85e0e23bc25f88` / 1.6.2 |
| Official 1.6.2 backend fix | Filter internal migration/bookkeeping rows out of settings responses |
| Later releases | Require a fresh contract/form review; not automatically certified |

See the [1.6.1 audit](docs/PANEL-1.6.1.md), [1.6.2 audit](docs/PANEL-1.6.2.md),
and [APIv2 documentation](https://github.com/alireza0/s-ui/wiki/API-Documentation).

### Protocol families

Inbound editors include direct, mixed, SOCKS, HTTP, Shadowsocks, Snell, VMess, Trojan,
Naive, Hysteria, ShadowTLS, TUIC, Hysteria2, VLESS, AnyTLS, TUN, redirect, TProxy and
Cloudflared. Outbound editors cover proxy protocols and core-specific routing/group
choices such as selector and URL test.

Endpoints include **WireGuard, WARP, Tailscale, OpenConnect, OpenVPN client and OpenVPN
server**. Services include DERP, Resolved DNS, Shadowsocks API, supported multiplexer/API
services and OOM killer. Some choices depend on the core build and host OS.
WireGuard peer export is not a promise of QR export for every endpoint type.

### Wire behavior

- Only actions registered by the backend's APIv2 handler are permitted.
- Full synchronization uses `GET load`, without a phone-clock `lu` cursor.
- Summary lists are not used as complete editable objects: fetch full records first.
- Saves use form-encoded `object`, `action`, JSON `data` and optional `initUsers`.
- Responses use `{ success, msg, obj }`. HTTP 200 alone does not mean success.
- Ordinary edits retain unrelated/unknown protocol fields. Changing a protocol or disabling
  an option can intentionally remove the fields belonging to that choice.
- Panel settings use a reviewed allowlist of 27 writable keys. Internal flags and protected
  settings are never echoed back through the general settings save.
- JSON/YAML subscription extensions retain their string representation.
- Failed refreshes keep the last good snapshot and display the failure.

Web administrator credentials, API-token management and generated core-configuration
downloads are not implemented as pretend APIv2 operations.
Maintenance uses its dedicated action, not a generic settings flag.

**Verification boundary:** automated fixtures validate wire shapes and app logic, not a
running sing-box instance. Physical-device navigation and GET checks do not certify live
POST operations, every protocol combination, or background delivery across all OEMs.
Use a disposable panel for write/restore/runtime acceptance tests before broad deployment.
The current evidence and remaining manual checks are in [VERIFICATION](docs/VERIFICATION.md).

## Save safeguards

Optional save review shows a redacted summary before submission. Full records are read
again to detect intervening changes. Interrupted-save recovery stores encrypted evidence
and checks server state with GET; it does not blindly retry POST.
An unresolved write must be reconciled before further writes.

Local read-only profiles block write operations and active probes. This is an app safeguard,
not a server-side restricted token. Backend saves do not offer an atomic compare-and-swap
guarantee. Details: [Save safety](docs/SAVE-SAFETY.md).

Database backups are the **original SQLite bytes received from the panel**. They are
deliberately not encrypted or repackaged by the app. Treat them as sensitive credentials.
Restore, reset, restart, maintenance and renewal operations can affect real users.
Opening Android's document picker does not discard a backup request. If app lock engages,
authenticate when returning and the selected export continues. Restore requests are not
automatically replayed after activity/process recreation.

## Optional monitoring

Monitoring starts disabled. Enable it explicitly, select panels and alert types, and save.
On Android 13+, allow notification permission. The app posts its own notifications;
it does not request access to other apps' notifications.

Checks use Android WorkManager, with a minimum interval of 15 minutes. Doze, battery saver,
device lock, loss of connectivity and OEM autostart restrictions can delay them.
Force-stopping the app prevents work until it is reopened. These are best-effort alerts,
not an exact scheduler or uptime SLA.

VPS traffic tracking supports combined, receive-only, send-only and separate directional
limits. It estimates usage from panel counters; provider accounting and missed observations
may differ. Limits use decimal GB/TB; client quotas use GiB. Configure a baseline, reset
day and provider timezone.

VPS billing reminders store a due date, period, optional price/currency and warning window
locally. They neither contact your provider nor make payments. Dates advance only when
you explicitly mark a renewal and save it.

Read [Monitoring and renewals](docs/MONITORING.md) before relying on these features.

## Security and privacy

Profiles, tokens and sensitive local state use an authenticated AES-256-GCM vault backed
by non-exportable Android Keystore keys. StrongBox is preferred where available; actual
hardware protection depends on the device. Vault files are excluded from Android backup.

An optional app PIN is separate from the phone's screen-lock PIN. App lock protects the UI;
choose Immediate, 1 minute, 5 minutes, 15 minutes or screen-off locking after leaving.
Screen-off always locks the app, and a fresh process requires authentication when app lock
is enabled. Timed presets intentionally retain the unlocked session during their grace period.
Explicitly enabled background monitoring can still access the vault to perform checks.
Screenshot/Recents protection is optional and updates immediately.

A changed server certificate requires fingerprint review. A legitimate renewal can trigger
this; verify the new fingerprint independently before trusting it. Root, compromised
firmware or a debuggable process is outside the vault's protection guarantees.

**Do not uninstall or clear app data to fix vault errors:** doing so can destroy encryption
keys and saved profiles. Do not share real database backups or device dumps in issues.

See the [security model](docs/SECURITY.md) and [private reporting guidance](SECURITY.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Invalid/expired token | Generate or replace the token in the web panel, then edit the profile. The backend does not distinguish these two rejection causes. |
| Empty lists or failed sync | Verify the panel URL/web path, API token, proxy header forwarding and displayed error; don't save an empty replacement configuration. |
| Certificate warning | Check the server certificate and fingerprint through a trusted channel; do not disable verification merely to dismiss a warning. |
| Subscription links contain an internal port | Configure Public subscription URI to the externally reachable subscription base. |
| Notifications are late/missing | Global monitoring, selected panels, permission, quiet hours, device unlock and OEM battery/autostart settings all matter. |
| Vault cannot open | Do not clear data/uninstall. Preserve the installation and investigate the key/storage error. |
| Setting not reflected on the panel | A toggle changes the draft. Review and save with the tick, check the response, then refresh. |
| Backup reports `backing up stats: too many SQL variables` | The panel could not copy its stats table. Explicitly selecting Exclude traffic graphs can allow a backup without that history; it does not delete panel data. A full-history backup requires resolving the server-side error. |

## License and credits

Copyright (C) 2026 **SONIX**. Original project code is licensed under
**GNU GPL version 3 only** (`GPL-3.0-only`). See [LICENSE](LICENSE) and [NOTICE](NOTICE).
Preserve applicable copyright/license notices, identify distributed modifications and
provide corresponding source when distributing binaries as required by GPLv3.
The software is provided without warranty.

Thanks to [alireza0 and s-ui contributors](https://github.com/alireza0/s-ui) and the
[s-ui frontend contributors](https://github.com/alireza0/s-ui-frontend).
Third-party code, libraries and assets retain their own licenses and notices.
This independent client is not endorsed by the upstream project or GitHub.

<div align="center">
  <strong>Developed by <a href="https://github.com/Sownix21">SONIX</a></strong>
</div>
