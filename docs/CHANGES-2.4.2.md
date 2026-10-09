# S-UI Node 2.4.2

October 9, 2026 · Android version code **8**

Compatibility update for the supplied **s-ui 1.6.4**, frontend
`e4525297b002c1c3be234cc1c9695ef84be741a0` and **sing-box 1.14.2**.

## Changes

- Expose full dial controls for shared and inline HTTP clients: detour, interface and
  IPv4/IPv6 binding, bind-without-port, routing mark, address reuse and DNS resolver.
- Add conditional DNS source-IP controls with correct CIDR-array/boolean wire values.
  Changing the match type clears its alternative; disabling the group clears both.
  Existing advanced rules containing both conditions remain visible until explicitly changed.
- Fix repeated DNS rule reordering/deletion by updating the observable draft after each
  operation. Reordered nested and unknown fields are retained. Changes still require Save.
- Correct the all-client traffic-reset warning for 1.6.4's in-place inbound-user update;
  explicitly warn that manually disabled clients are also re-enabled.
- Invalidate pre-reset monitoring snapshots after a successful global traffic reset,
  preventing old quota snapshots from publishing after that reset.
- Explain server-validated global reset schedules and their no-restart behavior. Invalid
  cron responses remain visible; internal reset bookkeeping is never posted as settings.
- Retain usage-based sorting separately from quota sorting, including unlimited clients.
- Resolve committed merge-conflict markers in the GitHub Android workflow. Retain the
  pinned Android setup action and separate SDK package arguments.
- Update English/Persian product documentation and the compatibility review.

The APIv2 routes and supported protocol registries did not change. ECH data migration
and QUIC disconnect/restart fixes belong to the updated panel/core, not Android-side
replacement protocols. See [the source review](PANEL-1.6.4.md).

## Verification and release

All **145 JVM tests** passed; debug/release lint reported **0 errors / 0 warnings** in
the offline run. Optimized unsigned APK/AAB builds, APK 16 KB ZIP alignment, APIv2 registry
parity, local workflow lint and Persian README RTL checks passed.
See [VERIFICATION](VERIFICATION.md) for hashes, scope and remaining acceptance checks.
Production signing, installation of the new signed build and GitHub publishing remain
owner-controlled. No live panel operation or device-setting change was performed.
