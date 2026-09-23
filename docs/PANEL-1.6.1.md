# s-ui 1.6.1 compatibility audit

Reviewed the supplied `s-ui-main` (config/version 1.6.1) and frontend
`s-ui-frontend-3693274a02adab71d4df48e8d87eb190f6f8900a` against the Android app.
The [official release](https://github.com/alireza0/s-ui/releases/tag/v1.6.1) identifies
the changes; local API handlers, models and Vue forms determine the implementation.

| Change | Android adaptation |
| --- | --- |
| Maintenance | APIv2 POST `maintenance` with form `enable=true/false`; confirmation, GET status verification, capability detection, dashboard state and suppression of intentional-stop alerts. |
| Endpoint TLS | OpenConnect/OpenVPN now edit their own inline `tls` objects. Remove the obsolete `tls_id` picker and its shared-template projection checks. Preserve unrelated extension fields. |
| OpenConnect TLS | CA text/path, optional client certificate/key/password, peer fingerprints, expected name and certificate verification controls. |
| OpenVPN TLS | Server certificate/key or client CA, conditional client-auth CA/mutual authentication, control-wrap modes/keys/direction, fingerprints, CRL, version range and certificate profile. |
| OpenVPN mode | CBC cipher only for static mode; negotiated data cipher list/fallback in TLS mode. Direction is a `server`/`client` string. Static payloads omit TLS; mode changes clear incompatible cipher fields. |
| OpenVPN key generation | GET `keypairs?k=openvpn`, parse OpenVPN markers and keep the material in the draft. Self-signed server pairs use `k=tls`. The static-key generator is not offered as a tls-crypt-v2 generator. |
| TLS spoofing | Conditional host/method controls in outbound TLS and TLS-template client settings, with method removed on empty host and both fields removed when REALITY is selected. |
| Protected settings | Strip secret/config/version/globalResetLast/maintenance from settings saves. Maintenance has its own action; core config retains its own save object. |
| Token validation fix | Existing invalid-or-expired guidance remains correct; the server still uses the same rejection message. |
| Proxy/login fixes | Token APIv2 transport needs no cookie-login or forwarded-header workaround. |
| Backup completeness | Continue streaming the database bytes provided by APIv2 without transformation. |
| Subscription generation fixes | The panel remains responsible for generating subscription content. Public subscription URI remains the URL override; no separate port-removal action. |

Maintenance controls appear only when `status.sbd.maintenance` is a Boolean. Older panels
remain readable; new protocol fields require a matching core. Existing legacy endpoint
template references are highlighted rather than guessed into incompatible TLS names.
Certificate paths refer to the VPS filesystem, not Android. Opening an endpoint only
changes its local draft, including any option placeholders; empty TLS values are pruned
from the submitted copy in the same way as the new frontend.

Also fixed immediate state feedback for screenshot/Recents protection, save safeguards and
wallpaper-color switches. Accent selection updates the wallpaper switch in the same visit.
The existing optional local features (PIN, offline overview, VPS quotas/reminders) are
included in this build. Device verification and final test results are in VERIFICATION.md.
