# Maintainer release guide

Open `s-ui-node` as the project root. Backend/frontend reference folders are not needed
to build this Android app. Use JDK 21, Android SDK Platform 36.1 and the checked-in
Gradle wrapper. Configure the SDK in Android Studio or untracked `local.properties`.

Build the release with `./gradlew assembleRelease bundleRelease` (Windows: `gradlew.bat`).
Initial dependency downloads need internet access. CI retains regression tests and lint.

Application ID: `com.sonix21.suinode`. Version: **2.4.3 (9)**. Release builds enable
code minification and resource shrinking. Production signing material is not included.

## Signing

Use Android Studio's Generate Signed App Bundle / APK action with your existing key,
or configure all four private environment variables/Gradle properties:

- `SUI_RELEASE_STORE_FILE`
- `SUI_RELEASE_STORE_PASSWORD`
- `SUI_RELEASE_KEY_ALIAS`
- `SUI_RELEASE_KEY_PASSWORD`

Do not put credentials in this repository. `.env.example` documents names; Gradle does
not automatically load `.env`. Without signing settings, release outputs are unsigned.
APK output is under `app/build/outputs/apk/release/`; AAB output is under
`app/build/outputs/bundle/release/`.

Updates require the same signing identity and a higher version code. Never uninstall
an existing installation just to resolve a signature mismatch: it can destroy saved
profiles. Verify the final APK with `apksigner verify --verbose`, keep the release
mapping file privately, and complete [the release checklist](RELEASE-CHECKLIST.md).

## Source publication

Publish this app directory, not its parent workspace. Preserve GPLv3 and attribution
files. Run `powershell -File tools/export-source.ps1` for a source-only ZIP under
`build/`. Build output, IDE state, local SDK settings, keys and panel backups are excluded.
Review the contents before upload; filename filtering is not a secret scanner.

Regression tests remain in `app/src/test`. The read-only contract checker is
`python tools/check_panel_contract.py ../s-ui-main --frontend <frontend-path>`.
Its registry checks do not replace live protocol acceptance. Hosted CI must pass after
upload; a local build does not verify GitHub's runner.
