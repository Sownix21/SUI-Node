# APIv2 alignment and verification

## Current update — 2.4.2 / October 9, 2026

Targets the supplied **s-ui 1.6.4 / sing-box 1.14.2** and frontend
`e4525297b002c1c3be234cc1c9695ef84be741a0`. See [the source review](PANEL-1.6.4.md)
and [release notes](CHANGES-2.4.2.md).

- **145 JVM tests passed** across 21 suites, with zero failures/errors. New tests cover
  source-IP field types and cleanup, repeated rule ordering, unlimited-client usage sorting,
  full HTTP dial/DNS config POST+GET fixtures, reset-alert freshness and cron rejection.
- Debug and release lint report **0 errors and 0 warnings** in this offline run. No lint
  baseline or new suppression was added. Online dependency advisories can differ.
- APIv2 registry comparison passed: **19 GET / 10 POST / 27 writable settings**, with
  **19 inbound / 19 outbound / 13 DNS types** matching the supplied frontend.
- The repaired GitHub workflow passed local actionlint (without optional ShellCheck or
  Pyflakes). A hosted GitHub Actions run has not been triggered or verified.
- Persian README rendering checks passed: all nine main headings and three tables inherit
  RTL direction, while address examples retain LTR. Local Markdown links resolve.
- `testDebugUnitTest lintDebug lintRelease assembleRelease bundleRelease` completed
  successfully offline. R8/resource shrinking produced an unsigned release APK and AAB;
  APK metadata confirms **2.4.2 (8)** and `com.sonix21.suinode`. 16 KB ZIP alignment passed.
- `git diff --check` passed. No APK/AAB, signing key or local SDK-properties file is
  tracked. A source-only export excludes build output, local captures and signing material.

| 2.4.2 artifact | SHA-256 |
| --- | --- |
| Unsigned release APK | `b9fe0fcc587b8ca0efd8105fc5d7488d9e506c0f91689022c4a00a391b248eb1` |
| Unsigned release AAB | `0497309a22ded1247de40aa906f9fbc8a0ba9bedf4013c7b6aee1b640c01fa14` |

Earlier device tests and artifact hashes below describe older builds, not this update.
No device or live panel operation was performed. Signing and publishing are left to the owner.

## Post-device-test PIN/biometric follow-up — October 2, 2026

Source changes after the signed-device checks below move PIN setup, change, removal and
lock confirmations into a modal, scrollable form with separate confirmation/cancel actions.
Biometric enrollment now opens Android's strong-biometric prompt directly, without an
extra app-PIN entry. It still checks for a configured fallback PIN, enabled app lock and
an unlocked session before prompting and again before persisting a successful opt-in.
Cancellation/error callbacks do not enable biometrics. The signed APK and historical hashes
below describe the preceding build, not this follow-up. The new popup layout and enrollment
behavior still require a freshly signed device update for physical-device acceptance.

Follow-up verification completed:

- All **137 JVM tests** passed across 20 suites, with zero failures/errors, including the
  biometric enrollment prerequisite matrix.
- Debug/release lint passed with **0 errors and 2 toolchain-update warnings** each.
  The initial internal lint-analyzer failure did not recur in the completed build.
- `testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease`
  completed successfully offline. Debug APK, optimized unsigned release APK and AAB were
  produced. The release APK passes 16 KB ZIP alignment.
- The APIv2 registry comparison passed against the supplied s-ui 1.6.3 sources.
- No live panel writes, phone-setting changes or production-key access occurred.

| Follow-up artifact | SHA-256 |
| --- | --- |
| Debug APK | `24f9311a814b5a3a3d56913f3ed0abc0494dfba35c3a1901f9d1333a7b394ee4` |
| Unsigned release APK | `618acfe62320ea8c480ae9c18c186f3460d94c7b0792ca1b7883cd8c8bcf35a5` |
| Unsigned release AAB | `f8a9fda1f19d5bf99c911cb0aa763e210e9dcb1c328dc0f68ed7148f47fb98e1` |

## Pre-follow-up verification — 2.4.1 / October 1, 2026

Version **2.4.1 (7)** targets the supplied **s-ui 1.6.3** backend and frontend
`f859e16953cd733293618f626cc19b8466e00fd3`; its backend declares sing-box **1.14.1**.
See [the compatibility review](PANEL-1.6.3.md) for the changed API contracts and forms.
The older checks below are retained as history, not evidence that every earlier acceptance
case was repeated for this release.

### Automated results

- `testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease`
  completed successfully with the checked-in Gradle wrapper.
- **136 JVM tests passed**, with zero failures/errors across 20 suites. Added coverage
  includes PIN-first policy, expiry-extension deduplication, stale monitoring snapshots,
  notification failures, scheduled quota baselines, clock rollback, billing dates and DST.
- Debug and release lint each reported **0 errors, 2 warnings** in the offline build.
  The remaining findings recommend newer Gradle and Kotlin Compose plugin versions.
  Online dependency-update advisories can vary with repository availability. No lint
  baseline was added. The two targeted KTX suppressions preserve checked synchronous
  commits for lock-state changes and monitoring scheduling, where ignoring failure is unsafe.
- APIv2 registry comparison passed: **19 GET actions, 10 POST actions, 27 writable settings**.
  Frontend selectors matched **19 inbound, 19 outbound and 13 DNS types**. This checks the
  registries, not every configuration or protocol at runtime.
- R8 minification/resource shrinking and debug APK, unsigned release APK and AAB builds
  completed. The release is non-debuggable, passes 16 KB APK ZIP alignment, and its packaged
  native libraries have 16 KB-aligned ELF LOAD segments. Production signature verification
  still requires the owner's signed output. A separate debug-key QA copy of the final
  optimized APK passed v2/v3 signature verification; it is not a production distributable.
- The workflow installs SDK tools before invoking `sdkmanager`, with an empty action
  package input and three separately quoted SDK arguments. The action is pinned to its
  verified v4 commit. Checksum-verified actionlint 1.7.12 passed locally (without optional
  ShellCheck/Pyflakes integration); a hosted GitHub Actions run has not been triggered or verified here.
- The Persian README was rendered locally: all nine main headings and three tables inherit
  RTL direction; URL examples remain LTR. A local browser preview was visually inspected.
- Local documentation links resolve. Source scanning found no old application name or
  package identity, and Git tracks no APK/AAB, keystore or local SDK-properties file.

### October 2 production-signed device follow-up

The owner's SONIX-signed **2.4.1 (7)** is installed on the Mi 9T Pro / Android 11.
The installed APK matches the local signed release byte-for-byte (SHA-256
`842436890648581b7c2ed664afd4451e0bec1da6432e7e3f37c762b38fc00dbd`).
Its v2 signature verifies, and 16 KB APK ZIP alignment passes. No signing key was accessed.

Read/navigation checks completed on this signed build:

- Overview connected and populated clients/inbounds. An existing client editor loaded.
- All five expiry shortcuts, including infinity, occupy the same row. The date and time
  popups opened; cancellation did not change expiry. Returning to Clients restored the
  same edit-action bounds at the previously scrolled list position.
- Endpoint latency status occupies a readable single line, with actions in a separate
  row. WARP has no sharing action; WireGuard with peers offers peer sharing. No probe ran.
- The Services empty state and an unsaved new-service form opened successfully.
- The VPS renewal calendar, billing-reset time popup and quiet-hours hour-only popup
  opened in unsaved local drafts. Drafts were discarded without saving. Reopening renewal
  confirmed it remained disabled. Monitoring and quota tracking were not enabled.
- No app-process AndroidRuntime crash entries appeared during these checks.

The owner authorized a temporary PIN/biometric lock and backup regression test. PIN
enrollment and biometric enrollment succeeded with owner authentication. The PIN confirmation
form was initially below the visible area; scrolling to it resolved the setup confusion.
The source follow-up above replaces that inline form with a popup; device verification
of the replacement still requires a newly signed APK.

With the **Immediately** preset, the app locked after the backup destination picker.
Both PIN and biometric unlock remained available. The owner authenticated with a fingerprint;
the queued download resumed automatically. Reopening backup tools showed **Backup saved
(1.29 MB)**, and the distinct test output measured **1,355,776 bytes**. App-process
AndroidRuntime output contained zero error entries. This test used **Exclude traffic graphs**;
it does not establish that the earlier full-backup server SQL limit is fixed. No backup
contents were inspected or copied to the host. An earlier attempt interrupted by tool
approval limits also left a 1,355,776-byte test file, but only the repeated attempt has a
directly observed completion message.

The owner removed the temporary PIN. The settings screen again offered PIN setup, and
leaving/reopening the app returned directly to the connected dashboard without a lock
prompt, confirming restoration of the original no-PIN/lock-off/biometrics-off state.
Both named test backups were deleted from Downloads with the owner's explicit approval;
no regular backups were touched. No live panel POST, restart, restore or configuration save
was submitted. Notification
delivery, all protocol write/connect combinations and the wider Android/OEM matrix are
not certified by this read/navigation pass. A minor TalkBack wording defect was observed:
the one-day expiry shortcut says “Extend expiry by 1 days”; the action itself is unchanged.

### October 1 physical-device follow-up

The owner's installed SONIX-signed 2.4 build was inspected on the Mi 9T Pro. Opened Clients
and an existing editor without modifying it, confirmed the infinity shortcut wrapped below
the four duration shortcuts, and returned to the list. App-process AndroidRuntime output
contained zero error/crash entries. No live panel write or local monitoring opt-in occurred.

The installed certificate differs from the local debug key. No replacement, uninstall,
data clearing or signing-key access was attempted. The compact five-button row, VPS calendar
and clock popups built today have **not** been installed on that phone; they require a new
APK signed with the owner's existing identity. PIN enrollment/migration and background
delivery still need owner/device acceptance checks.

The signed 2.4 installation was opened again during the final follow-up; its app-process
AndroidRuntime output contained zero error entries. The temporary Android 36.1 emulator
had system-app ANR dialogs during startup. After recovery, the optimized 2.4.1 QA copy
opened its empty Panels page and Add Panel form without app-process crash entries.
Only a synthetic loopback profile draft was entered. No new editor, calendar, PIN or
monitoring acceptance test completed. The owner explicitly deferred further testing to
a later production-signed device build; the temporary emulator and fixture were stopped.

### September 30 physical-device results (preceding build)

The endpoint-card layout was subsequently adjusted after the owner reported broken-word
wrapping in the latency status. Status and actions now occupy separate rows, and actions
can wrap on narrow screens. Per the owner's request, that layout-only follow-up was not
installed or tested on the phone; the device results below describe the preceding build.

Mi 9T Pro / Android 11, after the owner authorized USB debugging. The installed 2.3 app's
certificate matched the local standard Android debug key. Updated in place to debug 2.4,
then to a **debug-key-signed copy of the optimized release**. No uninstall, data clearing,
credential extraction or app-lock bypass was performed. The saved profile still connected.

On the optimized build:

- Overview, Clients and Inbounds loaded. Existing client and inbound editors opened from
  their lists; neither was saved. The Services list displayed its empty state.
- Tools → Live sessions returned real connection rows. Switching the resource selector to
  Endpoint completed another read successfully. No disconnect action was pressed.
- In a new, unsaved TLS template, enabling fragmentation revealed record-fragmentation and
  fallback-delay controls; disabling it hid them. Enabling mutual TLS revealed certificate
  source, client-authentication policy and certificate-path controls.
- Leaving that modified template prompted for confirmation; **Discard** removed the draft.
- Panel settings loaded and displayed maintenance as off. The app was returned to Overview;
  the final app-process AndroidRuntime check found **zero error/crash entries**.

These are bounded read/navigation checks, not proof of submitted-write correctness. No
live configuration save, disconnect, restart, maintenance change, restore or probe was
performed. App-process AndroidRuntime logs were checked for crashes during this smoke test.
The phone's test-signed build is **not a production-signed distributable**.

### Pre-follow-up artifact hashes

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `bdf198903937b2ca14b8e5070dd35d8acdf0facbdd17d8a82bfced56cea2e096` |
| Unsigned release APK | `504e2a7f95358d1714bc0bdd234c476445d881085cf63c0572a5d96bbd5a8ab8` |
| Unsigned release AAB | `ecfb3ac6ad6c13838b6446098a5939e0d53e01b0560da1ff6b9304fb8d95cb21` |

### Still required before a production release

Use the owner's production signing identity and complete the disposable-panel acceptance
matrix in [RELEASE-CHECKLIST](RELEASE-CHECKLIST.md), including create/edit/delete and real
connections for the supported protocols. Long-running background/OEM behavior, the full
Android-version matrix and hosted GitHub CI were not revalidated here. The earlier live
panel full-backup SQL-variable limit must not be considered fixed merely because the app
builds. Release-build-ready source is not a claim of zero bugs or universal compatibility.

## Historical verification — 2.3 and earlier

### Earlier source of truth

The bundled `s-ui-main/api/apiV2Handler.go`, `apiService.go`, service/model implementations,
and `s-ui-frontend-ab6ed5148c02d311b19e1fe17e85e0e23bc25f88` were used to check wire formats
and conditional editor controls. Backend APIv2 availability takes precedence over web UI
features. No legacy `/api` fallback is implemented.
The September 13 refresh covers 1.6.1 and the 1.6.2 settings fix; see PANEL-1.6.1.md and
PANEL-1.6.2.md. The latter records the stale local backend and official-source cross-check.

## 1.6.2 compatibility verification

All 102 JVM tests passed, debug assembly succeeded, and lint completed with 0 errors and
41 warnings. The new HTTP fixture reproduces 1.6.1's migration-row settings-save failure
without using the live panel. A source comparison confirms all 27 writable settings match
the backend map after excluding protected fields.

The resulting debug APK was installed in place on the connected Mi 9T Pro. It connected,
opened Panel settings with its Web/Subscription/JSON sub/Clash sub controls, and read
`Maintenance is off`. App-process AndroidRuntime logs showed no crash entries. Only GET
requests/navigation were exercised against the real panel; no settings save or maintenance
operation was submitted. These checks do not establish the live server's installed version.

Earlier 1.6.2-check APK SHA-256: `c5258956137369b23208adb32e2764917b854d3ead238764f5a3bd74d5c1702b`.

## September 13 device checks

### Final app-lock and backup regression check

On the Mi 9T Pro (Android 11), the latest debug build opened the system backup destination
picker with App lock enabled and the Immediately preset. The owner authenticated on return.
The activity-owned pending request resumed, and the panel returned
`backing up stats: too many SQL variables` for a full backup. This is a server-side backup
error, not a lost Android callback; the error appeared in the backup sheet without a crash.

Repeating with **Exclude traffic graphs** selected, then authenticating again, automatically
saved **1,089,536 bytes** and displayed `Backup saved (1.04 MB)`. The export remained the
original panel SQLite response; database contents were not opened or copied to the host.
Excluding a table changes only the backup response, not the live panel. App lock was restored
to its original **Off** state and the unchecked control was verified. App-process crash logs
contained no AndroidRuntime errors during this check.

Full backups of this live deployment remain affected by its stats-table SQL-variable limit.
The app does not silently exclude data or claim a complete backup when the server rejects it.
The successful QA backup intentionally excludes traffic history; it is not a full-history
backup. No restore, panel settings save, restart or other live-panel write was submitted.

### Final minified-release navigation (September 13–14)

The latest debug-key-signed minified QA build installed over the new application identity
without clearing its profiles, then reached Connected/Overview. The compact panel-group
selector opened and dismissed correctly. All groups and Favorites shared one row; Pending
changes appeared as lightweight per-panel actions. Monitoring remained accessible in Tools.
Source inspection confirms Monitoring, Connection diagnostics and per-panel Pending changes
are no longer duplicated in App settings. The About footer's GitHub action was also checked
earlier in this audit: its Android view intent targeted `https://github.com/Sownix21`.
The final release-mode check confirmed App lock was still unchecked after installation,
and the current app process had no AndroidRuntime crash entries.

These are bounded smoke checks, not full visual, accessibility, lifecycle or protocol
certification. The backup unlock/export regression was exercised on the latest debug build;
it was not repeated after installing the matching minified release. The release uses the
same source, but that is not a substitute for running every acceptance case in release mode.

### Earlier debug checks

The 101-test debug APK was installed in place on the Mi 9T Pro. It connected to the live
profile and loaded 206 clients and 6 inbounds. Screenshot protection was initially off.
Turning it on immediately updated the accessibility switch to checked and set Android's
SECURE window flag; turning it off immediately unchecked the switch and cleared the flag,
without reopening the app. Its original off setting was restored. This Android 11 device
cannot exercise the separate Android 13+ Recents screenshot API.

An unsaved OpenVPN-server draft showed negotiated data cipher/fallback inputs in TLS mode,
dedicated certificate/key paths, explicit client-certificate verification, and optional
control-channel/fingerprint/version sections. Enabling control-channel protection in the
draft revealed tls-crypt, key-path and static-key-generation controls. No key generation,
save, maintenance toggle or latency probe was submitted. App-process AndroidRuntime logs
were empty during the navigation check.

After owner authentication, Panel settings read `Maintenance is off` and displayed the
stop action, confirming the live APIv2 capability. The action was not pressed. Monitoring
remained disabled. The VPS renewal screen also started disabled; an unsaved enable draft
revealed the date, monthly/quarterly/yearly/custom selector, price/currency, warning days
and timezone. Selecting Custom days revealed its numeric period input. Back opened the
discard confirmation; Discard returned to Monitoring and reopening confirmed reminders
were still off. No monitoring configuration or reminder was saved. Final app-process
AndroidRuntime logs contained no crash entries.

Previous 101-test APK SHA-256: `2e732de9f91878e34e54a1066167caf28f66847e9306acbe7b4bc88dd4f71495`.

## Automated checks

Final source verification on 2026-09-13 supersedes the earlier build counts above:
all **108 JVM tests passed**, with no failures or errors. Debug and release lint each
reported **0 errors and 35 warnings**. `assembleDebug`, `assembleRelease` and `bundleRelease`
completed successfully. The warnings are retained for review, not treated as proof of a
warning-free or certified application. Hosted GitHub Actions has not yet been executed.

The application ID and namespace are `com.sonix21.suinode`. This identity is a separate
Android installation; profiles must be entered by the owner, not extracted from another app.
The owner added the live profile used for the final checks. No existing app was uninstalled
or had its data cleared.

Historical September 13 artifact SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `7441e7aa06d5bc6da4810bb6dec8aa69910a40963dc5a96d80d26cbf973646fa` |
| Unsigned release APK | `a4c63deda38bb04ae4cfb8180884162804cf2735b8a9fbe3c111ee68d2a2d513` |
| Release AAB | `a338d98ac9279c5a09911641d529f91ca4d5f0f8b3528fdb67d4b8942a46c0dd` |

A separate minified-release QA copy was signed with the standard **debug key**, not a
production key. APK inspection confirmed the application ID, `debuggable=false`, v2/v3
signature verification and 16 KB ZIP alignment. The owner's production key is still needed
to sign distributable release artifacts. Do not publish the debug-key QA APK as production.

The six new regression tests cover auto-lock deadlines, clock anomalies, screen-off-only
timing, pending document cancellation, unlock/vault gating, duplicate-launch rejection and
single consumption. Document callbacks now belong to the activity rather than authenticated
Compose screens, allowing a pending backup destination to survive the lock screen. These
pure tests do not replace Android lifecycle or OEM testing.

`ApiV2ContractTest` checks authentication/path construction; complete and incomplete loads;
null collection clearing; HTTP-200 invalid-token responses; preservation of the last good
snapshot; initial-load save gating; JSON-quoted tag deletion; full client detail retrieval;
rejection of web-only operations; database download error handling; status system-info
retention; and protocol discriminators in generated defaults. Existing tests cover JSON
helpers and panel URL normalization. New regression cases cover wrapped TLS/outbound/
endpoint/service collection responses, absent/rejected editor records, exact fractional
GiB quota conversion, and WireGuard client export. WireGuard tests prove that an absent
client keypair never falls back to the server private key, and cover IPv6 endpoints,
peer addresses, server public keys, preshared keys, and keepalive.

These are controlled wire-contract tests. They do not start sing-box or prove that every
protocol configuration is accepted by a running server.

The additional tests cover routing draft initialization/logical conversion, authenticated
vault round trips/tamper rejection/verified migration/fail-closed recovery, alert thresholds,
quiet hours/deduplication/recovery/scope pruning, renewal preservation/overflow/conflicts,
and draft change detection. Local HTTPS fixture tests verify a rejected fingerprint sends
zero HTTP requests and an allowed fingerprint permits APIv2. Client fixture tests verify
205 full records are fetched in three bounded detail batches after the summary GET,
missing records fail instead of generating misleading results, and a null list is empty.

## Security and monitoring verification (September 10–12)

The security-only update was installed in place on the Mi 9T Pro. The existing profile
still reached Connected/Overview after vault migration. File-name-only inspection confirmed
the no-backup vault exists and legacy encrypted preferences were removed. No vault content,
token or private preference value was printed. The latest update adds monitoring, history,
renewals, diagnostics and draft safeguards; its device navigation checks require the owner
to complete the app's fingerprint/PIN prompt. App lock was not bypassed or disabled.
The 76-test debug APK was installed in place successfully on September 12.
After owner authentication, the live profile reached Connected. Read-only diagnostics
passed with normal HTTPS certificate/hostname validation; the stored fingerprint was
shown without approving any certificate change. The security card reported hardware-backed
Android Keystore protection. Monitoring started disabled, revealed its interval/expiry/quota/
availability/core/recovery/privacy controls when enabled only in a draft, and revealed 22:00
and 08:00 inputs when quiet hours were toggled. Android Back showed Discard/Cancel; Cancel
preserved the draft and Discard returned to the same Tools scroll position. Reopening
Monitoring confirmed it was still disabled. Empty alert history and the SONIX About sheet
also opened correctly. No monitoring settings were saved.

The renewal dashboard exposed a device-specific failure: the loading overlay completed
without rows or a persistent error. Connection-pool eviction in `logout()` ran synchronously
in UI `finally` blocks; TLS shutdown can write to a socket there. Eviction now runs on
the connection executor, and renewal errors remain visible. The updated APK again passed
all 76 tests and lint and was installed. After owner authentication, the renewal dashboard
reported successful APIv2 list refresh and displayed 10 matches. No renewal was submitted.
JVM tests cannot validate Android's main-thread network enforcement.

The newer 94-test build adds app PIN/privacy options, client sorting, public-URL helpers,
VPS traffic and billing reminders, profile organization/offline summaries and save safeguards.
New fixtures cover minimum PIN length/salts/delays, quota baselines/resets/accounting modes,
renewal date anchors/windows, token guidance, URL preservation, metadata defaults, read-only
transport gates, hash receipts, duplicate prevention and secret redaction. These new features
have not yet completed physical-device navigation, PIN enrollment or background notification
delivery tests. SaveGuard orchestration is not covered by a live submitted-write test; its
pure receipt logic is fixture-tested. No production-readiness claim is implied.

No live panel mutation, certificate-change approval, background-alert opt-in, latency probe,
backup restore or real renewal has been performed as part of this update. Long-running
OEM background reliability and submitted renewal behavior still need verification.
Passing JVM tests and lint is not a production or security certification.

## Emulator smoke checks (2026-09-07)

Using the loopback-only, in-memory APIv2 fixture in `tools/apiv2_fixture.py`:

- Loaded two inbounds and one client; dashboard CPU, memory and core status matched the
  fixture values.
- Opened the About bottom sheet and verified its APIv2 explanation and SONIX footer.
- Opened an existing VLESS inbound without an application crash. Transport fields were
  hidden while disabled and appeared when enabled. Saved an HTTP transport path and
  confirmed it through GET; the unrelated `future_option` field was preserved.
- Opened Hysteria2, enabled Ignore client bandwidth and verified upload/download fields
  disappeared. Enabling Masquerade revealed its type selector and dependent input.
  Disabled Masquerade again, saved, and confirmed `ignore_client_bandwidth: true` by GET.

These checks use a simulated server, not a real panel or sing-box. The emulator also had
System UI/UiAutomation instability, so this is not a long-running stability certification.
The inbound list's cramped action row was subsequently moved below its information row.

## Physical-device read-only audit (2026-09-08)

Mi 9T Pro, Android 11 / MIUI 12.5, debug 2.2. Installed in place with existing profiles
preserved. The live profile returned 203 clients, 4 inbounds, 2 outbounds, 1 endpoint,
1 TLS template, and no services during the audit.

Opened Overview, Clients and its editor, Inbounds and its editor, all six network Tools
lists, Core settings, HTTP clients, Certificate providers, Panel settings, Activity,
Logs, outbound traffic history, the backup sheet, App settings, and About. Verified
the About APIv2 explanation and "Developed by SONIX" footer.

After the fixes, observed the centered branded startup loader, the previously blank
TLS editor opening with its record, readable outbound protocol/action rows, wrapped
routing rule controls, and populated traffic history. Screenshots are local QA artifacts
under build/ and may contain panel metadata; do not publish them without reviewing.

No live-panel save, clone, delete, reset, restart, restore, or outbound probe was run.
The backup UI was inspected without exporting the database. This audit verifies
read/navigation behavior, not live mutation correctness or every protocol option.

## Fixes from the device audit

- Full-width content bounds and a shared centered request card replace the misplaced
  spinner. Failed record loads retain navigation and provide a retry action.
- TLS editing and WireGuard sharing parse APIv2's object-wrapped collection arrays.
- WireGuard peer export mirrors WgQrCode.vue and refuses to export the server's private key.
- Outbound batch tests execute sequentially inside one task and settle failed requests;
  the old loop dropped all but the first request.
- Logs, Activity and traffic-history loads follow filter changes with cancellable GETs;
  error states no longer silently look like an empty result. Traffic totals sum integer
  bytes before formatting.
- Routing has a rule-set edit action; duplicated/mislabelled import navigation was removed.
- Panel-settings tabs scroll horizontally; app/core restart controls ask for confirmation.
- HTTP clients has an informative empty state and device Back closes its local editor.
- Android force-dark is explicitly disabled because Compose supplies both light and dark
  palettes. The XML override is scoped to Android 10+; the View call is version-guarded.
  Reference: [Android dark-theme guidance](https://developer.android.com/develop/ui/views/theming/darktheme).

## Important behavior changes

- Full GET before editing clients/inbounds and bulk client edits; do not post summary rows.
- No device timestamp is passed as the server's incremental-load cursor.
- API response bodies are read off the main thread.
- Session/profile changes replace the observable session and reset status history.
- Network/disk rates are calculated from counter deltas; disk totals are not labelled rates.
- Transport, multiplex, listen, TLS, QUIC and masquerade controls reveal dependent fields.
- Switching transport types removes fields from the previous transport.
- Wallpaper colors are applied on Android 12+. The separate Persian-digit display setting
  has been removed; Persian keyboard input normalization remains supported.
- About opens as an animated bottom sheet from onboarding, Tools or App settings. English
  and Persian descriptions explain APIv2 communication and carry the SONIX developer credit.
- The SONIX Switchline icon has adaptive color and monochrome/themed versions. Editable
  artwork is in `design/sonix-switchline.svg`.
- Enabling app lock requires a successful device authentication; unavailable authentication
  cannot silently enable a lock that the owner cannot pass.
- Database exports are sensitive, unencrypted SQLite files; store them securely.

## Release gates still requiring a real deployment

### Latest physical follow-up (2026-09-09)

Installed debug 2.3 in place on the connected Mi 9T Pro, retaining the NY profile and
AMOLED/Emerald appearance. Verified Clients and Inbounds have a header + beside Refresh
and no reserved bottom action bar. The client draft opens from the header. Services
also uses a header +; the service draft has readable address/port fields and seven type
choices. OOM protection hides listener/TLS controls. Enabling DERP STUN reveals its
listener fields. All local drafts were discarded; no panel writes were submitted.
The current app process's AndroidRuntime error log was empty after these checks.

Earlier 2.3 physical checks verified the backup document picker opens without crashing
(cancelled without exporting), WireGuard-only QR eligibility, endpoint type/mode fields,
Light readability, the exact AMOLED black base, immediate accent updates, and restoration
of the Clients scroll position after opening an editor and returning.

### Remaining deployment checks

- Live GET connectivity and navigation have been checked on the connected panel; still
  verify behavior across other deployed s-ui versions, reverse proxies and token scopes.
- Create/edit/delete and GET-back comparison for each protocol used in production, including
  TLS/REALITY, transport, multiplex, user assignment and shared HTTP client references.
- Confirm sing-box starts successfully after each valid configuration; ensure rejected
  configurations display useful errors and do not misrepresent local state as saved.
- Exercise multiple profiles, credential expiry, interrupted requests, reconnects and backup
  import in a disposable panel. Never test restore or restart on production without approval.
- Review small screens, accessibility, light/dark themes, Persian/RTL and large fonts.
- Sign the release with the owner's production key. Debug builds are for testing.

The source is not a substitute for these release gates; no claim of complete production
validation is made without a real panel and device test matrix.
