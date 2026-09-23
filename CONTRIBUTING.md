# Contributing to S-UI Node

Changes to this GPL-3.0-only project should preserve SONIX and upstream notices.
Do not add a dependency or copy upstream code without retaining its required notices
and checking license compatibility.

## Development

1. Build with the checked-in Gradle wrapper; see the [developer guide](docs/BUILD.md).
2. Keep changes focused. Add regression tests for bugs and wire-format changes.
3. Run `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`.
4. Exercise changed forms against the local fixture, then an explicitly disposable panel.
5. Document the exact backend/frontend revision used for compatibility changes.

APIv2 is the sole remote interface. A web-panel control does not prove an operation is
exposed to APIv2. Check backend handlers, service/model code and frontend visibility rules.
Fetch full records before editing; preserve unrelated/unknown configuration fields.
Never silently retry a write after an uncertain response, or turn missing status into zero.
Settings have a deliberate writable-key allowlist; update it and tests when upstream changes.

## Safe testing

Do not run save, delete, renewal, restore, reset, restart, maintenance or active probes
against someone else's live panel without explicit permission. Fixtures are synthetic;
their success is not proof of sing-box runtime validation. Report what was actually tested.

Never attach real tokens, subscription links, certificate/private keys, database backups,
device UI dumps or unredacted logs to issues/PRs. Use synthetic examples and `.example`
domains. Keep production signing keys outside the checkout. See [SECURITY](SECURITY.md).

For UI changes, check Light, Dark and AMOLED, all accents, small screens, keyboard
visibility, scrolling and English/Persian layouts. Screenshots must use synthetic profiles.
