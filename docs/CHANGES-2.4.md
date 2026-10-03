# S-UI Node 2.4.1

Compatibility update for the supplied s-ui 1.6.3 backend/frontend and sing-box 1.14.1.

## New and improved

- Live APIv2 sessions, resource/tag filtering, optional foreground refresh and shortcuts
  from client, inbound, outbound and endpoint lists. Disconnecting a client's sessions
  requires confirmation and respects read-only mode.
- Snell client credentials and initial inbound user assignment, including an explicit
  add-credentials action for existing clients without resetting other protocol keys.
- mDNS server selection and a shared default HTTP-client selector for routing.
- Conditional TLS cipher suites, handshake timeouts, mutual TLS, certificate source and
  record-fragmentation controls matching the current wire formats.
- Current Hysteria/QUIC tuning fields, HTTP-version-dependent cleanup, corrected kTLS
  switch values, and visible certificate-probe errors.
- Endpoint online detection checks both connection directions. Endpoint probes respect
  local read-only mode.
- Endpoint latency status now has a full-width row, with wrapping action buttons below
  it, so the optional WireGuard QR action cannot squeeze the status into broken words.
- Current shared certificate-provider workflow replaces new legacy inline ACME setup;
  existing legacy data is preserved for explicit migration.
- PIN-first app lock: an 8–32 digit app PIN is required before optional strong biometrics.
  Existing biometric-only locks migrate after authentication; missing/unreadable PIN
  records do not fall back to an unprotected unlock.
- Freshness checks suppress pre-renewal monitoring snapshots. Expiry extensions no longer
  immediately repeat warnings; warning days/hours, check intervals and repeat intervals
  are distinct controls. Server-only monitoring avoids full client-detail batches.
- VPS traffic tracking can start now or at the next billing reset, including reset hour,
  minute and timezone. Billing calendar changes require explicit baseline handling.
- Calendar/time popups for client and bulk expiry editing, localized numeric input,
  preserved monitoring drafts, lazy alert-history rendering and failed-delivery retries.
- Comprehensive Persian README alongside the English product guide. Removed the separate
  contribution guide and its source-export entry.
- Compact client expiry shortcuts keep the infinity action in the same row. VPS renewal
  dates use a calendar popup without timezone-driven date shifts; billing reset time and
  quiet hours have popup controls. Cancel does not change the saved draft value.
- Persian documentation now has explicit RTL layout and isolated Latin names/addresses.
- GitHub workflow initializes SDK tools and passes SDK packages separately, avoiding the
  missing `sdkmanager`, obsolete `tools` and multiline-package errors.
- App-wide profile storage retains only the Application context. QR images use a single
  bulk pixel upload, avoiding hundreds of thousands of individual bitmap calls.
- Quiet-hours popups offer whole-hour choices instead of accepting and discarding minutes.
- Version code 7 provides an upgrade path from 2.4 (6) when signed with the same owner key.
- Follow-up: PIN setup/change/removal and lock confirmations use a popup with a scrollable
  form and separate action buttons. Enabling biometrics prompts Android authentication
  directly; no repeat app-PIN entry is needed. A configured PIN and an unlocked session
  remain mandatory, and opt-in is persisted only after successful biometric authentication.

## Verification

137 JVM tests passed; debug/release lint has no errors (2 toolchain-update warnings in the
offline run). Debug APK, optimized release APK and AAB builds passed. The GitHub workflow
passed actionlint. Release ZIP alignment and packaged native ELF alignment were verified
at 16 KB. Earlier read-only navigation and conditional TLS drafts were checked on a Mi 9T Pro.
The owner's signed build passed read-only navigation and a lock/backup-resume check.
The later PIN-popup/direct-biometric changes still need a matching signed update for
physical-device acceptance.
See [VERIFICATION](VERIFICATION.md) for artifact hashes,
the exact test scope, lint warnings and remaining acceptance/signing requirements.

No live panel writes were performed. Protocol write-and-connect acceptance still requires
a disposable panel; unsigned output and debug-key QA copies must not be published as
production-signed releases.
