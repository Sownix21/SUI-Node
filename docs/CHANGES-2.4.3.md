# S-UI Node 2.4.3

October 16, 2026 · Android version code **9**

Compatibility update for **s-ui 1.6.5**, **sing-box 1.14.3** and frontend
`abd7f342f6f6dcb626bcf70a25b092f4727a4f2d`.

## Changes

- Add custom subscription response headers in Panel settings → Subscription when
  the panel exposes `subCustomHeaders`. Preserve the exact multiline string through
  APIv2 GET/save, including an empty string for clearing. Explain public visibility
  and the explicit panel restart needed to apply changes.
- Add an optional custom HTTP/HTTPS latency-test URL to Outbounds and Endpoints.
  The field is conditional, blank/disabled selects the core default, and malformed
  nonempty input blocks tests. Test all captures one target for the entire batch.
- Share the test target between both screens within the current panel session,
  without writing potentially private URLs to disk. Clear displayed results when
  the target changes. Active probes continue to respect local read-only mode.
- Use the strict latency-result parser for outbounds too: missing/invalid delay is
  an error, not a successful zero-millisecond result.
- Add regression coverage for header round trips/clearing, older-panel settings,
  URL validation and query encoding, probe errors/read-only mode, periodic client
  reset snapshots and opaque external-link passthrough to the panel converter.
- Update English and RTL Persian product documentation and release metadata.

Subscription gzip is automatic on the panel. Clash UDP/skip-cert-verify corrections,
external-link decoding and periodic-reset change notifications are backend fixes;
Android does not replace the panel's converters, add fake toggles or run migrations.
Go and frontend package upgrades do not require Android dependency version changes.

## Verification

All **154 JVM tests passed**. Lint reported **0 errors** and one existing
OkHttp dependency-update advisory each. Optimized unsigned APK/AAB builds, APK 16 KB
alignment, registry parity, workflow lint and Persian README direction checks passed.

See [VERIFICATION](VERIFICATION.md) for the current checks and their scope, and
[the source review](PANEL-1.6.5.md) for the APIv2 mapping. Production signing and
publishing remain owner-controlled. No live panel or device operation was performed.
