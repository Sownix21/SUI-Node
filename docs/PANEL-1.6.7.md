# s-ui 1.6.6–1.6.7 compatibility

Reviewed October 31, 2026 against supplied backend 1.6.7 (Go 1.27.3,
sing-box 1.14.4) and frontend `f473aeba368de6581cad70412c098ed98370cbcc`.

Cross-checked the official [backend diff](https://github.com/alireza0/s-ui/compare/v1.6.5...v1.6.7)
and [frontend diff](https://github.com/alireza0/s-ui-frontend/compare/abd7f342f6f6dcb626bcf70a25b092f4727a4f2d...f473aeba368de6581cad70412c098ed98370cbcc).
Other reviewed protocol/service form changes add web accessibility labels, rather than
new wire fields. Android's existing labeled controls remain in place.

## Contracts

- `api/apiV2Handler.go`: unchanged 19 GET / 10 POST actions. `checkUpdate` and
  `update` are registered only by `apiHandler.go`; Android must not call them.
- `service/setting.go`: adds `subRemarkPrefix`, default string `true`, making 29
  writable settings. UI is gated by GET field presence and saves string booleans.
- `service/server.go`: `keypairs` now accepts `k=tls-client`, optional common name
  in `o`, and returns PEM lines in the ordinary envelope. Generated client key pairs
  are not server-provisioned accounts or CA-signed trust enrollment.
- `Hysteria2Realm.vue`: realm URL/ID/token, explicit STUN list, IP version and optional
  port mapping timeout/lifetime. IPv6 clears mapping; outbound realm activation removes
  server/port/port-hopping. The inbound listener is retained.
- `util/outJson.go`: server generates realm subscriptions, preserves an explicit
  client token override and strips direct addressing. Android does not duplicate this
  conversion or rewrite generated links. The client-side editor hides hopping for realm.
  `sub/clashService.go` skips realm nodes; sing-box JSON subscriptions are the supported
  realm-capable output. Android must not invent ordinary address-based Clash nodes.
- `services.ts` / `HysteriaRealm.vue`: eighth service `hysteria-realm`, listener 8443,
  TLS template and `users` array of name/token/optional numeric `max_realms`.
- `OpenVpn.vue`: static CBC/NONE versus TLS AEAD/CBC/CFB/OFB lists, optional digest,
  credentials, interface, MTU, peer IPv6, client limit. Android retains unfamiliar
  existing values until edited and preserves configured cipher preference order.
- `Basics.vue`: optional `experimental.debug` with `listen`, string `memory_limit`,
  numeric `gc_percent` (including zero/negative). Cleared optional values are removed.

## Server-only fixes

Bound initUsers queries do not change the Android comma-separated numeric ID form.
Core idle-session sweeping and stats aggregation do not introduce new response shapes.
XHTTP conversion/preservation, WARP error handling, control-character stripping in
subscription headers, token locking and installer/release URL hardening are implemented
on the supplied backend. No Android migrations or new legacy API calls are needed.

Android polling awaits each cycle and is lifecycle-bound; a mutex additionally serializes
explicit maintenance refreshes and a reset generation prevents old status repopulation.
No polling or active probe was run against a live panel during this work.

Registry matching is not certification of every protocol combination. Real realm NAT
traversal, VPN certificate trust, subscription imports, live saves and signed-device UI
acceptance need a disposable panel and owner-approved testing.
