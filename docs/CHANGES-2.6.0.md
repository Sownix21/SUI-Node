# S-UI Node 2.6.0 (11)

November 19, 2026 — source compatibility update for s-ui 1.6.8.

- Inbound enable/disable controls fetch the complete record before posting the boolean
  through APIv2. Disabled state is visible; type changes preserve it. Controls are not
  offered for older records without this capability. Disabling preserves assignments.
- Shared inbound/outbound Hysteria2 obfuscation form supports Salamander and Gecko.
  Packet sizes appear only for Gecko, omit empty/zero values, and clear when leaving Gecko.
- Snell inbounds now expose the client-side dial/multi-domain tab. Per-address TLS uses
  the full existing TLS form, including ECH, and is hidden for protocols without TLS.
- Happ subscription and direct-import QR/copy actions; encoded client-name path segments.
  Default URLs retain the backend's User-Agent selection. No local proxy-link conversion.
- ECH generation requests a public outer name rather than reusing private SNI. Both PEM
  blocks must validate before replacing keys/configs; conflicting paths/query names clear.
  Generated PEM text now refreshes immediately in the editor; inline ECH input is multiline.
- Dashboard displays `sys.coreVersion`. Client cards and details display `onlineAt`
  in local time; absent/zero values show “Not recorded”.
- English and RTL Persian documentation and new form labels updated.

See [source review](PANEL-1.6.8.md) and [verification](VERIFICATION.md).

Verification: 175 tests passed; debug/release lint passed with zero errors and nine
dependency-update advisories each; release compilation, resources and R8 optimization
passed. No APK/AAB was generated or signed, and no live panel was accessed.
