# Release verification — 2.6.0 (11)

Results reviewed November 19, 2026. Targets **s-ui 1.6.8 / sing-box 1.14.4**,
frontend `461ec847b354d9c98ba80f2c74adc83eee3abade`.
See [compatibility](PANEL-1.6.8.md) and [release notes](CHANGES-2.6.0.md).

- **175 JVM tests passed** in 24 suites; no failures, errors or skipped tests.
  New coverage includes boolean inbound enable/disable with full-record saves,
  Gecko cleanup, atomic ECH PEM replacement and public-name API requests,
  Happ URLs, opaque proxy/ECH links, Snell addresses, WireGuard zero listen port,
  client last-online timestamps, multiple inbound IDs and retained core version.
- Lint passed with **0 errors / 9 warnings per variant**. All warnings are
  dependency-update advisories: Kotlin Compose plugin, Lifecycle (two artifacts),
  Activity Compose, Compose BOM, Coroutines (two artifacts), OkHttp and ZXing.
  No suppression or baseline was added. Offline lint is not a vulnerability audit.
- Final verification completed successfully with these Gradle tasks:
  `testDebugUnitTest lintDebug lintRelease compileReleaseKotlin
  compileReleaseJavaWithJavac processReleaseResources minifyReleaseWithR8`.
  This checks compilation, resources and release code optimization without packaging.
  Final run: **BUILD SUCCESSFUL**, 66 tasks (18 executed, 48 up-to-date).
  Completion was confirmed from the Gradle daemon log after the chat interruption.
- **No APK/AAB was generated or signed.** Older local build artifacts were left untouched;
  their signatures, alignment and packaging results do not certify this update.
- Source registry parity passed: **19 GET / 10 POST / 29 writable settings**,
  **19 inbound / 19 outbound / 13 DNS / 8 service / 6 endpoint types**.
  OpenVPN CBC, static/TLS cipher additions and digest choices match the supplied frontend.
- Documentation links, patch formatting and Persian README RTL checks passed
  (nine RTL headings, three tables, LTR address blocks). Hosted GitHub Actions
  was not run; its workflow was not changed in this update.

Automated HTTP tests use local fixtures. No live panel or device was accessed or changed.
Live inbound toggles, actual Happ/Clash/sing-box subscription output, Gecko connections,
ECH handshakes, signed-device UI and OEM background behavior still require controlled
acceptance tests. Local HTTP fixtures verify Android requests/parsing, not a running backend.
Panel updates/checks are web-session-only in this backend and are not exposed as fake
APIv2 actions. Signing, final package verification and publishing remain owner-controlled.
