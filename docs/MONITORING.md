# Optional monitoring and renewals

Monitoring is disabled by default. Open Monitoring & alerts in Tools, choose options,
and save with the tick. It uses only APIv2 GET clients/status, never changes a client,
restarts the core, exports backups or runs latency probes. Notification permission is
for posting this app's alerts, not reading other apps' notifications.

Configurable options include selected panels, check interval, expiry warning window,
quota percentage, disabled-client inclusion, consecutive failures before unavailable
alerts, core-stopped alerts, recovery notifications, repeat interval, local quiet hours,
notification privacy and history retention. Unlimited and first-use expiry are handled
separately. Quota usage uses current upload/download counters, not lifetime counters.
Missing core data is unknown, not stopped. Unavailable means unreachable/rejected from
this phone and can also reflect network, token or certificate problems.
Rejected API tokens receive specific invalid-or-expired guidance; the backend does not
distinguish those cases. Device-network absence does not increment panel-offline failures.
On 1.6.1+, `sbd.maintenance=true` identifies an intentional core stop and suppresses
core-stopped failure alerts. Older panels without that flag keep their existing behavior.

Expiry warnings can be configured in 1–90 remaining days or 1–2160 hours. Checks offer
15/30 minutes and 1/2/3/4/6/8/12/24 hours; repeating a previously reported warning is a
separate 0–168 hour setting (0 = once). Newly detected conditions are checked at the
check interval, not the repeat interval. Notifications are grouped separately per panel.

Successful in-app client saves invalidate pre-save monitoring snapshots, clear that
panel's displayed notification, and request a fresh check when monitoring is enabled.
Extending an expiry that is still inside the warning window preserves the previous
last-sent time and suppresses a new warning for one check interval. A worsening condition
can still escalate. Renewals outside the warning window resolve the condition on the
next successful read. External edits cannot be observed until another successful GET;
the backend does not provide push notifications or atomic read/write monitoring snapshots.

WorkManager checks are approximate (minimum 15 minutes). Doze, offline periods, battery
saver, device-lock restrictions and OEM battery/autostart policies can delay them.
Force-stop prevents work until the app is reopened. No exact-alarm or notification-listener
permission is requested. The screen links to device settings and reports the last check.
Delivery is best effort, not a paging/SLA service. Pending active alerts can be delivered
after quiet hours or after notifications are permitted; resolved alerts are not replayed
as stale warnings. Local encrypted history is bounded to 500 events and the chosen age.

Refreshing check status preserves unsaved monitoring edits. Alert history renders lazily
instead of building all 500 cards at once. Server-only monitoring skips full client-detail
batches. If Android explicitly rejects notification delivery, active warnings remain
eligible for a later check. Turning an alert type off does not emit a false recovery notice.

## Optional VPS traffic estimates

Each panel has a separately disabled-by-default VPS traffic tracker. Choose combined,
receive-only, send-only or separate directional limits, a monthly reset day/time, provider
timezone and warning percentage. Limits use decimal GB/TB, not client quota GiB. A manual
starting-usage correction lets the user match the provider's current accounting. The first
sample establishes a baseline; it does not charge the server's lifetime counters as this
month's usage. Subsequent APIv2 status GETs use `net.recv`, `net.sent` and `sys.bootTime`.

Choose Now to start at the first successful sample, or At the next billing reset to defer
counting until the saved next reset date/time. Waiting samples and counters do not create
quota alerts. The first successful sample after that reset establishes a zero baseline;
Android may delay it, and unobserved traffic before it cannot be reconstructed. The saved
start date is shown and is not moved forward by an unrelated settings save. Switching back
to Now retains already observed usage unless explicitly corrected. A stale sample or
backward device-clock jump does not overwrite newer usage within the same baseline.

This is an estimate, not the provider's billing API. The panel counts server interfaces;
provider exclusions, downtime, restarts and missed cycle boundaries can differ. Counter
resets and ambiguous cycle gaps are marked uncertain rather than silently claiming exact
usage. Reset days 29–31 clamp to shorter months. No server traffic counter is reset.
Background alerts require the global Monitoring switch and this panel in its scope.

The quota editor previews the next reset date. Changing a saved billing calendar with an
existing sample requires an explicit already-used-traffic correction or a next-cycle start. Hidden limits from
other accounting modes do not prevent saving the selected mode, and disabling tracking
preserves the last saved configuration rather than saving invalid hidden drafts.

## Optional VPS billing reminders

Each panel can independently enable a next renewal date, monthly/quarterly/yearly/custom-day
period, optional price and currency, billing timezone, and 0–90 day warning window.
Dates and price are local encrypted notes: no provider integration, payment or panel write.
Reminders become overdue after the due date. Mark renewed advances only the draft by one
period; the user must save it. Month-end anchors survive February and short months.
Dates never advance automatically because a scheduled date does not prove payment.

Enable global Monitoring and include the panel for notification delivery. Local billing
reminders can be evaluated without network access; WorkManager/OEM/device-unlock delays,
quiet hours, privacy and repeat settings still apply. Disabling either the local reminder
or all Monitoring stops future checks for that feature. No background opt-in is forced.

## Client renewal operations

The renewal dashboard reads multiple saved panels, with panel/group/urgency/search filters.
Preview fetches full client records and displays exact before/after expiry, quota and
enable state. No-op clients are omitted. Submission requires explicit confirmation,
re-fetches records to detect intervening configuration changes, preserves unrelated fields,
and verifies the result with GET. Traffic is not reset; unlimited and first-use settings
are preserved. At most 50 clients are included per preview.

APIv2 has no transaction spanning panels and no atomic compare-and-swap version check.
Concurrent server edits can still race a save. A partially accepted batch is reported and
is never automatically retried. Review current state before rebuilding a failed preview.
