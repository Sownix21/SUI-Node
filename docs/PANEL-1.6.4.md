# s-ui 1.6.4 compatibility review

Reviewed October 9, 2026 against the supplied backend (`config/version`: 1.6.4;
`go.mod`: sing-box 1.14.2) and frontend
`e4525297b002c1c3be234cc1c9695ef84be741a0` (`package.json`: 1.6.4).
The supplied code is the reference; no live panel was modified.

## Change scope

Compared the [backend release diff](https://github.com/alireza0/s-ui/compare/v1.6.3...v1.6.4)
and [frontend snapshot diff](https://github.com/alireza0/s-ui-frontend/compare/f859e16953cd733293618f626cc19b8466e00fd3...e4525297b002c1c3be234cc1c9695ef84be741a0),
then checked the affected local handlers, services, migration, components and tests.

- `api/apiV2Handler.go`: unchanged **19 GET / 10 POST** actions, token header and JSON
  envelopes. No cookie-login or frontend-only route was introduced into the app.
- `service/setting.go`: **27 writable settings**, same five protected bookkeeping keys.
  `globalReset` now validates standard five-field cron, optional leading seconds and
  descriptors with the panel's cron parser. Empty or `off` disables it. A changed schedule
  clears the internal armed boundary; the background job reads the current spec and arms
  the next boundary without restarting. Android preserves the full string and displays
  server validation failures rather than implementing a conflicting cron grammar.
- `api/apiService.go` / `service/client.go`: `POST resetTraffic` still has no form payload
  and returns an envelope. It resets current counters, accumulates lifetime totals and
  re-enables all clients. Updated inbound users replace the former core restart. Android
  does not add a restart, explains the effects, and invalidates stale monitoring snapshots
  only after a successful response.
- `core/usersession/registry.go` and QUIC inbounds: bounded 30-second fallback mute and
  proper connection-close handling fix reconnect behavior. `closeSessions` still posts
  client name in `u`; the app retains explicit confirmation and read-only safeguards.
- `cmd/migration/1_6_4.go` / `util/outJson.go`: server ECH and stored generated outbound
  ECH lose `pq_signature_schemes_enabled` and `dynamic_record_sizing_disabled`; generated
  outbounds no longer copy these fields. Android's structured TLS editor already omits
  both. Migration is performed by the panel; Android does not run migrations or silently
  rewrite unrelated JSON/backup contents.

## Frontend parity

- `HttpClient.vue` now uses unrestricted `Dial`: Android removes its client-only mode
  for both shared and inline HTTP clients. Existing conditional input groups are reused.
- `DnsRule.vue` exposes source CIDRs and the private-source switch. Android now provides
  the same conditional group and type switch, including IPv6 CIDRs and explicit false.
- `Dns.vue` / `Rules.vue` add touch-friendly move buttons. Both already existed in Android;
  repeated DNS mutations now replace the observable draft to avoid stale list ordering.
- `Clients.vue` sorts traffic by `up + down`, not quota. Android already offers explicit
  Traffic used / Traffic quota modes; regression coverage includes unlimited clients.
- Protocol registries remain **19 inbound / 19 outbound / 13 DNS types**. No new protocol
  forms are necessary for this release. Web translation/dependency updates are not
  Android API features and are not copied as Android dependencies.

## Limits

Automated registry/HTTP-fixture tests do not certify every live protocol configuration.
The supplied backup code batches table copies, reserves unique temporary files and
checkpoints SQLite WAL before returning bytes; it was not changed by this release diff.
This source review does not establish that the earlier device's full-backup failure is
resolved on that deployment. Backup bytes remain unencrypted and unmodified as requested.

See [VERIFICATION](VERIFICATION.md) for this Android update's checks. Signed-device,
disposable-panel write/connect and OEM background acceptance remain separate release gates.
