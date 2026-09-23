# Release checklist

This separates reproducible build checks from live acceptance tests. A passing build is
not certification of every server/core/protocol combination. The evidence for the current
source is recorded in [VERIFICATION](VERIFICATION.md).

## Before signing

- Run `testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease`.
- Review lint warnings; do not suppress errors just to obtain an artifact.
- Compare the exact supported backend/frontend revision and run the optional source-registry
  checker after every panel update. Review changed request/response schemas and forms too.
- Confirm release is non-debuggable, minification/resource shrinking completed, and native
  libraries support the target Android page size. Retain the matching R8 mapping privately.
- Check the source-only archive for local SDK paths, tokens, signing files, backups and
  real-device captures. Exclusions do not scrub embedded secrets or previous Git history.
- Preserve GPLv3, SONIX attribution and applicable third-party notices. Ship corresponding
  source for the exact distributed build, including the build scripts.

## Device and disposable-panel acceptance

- Verify an in-place upgrade with the same signing key preserves profiles and vault access.
- Test Light/Dark/AMOLED, accents, screen protection, Persian/RTL, large fonts and rotation.
- Open lists and edit drafts; verify populated full records, scrolling and conditional fields.
- On a **disposable panel**, create/edit/delete each protocol and compare GET results to
  submitted data and the web UI. Start the resulting core and test actual client connections.
- Exercise structured JSON/Clash subscription settings, TLS and endpoint variants, rule
  selectors, services and certificate provider options against that panel's supported core.
- Check invalid/expired tokens, TLS renewal/mismatch, offline errors and interrupted writes.
- Verify backup download bytes and restore on a disposable panel only.
- Test PIN enrolment/change/removal and throttling with owner-controlled credentials.
- With app lock enabled, choose a backup destination, authenticate after the picker and
  verify the exported SQLite file. Check cancelled pickers and all lock-timing presets.
- Opt into background monitoring on test profiles; verify actual notifications, quiet hours,
  permissions, device restart, Doze and OEM policies on the supported device matrix.
- Confirm expected maintenance suppresses unexpected-core-stop alerts.

Live write tests must not be run against an owner's production panel without explicit
permission. A skipped item must remain visible in release notes, not be reported as passed.

## Sign and publish

- Use the owner's production signing identity; do not ship a debug-key-signed QA artifact.
- Increment versionCode for a subsequent published update and choose a versionName.
- Build the signed release and run Android `apksigner verify --verbose` on the APK.
- Install/test that identity on a separate test profile/device; do not erase the owner's
  existing debug installation just to resolve a signature mismatch.
- Record artifact SHA-256, source revision, supported panel/core versions and limitations.
- Run hosted CI after uploading the app directory as the repository root. Local testing
  does not establish that a hosted workflow passed.
- Publish APK/AAB separately from the clean source. Never upload keys or local vault data.
