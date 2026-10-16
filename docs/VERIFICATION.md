# Release verification — 2.4.3 (9)

October 16, 2026. Targets **s-ui 1.6.5 / sing-box 1.14.3**, frontend
`abd7f342f6f6dcb626bcf70a25b092f4727a4f2d`.
See [compatibility](PANEL-1.6.5.md) and [release notes](CHANGES-2.4.3.md).

- **154 JVM tests passed** in 22 suites; no failures, errors or skipped tests.
- Lint passed with **0 errors** and one existing OkHttp version-update advisory per variant.
  No warning suppression was added. Offline lint is not a vulnerability audit.
- Optimized unsigned APK/AAB builds passed; APK 16 KB ZIP alignment passed.
- Source registry parity: **19 GET / 10 POST / 28 writable settings** and
  **19 inbound / 19 outbound / 13 DNS types**.
- Local workflow lint, documentation links and Persian README RTL checks passed.
- Source packaging excludes generated output, local credentials and signing material.

This cleanup changes documentation and repository packaging only, not application
behavior. Automated tests use local fixtures; no live panel or device was accessed.
Signed-device checks and disposable-panel acceptance remain manual, including response
headers after restart, gzip, custom probes/imports and periodic resets. Hosted CI was not
run. Production signing and publishing remain owner-controlled.
