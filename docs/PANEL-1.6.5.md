# s-ui 1.6.5 compatibility review

Reviewed October 16, 2026 against the supplied backend (`config/version`: 1.6.5;
`go.mod`: Go 1.27.2, sing-box 1.14.3) and frontend
`abd7f342f6f6dcb626bcf70a25b092f4727a4f2d` (`package.json`: 1.6.5).

Compared the [backend release diff](https://github.com/alireza0/s-ui/compare/v1.6.4...v1.6.5)
and [frontend snapshot diff](https://github.com/alireza0/s-ui-frontend/compare/e4525297b002c1c3be234cc1c9695ef84be741a0...abd7f342f6f6dcb626bcf70a25b092f4727a4f2d)
with the supplied local handlers, settings, subscription code, core probe and tests.

## API and form mapping

| Upstream change | Android behavior |
| --- | --- |
| `service/setting.go`: `subCustomHeaders` | Allowlist grows from 27 to 28 settings. GET retains the string; save sends it as a string. The field is shown only when present in GET, so older panels do not get an invented key. |
| `Settings.vue`: subscription-header textarea | Conditional multiline field in the Subscription tab, retaining comments, URL colons and line breaks; an empty string clears it. |
| `sub/sub.go`: response headers and gzip middleware | Explain automatic gzip and public header visibility; do not add a management-request header or compression switch. Headers are read during subscription-router initialization, so changes need an explicit panel restart. |
| `UrlTestTarget.vue` / `useUrlTestTarget.ts` | Toggle reveals URL input. HTTP/HTTPS validation, default target when blank/off, shared per-panel session state. Unlike frontend localStorage, private target URLs are not persisted. Invalid nonempty input blocks a test instead of silently using the default. |
| `Outbounds.vue` / `Endpoints.vue` | Both individual and batch tests use `GET checkOutbound?tag=…&link=…`, with proper query encoding and no direct phone-to-target probe. A batch captures one target. Read-only mode blocks probes. |
| `core/outbound_check.go` | Parse uppercase `OK`, `Delay`, `Error`. Errors or missing delay do not become zero-millisecond successes. Backend core owns timeout, outbound lookup and probe implementation. |

The backend ignores blank/comment/malformed header lines, splits each valid line at
the first colon, and validates HTTP header names/values. Later duplicates overwrite
earlier values; built-in subscription headers may override matching custom headers.
Android preserves the original string rather than introducing a different parser.
No restart is automatic; saving and restarting remain separate user actions.

## Backend-only fixes retained

- `sub/clashService.go`: UDP defaults apply to all generated proxies and null
  `skip-cert-verify` is omitted. Existing `subClashUdp` controls remain string-valued;
  the panel remains authoritative for generated subscription contents.
- `service/client.go`: periodic resets record client changes and refresh status.
  Android already requests full snapshots without a phone-clock cursor; regression
  fixtures verify re-enabled clients and zeroed counters replace old state.
- `util/base64.go` / `util/linkToJson.go`: unpadded/URL-safe base64 and legacy
  Shadowsocks handling remain in the panel converter. Android forwards opaque links
  through form encoding without local base64 rewriting.
- New APIv2 E2E tests confirm token authentication and `{success,msg,obj}` envelopes,
  including HTTP-200 failures. Android retains these checks; no web-login fallback.
- Go/core/frontend dependencies and core hot-reload internals belong to the server;
  they are not Android dependencies or new protocol-editor switches.

APIv2 remains **19 GET / 10 POST** actions. Protocol selector registries remain
**19 inbound / 19 outbound / 13 DNS types**. No new protocol form was introduced.

## Verification boundary

The Android HTTP fixtures run against a local test server, not a deployed panel or
sing-box instance. The upstream E2E tests were reviewed, not executed by this Android
update. Subscription header/gzip responses, live imports and protocol connectivity
still need disposable-panel acceptance. No live panel was modified or probed.
See [VERIFICATION](VERIFICATION.md) for build/test evidence and remaining checks.
