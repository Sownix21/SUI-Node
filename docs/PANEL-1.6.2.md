# s-ui 1.6.2 settings compatibility

The [1.6.2 release](https://github.com/alireza0/s-ui/releases/tag/v1.6.2) fixes
[settings round trips](https://github.com/alireza0/s-ui/commit/3a0759e6e16f3b88ef1050acf6deda24afc9f05f).
In 1.6.1, GET settings included internal migration rows, while Save rejected unknown keys.
Posting the read result could therefore fail with `unknown setting: migratedSingBox114`.
The fix withholds rows outside the known settings map, leaving the database flags intact.
APIv2 routes and POST encoding are unchanged.

The supplied frontend `ab6ed5148c02d311b19e1fe17e85e0e23bc25f88` is version 1.6.2.
Its [diff from the previously reviewed frontend](https://github.com/alireza0/s-ui-frontend/commit/ab6ed5148c02d311b19e1fe17e85e0e23bc25f88)
only updates package version metadata. No additional protocol or UI fields were introduced.
At review time the local `s-ui-main/config/version` still said 1.6.1, and its setting.go
lacked the fix. The official fix and [release commit](https://github.com/alireza0/s-ui/commit/79df629)
were checked to cover that difference; the reference backend was not modified.

The app now applies the 27 operator-writable setting keys from defaultValueMap (excluding
the five protected keys) to GET settings, settings POST payloads, and wrapped save responses.
This also protects older cached drafts and direct API callers on an affected 1.6.1 panel.
Values retain their string representation, including JSON/YAML subscription extensions;
missing keys are not filled with defaults. Other API objects retain unknown protocol fields.
Save review and interrupted-save evidence use the same filtered payload as the HTTP request.
The settings key list must be reviewed when a later panel release adds writable settings.

A local HTTP regression fixture reproduces the faulty GET and rejecting POST, checks one
APIv2-only write succeeds after filtering, and verifies subscription strings and source
objects are preserved. This fixture does not write to the connected panel. Live settings
saves continue to require the owner's permission.
