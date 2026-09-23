# 2.3 device fixes and appearance

- Keep backup/document-picker callbacks alive across app lock and resume selected exports
  after authentication. Do not automatically replay restores after activity recreation.
- Add Immediate, 1/5/15-minute and screen-off lock policies, using elapsed time; screen-off
  always locks. Delay automatic biometric prompts until the app resumes.
- Use compact panel-group/favorite filters and lightweight per-panel pending-change actions.
  Remove duplicate monitoring, diagnostics and pending-change links from App settings.
- Rename the standalone project and namespace to S-UI Node / com.sonix21.suinode.

- Prepare the standalone GitHub source tree: GPLv3/SONIX notices, detailed README,
  release/contribution/security guidance, unsigned CI and source-only export tooling.
- Remove unused HTTP-logging, ViewModel Compose and preview dependencies, AI Studio
  scaffold metadata and irrelevant build settings. Keep legacy vault-migration dependencies.
- Fail incomplete release signing configuration explicitly; retain standard debug signing.
- Distinguish maintenance status loading/failure from unsupported capability.
- Update About's vault description, include an offline GPLv3 viewer, and add the GitHub
  profile icon beside Developed by SONIX.

- Handle s-ui 1.6.2's settings fix and affected 1.6.1 responses: exclude internal migration
  rows from settings reads, write payloads and save responses, preserving subscription
  JSON/YAML strings. See [1.6.2 audit](PANEL-1.6.2.md).

- Align APIv2 and protocol forms with s-ui 1.6.1: maintenance, own endpoint TLS,
  OpenVPN static keys/cipher modes, TLS spoofing and protected settings. See [release audit](PANEL-1.6.1.md).
- Update screenshot/Recents and nearby preference indicators immediately, without an app restart.
- Remove the redundant port-removal action; use Public subscription URI for the proxy URL.

- Add optional encrypted VPS billing reminders (monthly, quarterly, yearly or custom),
  due dates, prices/currency, advance warnings and explicit local renewal-date advancement.
- Add optional VPS traffic estimates with provider accounting modes, reset day/timezone,
  threshold alerts, manual baseline correction and explicit uncertainty for counter gaps.
- Add app-specific 8–32 digit PIN authentication and optional screenshot/Recents protection.
- Add client sorting and clear invalid-or-expired token guidance. Public subscription URL
  controls use the backend override for the proxy URL.
- Add optional panel groups, favorites, memos, encrypted offline overviews and read-only mode.
  Add configurable redacted pre-save review and GET-based interrupted-save recovery without
  automatic POST retry. See [save safeguards](SAVE-SAFETY.md). No diagnostic export added.

- Move TLS pool shutdown off the Android UI thread so cleanup cannot mask a completed
  GET when leaving a temporary connection; keep renewal errors visible on the page.
- Replace legacy profile storage with an authenticated AES-256-GCM Android Keystore vault,
  verified migration, no-backup placement, fail-closed recovery and redacted diagnostics.
  Lock the UI again on backgrounding and protect app windows with FLAG_SECURE.
- Add opt-in expiry/quota, panel-unavailable, core-stopped and recovery alerts, configurable
  intervals/thresholds/quiet hours, notification privacy, OEM settings links and encrypted history.
  Fetch full client records in bounded GET batches: the backend list omits delay/reset fields.
- Add a cross-panel renewal dashboard with exact previews, explicit confirmation, no-op
  filtering, conflict checks and GET-back verification. No automatic renewal or traffic reset.
- Guard unsaved drafts across primary editors, bulk forms, rule sets, routing defaults,
  HTTP clients, imports, panel profiles and monitoring settings.
- Add read-only connection diagnostics and explicit certificate-fingerprint review before
  an authenticated request is allowed to use a changed TLS identity. Connection pools are
  cleared after review. Normal platform certificate validation remains in effect.
- Panel database exports remain exactly as received; encrypted backup export was not added.
  See [security limitations](SECURITY.md) and [monitoring behavior](MONITORING.md).

- Initialize new routing drafts synchronously, without borrowing the last existing rule
  or sending the frontend-only `simple` wrapper to APIv2. Logical-mode toggling preserves
  the first condition and retains additional conditions for toggling back within the draft.
- Fix subscription control recomposition after in-place JSON edits, including TUN and
  Mixed port; make subscription headings readable against the AMOLED background.
- JSON subscription now has the frontend's default routing presets, final route,
  geo/custom rule-set choices, logging, DNS/server choices, TUN and experimental toggles.
- Clash subscription now has mixed-port/LAN, controller, logging, TUN, DNS and routing
  controls plus the three panel generation switches. Parse and emit YAML for subClashExt;
  both extensions remain string-valued APIv2 settings. Custom fields and rules are retained.
  Advanced editors are optional, validate before applying, and only update the local draft.
  YAML parsing uses bounded safe data construction; see the
  [parser settings documentation](https://javadoc.io/static/org.snakeyaml/snakeyaml-engine/2.10/org/snakeyaml/engine/v2/api/LoadSettingsBuilder.html).
- Routing and DNS inbound/client conditions now use searchable multi-selection sheets,
  revealed by their match toggle. Store inbound tags and client names, not database IDs;
  retain existing references that are absent from the latest panel lists.
- Endpoint cards expose individual and sequential batch latency tests through APIv2
  checkOutbound. Show core milliseconds only for valid OK results, with per-item busy
  and error states. The running core determines whether an endpoint can be dialed.

- Rename the visible app to S-UI Node while retaining the application ID and saved profiles.
- Move page-level Create and Save actions into compact header buttons, including bulk
  clients, HTTP clients and outbound imports. Remove the reserved bottom action region.
  Buttons retain full accessibility labels, long-press tooltips and busy-state protection.
  Icon semantics are explicit: + opens a new-item draft; a tick submits any form,
  including newly created records. Delete actions retain their delete icon.
- Align Services with the supplied frontend: seven service types, conditional DERP
  verification/mesh/STUN controls, shared listener options, Core API dashboard options,
  and OOM protection. The Core API service is configured through APIv2; it is not a
  legacy panel API transport. Preserve repeated Shadowsocks API path mappings.
- Fix collapsed service/listener and REALITY handshake fields; stack multiplex limits
  for readable labels. Obscure service tokens, mesh secrets and private keys.

- Backup picker crash: explicitly resolve Fragment 1.8.9 instead of the legacy version
  that rejected Activity Result request codes. Catch missing/broken picker launches.
- Stream APIv2 getdb to the chosen document on IO, validate the SQLite signature before
  opening the output, and report transfer failures. Restore requires confirmation and
  performs file work on IO. Database exports contain credentials and are unencrypted.
- Preserve each route's saveable scroll/search state while opening detail screens;
  preserve root-tab state across tab switches. Discard popped form state.
- Dark, Light, AMOLED and System theme choices. AMOLED draws an exact #000000 base
  without ambient lights. Android force-dark is disabled for the app's own palettes.
- App-wide Lime, Emerald, Cyan, Blue, Amber and Amethyst circle selectors. Choosing a
  custom accent switches off wallpaper colors. Shared glass cards use 24dp corners,
  translucent fills and a subtle vertical highlight border.
- Remove the separate Persian-digit preference, retaining Persian language support.
- WARP never offers a peer QR. WireGuard only offers sharing when peers exist; export
  still requires the corresponding client private key and server public key.
- Add OpenConnect, OpenVPN client and OpenVPN server forms using the supplied frontend
  options and dedicated endpoint TLS forms. Static-key fields and client credentials
  follow mode/type conditions. OpenVPN server does not expose outbound dial options.
  These extensions require support in the panel's compiled core; APIv2 cannot install it.
- Correct Tailscale system-interface enablement and WireGuard peer key replacement.

Regression tests cover all six endpoint defaults, QR eligibility, static-key mode,
required fields, backend TLS restrictions, streaming downloads and rejection before
opening a destination. Existing APIv2 lossless round-trip tests remain in place.

Live write compatibility is not certified by these tests. No panel mutation should be
used for QA without owner approval; use a disposable panel for protocol save matrices.
