# S-UI Node 2.4

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

## Verification

119 JVM tests passed; debug/release lint has no errors. Debug APK, optimized release APK
and AAB builds passed. Read-only navigation and conditional TLS drafts were checked on a
Mi 9T Pro using the optimized build. See [VERIFICATION](VERIFICATION.md) for artifact hashes,
the exact test scope, lint warnings and remaining acceptance/signing requirements.

No live panel writes were performed. Protocol write-and-connect acceptance still requires
a disposable panel; unsigned output and debug-key QA copies must not be published as
production-signed releases.
