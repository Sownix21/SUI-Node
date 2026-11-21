# s-ui 1.6.8 compatibility review

Reference: supplied backend `config/version` 1.6.8 (Go 1.27.3, sing-box 1.14.4),
frontend `461ec847b354d9c98ba80f2c74adc83eee3abade` / 1.6.8.
Compared against the previously supplied 1.6.7 trees; only Android app files are changed.

| Upstream source | App handling |
| --- | --- |
| `database/model/inbounds.go`, `service/inbounds.go`, `views/Inbounds.vue` | GET summary/detail expose boolean `enable`; absent means enabled. Fetch full record, POST `save` with object `inbounds`, action `edit`, complete JSON including boolean. Never save a list summary as a full inbound. |
| `components/protocols/Hysteria2.vue` | `obfs.type`: salamander/gecko; optional numeric `min_packet_size`/`max_packet_size`; absent defaults 512/1200; remove sizes on Salamander. Shared inbound/outbound UI. |
| `layouts/modals/Inbound.vue`, `sub/jsonService.go` | Enable Snell client dial/addrs tab. Backend derives Snell version/PSK/mode from server fields; no fake client overrides for those generated fields. |
| `layouts/modals/QrCode.vue`, `sub/subHandler.go`, `sub/happ.go`, `sub/userAgent.go` | `?format=happ` and `happ://add/` followed by the unencoded subscription URL. Default subscription omits `format`, enabling backend User-Agent detection. Explicit JSON/Clash URLs retained. Happ excludes HTTP/HTTPS and maps socks5 to socks on the server. |
| `components/tls/Ech.vue`, APIv2 keypairs | GET `keypairs?k=ech&o=<public-name>`; paired PEM arrays. Public name is generator input, not a persisted sing-box option. Do not silently use private SNI. Query name is inactive next to explicit config. |
| `service/server.go` | Show `sys.coreVersion` from APIv2 status; absent field shows a dash. |
| `service/client.go`, `service/stats.go`, `database/model/model.go` | Show `onlineAt` Unix seconds, updated from traffic statistics; no fabricated connection time. Multiple inbound IDs remain a JSON array. |
| `components/protocols/Wireguard.vue` | Already aligned: blank WireGuard listen port posts numeric zero. |

Backend-only fixes (not duplicated in Android): proxy/ECH link generation and import,
Gecko Clash conversion, Snell multi-address export, VLESS encryption preservation,
WebSocket Host/early-data retention, each address's TLS override in subscriptions,
multiple-inbound SQLite JSON handling, IP-domain TLS handshakes, RHEL installer support,
and Go dependency/security updates. The app retains opaque panel links and nested data.
Server TLS relaxation is not a reason to disable Android HTTPS validation.

APIv2 route registries are unchanged. Panel updates/checks remain web-session-only;
no legacy API calls or unsupported updater controls were added. Live panel/device behavior
is separate from local fixture/compile checks; see [verification](VERIFICATION.md).
