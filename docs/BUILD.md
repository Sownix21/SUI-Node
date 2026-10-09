# Developer build and release guide

These instructions are for maintainers, not app users.

## Build from source

Open **this directory** (`s-ui-node`) in Android Studio. The sibling backend/frontend
folders are reference material and are not required to build the Android app.

Requirements:

- A JDK supported by Gradle 9.3.1 and Android Gradle Plugin 9.1.1; JDK 21 is used in CI.
- Android SDK Platform **36.1** and Android build tools; target SDK is 36, minimum SDK is 26.
- Android Studio with support for this toolchain, or the command-line SDK tools.
- Internet access for the initial Gradle/dependency download.
- Optional Python 3 for local fixtures and the source-contract checker.

The GitHub workflow explicitly initializes SDK command-line tools, then installs
`platform-tools`, `platforms;android-36.1` and `build-tools;36.0.0` as separate quoted
arguments. Do not pass a literal multiline package string or request the obsolete `tools`
package. SDK license acceptance is handled by the setup action.

Configure the SDK through Android Studio, `ANDROID_HOME`, or your untracked
`local.properties`. Use the checked-in wrapper rather than a separately installed Gradle.

Windows PowerShell:

~~~powershell
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease --max-workers=2
~~~

macOS/Linux:

~~~sh
bash gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease --max-workers=2
~~~

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Debug signing uses Android's automatically generated debug key; no project-local
`debug.keystore` is required.

The application ID and source namespace are `com.sonix21.suinode`.
The visible name is **S-UI Node**. Version 2.4.2 uses version code 8.
This identity installs separately from previous differently identified builds; their
encrypted profiles cannot be transferred automatically. Keep that installation until
you have securely re-entered and verified your panel profiles in S-UI Node.

## Release signing

Release builds enable code minification and resource shrinking. Production keys are not
included. Choose one of these workflows:

1. **Android Studio:** Build → Generate Signed App Bundle / APK, select the release variant
   and your securely stored signing key.
2. **Gradle:** provide all four values below as environment variables or Gradle properties
   in your private user-level Gradle configuration, outside this checkout.

| Setting | Value |
| --- | --- |
| `SUI_RELEASE_STORE_FILE` | Absolute path to your production keystore |
| `SUI_RELEASE_STORE_PASSWORD` | Keystore password |
| `SUI_RELEASE_KEY_ALIAS` | Existing signing-key alias |
| `SUI_RELEASE_KEY_PASSWORD` | Key password |

Run `assembleRelease` for APK or `bundleRelease` for AAB. Gradle properties take precedence
over environment variables. The `.env.example` file is documentation; Gradle does not load
.env files automatically. Partial signing configuration fails with a clear error.

With no signing values, the APK is
`app/build/outputs/apk/release/app-release-unsigned.apk`; **it cannot be installed as-is**.
A configured signed APK is normally `app-release.apk` in the same directory.
The bundle is `app/build/outputs/bundle/release/app-release.aab`; without credentials it
is unsigned too.

Never commit signing keys, passwords, or put real passwords in shared shell examples.
Keep a secure backup of your production key. Future updates need the same signing identity
and a higher version code. A production-signed APK cannot update a debug-signed installation;
do not casually uninstall an existing installation containing your only saved profiles.

Before publishing, verify the release signature with Android's `apksigner verify --verbose`,
retain the matching `app/build/outputs/mapping/release/mapping.txt` privately for crash
deobfuscation, and complete [the release checklist](RELEASE-CHECKLIST.md).
Distribute corresponding source with GPL-covered binaries as required by the license.

## Tests and development

The JVM tests use local HTTP/HTTPS fixtures and synthetic credentials; no live panel is
required. They cover APIv2 envelopes/routes, complete-record retrieval, settings round trips,
TLS identity checks, encrypted storage, quotas/reminders, save evidence and protocol helpers.

For an Android emulator fixture:

~~~sh
python tools/apiv2_fixture.py
~~~

Connect the emulator to `http://10.0.2.2:18995/app/` with `fixture-token`.
The server binds only to the host loopback interface and stores changes in memory.
Restarting it resets data. It is **not** a sing-box emulator or a production server.
`tools/emulator_ui.py` is restricted to emulator-5554; never use real credentials with it.

When reviewing an updated backend:

~~~sh
python tools/check_panel_contract.py ../s-ui-main
~~~

Pass `--frontend <path-to-frontend>` to also compare inbound, outbound and DNS type selectors.
This read-only check detects drift in APIv2 GET/POST registries and writable setting keys.
It intentionally does not claim form or protocol runtime parity.

The GitHub Actions workflow runs tests, lint and unsigned builds without production
credentials. Its hosted run must pass after upload; local success is not a hosted CI result.

## Repository layout

~~~text
app/src/main/       Android code, Compose screens and resources
app/src/test/       JVM regression and HTTP/HTTPS contract tests
gradle/             Version catalog and Gradle wrapper
design/             Original SONIX Switchline vector artwork
docs/               Security, monitoring, compatibility and verification notes
tools/              Local fixtures, contract check and source export
.github/workflows/  Unsigned build verification
~~~

### Prepare for GitHub

Use **the app directory as the repository root**, not the parent folder containing all
three projects. Keep the supplied backend/frontend snapshots local or publish them
separately with their own notices.

`.gitignore` excludes build output, IDE state, local SDK configuration, keystores, backups
and environment secrets. It does not remove files already committed to Git history.
Review staged files and enable GitHub secret scanning/private vulnerability reporting
where available. Never publish live-device screenshots or UI dumps.

For a source-only ZIP, run:

~~~powershell
powershell -File tools/export-source.ps1
~~~

The archive is written under `build/` from an explicit set of source folders/files.
It omits caches, APKs, local configuration and signing material. Inspect it before uploading;
filename exclusions cannot detect secrets pasted into source code.
