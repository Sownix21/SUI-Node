# Local security model

Saved panel profiles, API tokens, monitoring configuration/history and certificate trust
records use an AES-256-GCM vault with fresh random IVs and authenticated version headers.
The encryption key is non-exportable Android Keystore material. StrongBox is preferred
when supported, with Android Keystore fallback; the settings screen reports the actual
hardware/software protection level. Hardware-backed protection is not promised on every device.

The vault is in `noBackupFilesDir`; Android backup/transfer exclusions remain in place.
Migration verifies a durable encrypted write by decrypting it before deleting legacy
preferences. Corruption, missing keys and migration errors fail closed without resetting
profiles or silently creating an empty vault. Do not uninstall or clear app data to fix
a vault problem: doing so can permanently remove its encryption key and saved profiles.

App lock gates the UI and does not decrypt the vault before authentication. Immediate
background locking remains the default. Optional 1/5/15-minute grace periods deliberately
retain the unlocked session briefly; screen-off-only retains it until the screen turns off.
All policies lock on screen-off and clear active sessions when the lock takes effect.
Elapsed realtime, not the wall clock, controls grace deadlines; a fresh process starts locked
when app lock is enabled. It is not a per-operation, biometric-bound encryption key.
Explicitly enabled background monitoring can read the vault while the UI is locked.
Device-unlocked key restrictions are used on Android 15+, avoiding documented key-loss
problems on earlier Android versions. Background checks can consequently be delayed
until the phone is unlocked.

The optional screenshot/Recents protection setting (enabled by default) uses FLAG_SECURE
and, on Android 13+, the Recents screenshot policy. Turning it off deliberately permits
ordinary captures. It is not protection against root, compromised firmware or an instrumented
debuggable process. This debug APK is for testing, not a production-security certification.
Release distribution requires the owner's signing key and a non-debuggable release build.

## Optional app PIN and local summaries

An app-specific PIN of 8–32 ASCII digits can unlock alongside Android biometric/device
authentication. It is not the Android screen-lock PIN. A fresh 128-bit salt and
PBKDF2-HMAC-SHA256 with 600,000 iterations produce a 256-bit verifier; comparisons are
constant-time. The verifier and persistent failure counter live in a separate encrypted
Keystore vault, not plaintext preferences. Cryptographic work runs off the UI thread.
After five failed attempts, increasing delays start at 30 seconds and cap at 30 minutes.
The delay uses the device wall clock; clock manipulation, root or a modified debug process
is outside this protection. Setting a PIN enables app lock. Changing/removing an existing
PIN requires it; Android authentication remains an alternative unlock route, not a PIN
reset mechanism. A short numeric PIN is not equivalent to a high-entropy password.

Panel memos, provider/group labels, VPS quotas and billing notes use the profile vault.
Offline overview caching is disabled per panel by default. When opted in, it stores a
timestamp, counts and bounded client/inbound summaries, without tokens, subscription URLs
or full configurations. Client names and usage remain sensitive and are encrypted. It is
read-only and explicitly stale, never a source for submitting edits. Disabling the option
removes the cached overview. Pending-save evidence is encrypted too; configuration values
are hashed and the review summary masks secrets. No diagnostic-export feature was added.

## Transport

Only APIv2 is used. Normally verified HTTPS checks both the platform certificate chain
and hostname. Redirects are disabled to avoid forwarding authentication elsewhere.
After TLS connects, a network interceptor checks the leaf certificate SHA-256 identity
before sending the authenticated HTTP request. The first normally verified certificate
establishes a local baseline. Subsequent changes require explicit fingerprint review.
Normal certificate renewal can cause this warning; verify the fingerprint through a
separate trusted channel before accepting it. Acceptance does not bypass platform TLS
validation. Existing explicit insecure-TLS profiles require review even on first contact.

HTTP profiles still send tokens without encryption. Explicit insecure TLS still disables
normal chain/hostname verification. These compatibility choices are prominently warned
about and cannot offer the protection of normally verified HTTPS. Prefer fixing the
server certificate rather than opting out of verification.

No HTTP logging interceptor is installed. Profile `toString()` is redacted and connection
errors are classified without echoing URLs or credentials. Credentials necessarily exist
in memory while requests are constructed; no Android app can promise absolute protection
on a compromised or rooted device.

## User exports

Document contracts belong to the activity, not an authenticated screen. A selected backup
destination can wait while the UI is locked; export only begins once authentication and
vault loading complete. Pending metadata contains a profile ID, option names and document
URI, not the token or database bytes. Backup selection metadata can survive recreation.
Restores are not replayed after recreation, and requests are claimed before transfer starts.
An already-authorized transfer may finish while the UI locks; activity destruction cancels it.
An interrupted export can leave a partial destination; verify or remove it before relying on it.
An interrupted restore requires checking panel state, not automatically submitting again.

Panel database backups remain the original SQLite bytes returned by APIv2, deliberately
unencrypted as requested. They may contain sensitive panel credentials. The user controls
their destination and subsequent handling; vault encryption does not protect exported files.

## Primary references

- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography)
- [Unlocked-device key restrictions](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setUnlockedDeviceRequired(boolean))
- [OkHttp interceptor order](https://github.com/square/okhttp/blob/parent-4.10.0/okhttp/src/main/kotlin/okhttp3/internal/connection/RealCall.kt)
- [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
