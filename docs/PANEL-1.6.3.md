# s-ui 1.6.3 compatibility review

Reviewed September 30, 2026 against the supplied backend reporting 1.6.3 and frontend
`f859e16953cd733293618f626cc19b8466e00fd3` reporting 1.6.3. The backend declares sing-box
1.14.1. This records the local snapshots, not a guarantee about every upstream deployment.

October 1 follow-up: rechecked the folder the owner identified as the updated reference.
It still reports 1.6.3; routes/settings/protocol selectors remain unchanged. The local tree
does not contain `cmd/migration/1_6_4.go` and still copies the legacy ECH fields in
`util/outJson.go`. The separately reviewed upstream commit
[`84854f8`](https://github.com/alireza0/s-ui/commit/84854f87b21a7e1b494bc26d00643b782c3f70e2)
removes `pq_signature_schemes_enabled` and `dynamic_record_sizing_disabled` from persisted
server ECH/generated outbounds and stops copying them. It introduces no new APIv2 route
or form input. The app's structured ECH controls already omit both options; backend-side
migration is responsible for cleaning old persisted data. No live migration was run here.

## APIv2 and response formats

- 19 GET actions, 10 POST actions and 27 writable settings match the supplied backend.
- `GET sessions?resource=user|inbound|outbound|endpoint&tag=...` returns an envelope whose
  `obj` is a session array (or null). Optional metadata is accepted; malformed records and
  rejected/unsupported actions are errors, not an apparently empty successful result.
- `POST closeSessions` sends form field `u` with the selected client name, not its database
  ID. It closes connections without disabling the client. UI confirmation, local read-only
  enforcement and the existing pending-write guard apply. No automatic retry is performed.
- `POST getCertPing` sends `domain` and `port`; its object contains `leafHash`. The existing
  probe is retained and now has an explicit wire-contract regression test and visible errors.
- Configuration collections still use their object-wrapped arrays. Full-record GET before
  edits, unknown-field preservation, settings filtering and token-error guidance remain.

## Editors and conditional controls

- Retained the existing Cloudflared, Snell v5/v6 inbound and v4/v6 outbound, Bridge,
  OpenConnect/OpenVPN and shared HTTP/certificate-provider work in the current app.
- Snell now participates in initial inbound user assignment. New client credentials include
  a 32-character `userkey`; existing clients can explicitly add it without resetting other
  credentials. Editing, regeneration and rename handling use the backend's field names.
- Added mDNS to DNS server types and `route.default_http_client` as a shared-tag selector.
- Hysteria editors use the shared QUIC tuning fields instead of offering deprecated receive
  windows. Existing unedited legacy/unknown data is not silently deleted.
- TLS cipher suites and handshake timeouts reveal their inputs only when enabled. Mutual
  TLS uses an array for server-side CA paths, strings for outbound paths, and PEM line arrays
  for inline material; switching source clears the alternate representation.
- Record fragmentation and fallback delay belong to the TLS fragmentation toggle. Turning
  it off removes dependent fields. HTTP-version changes clear inapplicable tuning keys.
- Corrected kTLS switches to send true when enabled. Endpoints are online when referenced
  on either side of a connection. Active endpoint probes are disabled in read-only mode.
- Stopped offering new inline TLS ACME blocks; shared certificate providers are the current
  frontend path. Existing legacy blocks remain visible through an explanatory notice and
  Advanced JSON, rather than being silently discarded.

## Verification and limits

The source checker now compares inbound, outbound and DNS protocol selectors too:
19 inbound types, 19 outbound types and 13 DNS types. Registry parity is not field-by-field
runtime certification. Added fixtures cover sessions, disconnect form encoding/read-only
gating, certificate probing, malformed responses, Snell credentials and dependent-field
cleanup. See [VERIFICATION](VERIFICATION.md) for the latest actual build/device results.

No live panel save, disconnect, probe, restore or restart is authorized by this review.
Submitted configuration acceptance and real connections for each protocol must be tested
on a disposable panel. The supplied backend still copies backup tables with a bulk Save;
the previously observed stats-table SQL-variable limit must not be assumed fixed. Backups
remain the original unencrypted SQLite bytes, with exclusions only when explicitly chosen.
