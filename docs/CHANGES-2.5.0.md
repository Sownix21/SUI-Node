# S-UI Node 2.5.0

October 31, 2026 · version code **10** · s-ui **1.6.7** / sing-box **1.14.4**

- Hysteria2 realm inbound/outbound controls, IPv6/port-mapping dependency and
  mutually exclusive outbound direct-address/port-hopping fields.
- Realm-aware client-side token override and Hysteria Realm service users/TLS/listener.
- Capability-gated subscription client-remark-prefix toggle, sent as a string setting.
- OpenConnect client certificate generation via `GET keypairs?k=tls-client` and local
  password generation. Invalid PEM replies leave existing material untouched; valid
  pairs remove conflicting client paths/password while preserving CA trust settings.
- OpenVPN cipher/digest selection, optional credentials, cipher overrides, interface,
  MTU, client limit and IPv6-peer fields; correct static-key cipher-mode cleanup.
- Conditional experimental pprof, memory-limit and signed GC-percent fields.
- Serialized status polls and rejection of stale results after session reset.
- Updated English/Persian documentation and regression coverage.

Panel updater/check-update routes remain web-session-only and are intentionally not
called by this APIv2 client. Installer hardening, dependency patches, QUIC session
sweeping, stats batching, Profile-Title sanitization and external XHTTP subscription
conversion remain panel/core responsibilities. Install the updated panel to get them.

See [compatibility](PANEL-1.6.7.md) and [verification](VERIFICATION.md). This update is
validated without APK/AAB generation or live-panel writes; owner signing and runtime
acceptance remain separate.

Verification: **163 tests passed**; release compilation, resource processing and R8
optimization succeeded. Lint reports **0 errors** and two existing dependency-version
advisories per variant. No APK/AAB packaging or signing task was run.
