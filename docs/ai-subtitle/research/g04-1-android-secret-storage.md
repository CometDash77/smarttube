# G04-1 — Android Secret Storage, Backup/Export Exclusion, and API 17 Fallback

Status: **IMPLEMENTED (2026-09-13)**. Current Android primary sources verify the API-level floor and non-exportability claims. The feature-owned secret file is now excluded from Auto Backup on API 21+ by using the no-backup directory; API 17–20 deliberately use cache storage and require credential re-entry after cache cleanup.

## Question

Per the M03–M06 plan gate G04-1: establish the Android platform facts needed to decide secret storage, backup/export exclusion, restore failure, and the API 17 fallback.

## Verified repository facts

| Fact | Value | Repository source |
|---|---|---|
| SDK levels | minimum 17; target 27; compile 34 | `SharedModules/constants.gradle` |
| Host backup configuration | `android:allowBackup="true"`; no `android:fullBackupContent` or `android:dataExtractionRules` rule is present | `smarttubetv/src/main/AndroidManifest.xml` |
| Current secret persistence | `AndroidSecretStore.FileStorage` writes one feature-owned file per secret reference: API 21+ in `getNoBackupFilesDir()` and API 17–20 in `getCacheDir()` | `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStore.java` |

## Verified Android platform facts

Every platform statement below is tied directly to an Android-owned source retrieved on 2026-09-13.

| Technical fact | Primary-source evidence | Disposition |
|---|---|---|
| `KeyGenParameterSpec` was added in API level 23. | [Android `KeyGenParameterSpec` API reference](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec) | VERIFIED |
| The Android Keystore provider itself dates to API 18, but Android 6.0 added symmetric AES/HMAC primitives; therefore the implemented `KeyGenParameterSpec` AES-GCM path has an API 23 floor. | [Android Keystore system](https://developer.android.com/privacy-and-security/keystore), [AOSP Keystore history](https://source.android.com/docs/security/features/keystore#android_60) | VERIFIED |
| Android Keystore key material remains non-exportable and does not enter the application process during cryptographic operations. This is an extraction property, not a statement that an arbitrary ciphertext file is safe to restore. | [Android Keystore system](https://developer.android.com/privacy-and-security/keystore#security-features) | VERIFIED |
| Auto Backup applies to apps that target and run on Android 6.0/API 23 or later. By default it includes `SharedPreferences`; `getNoBackupFilesDir()` is excluded, and backup rules can exclude selected files. | [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup#Files) | VERIFIED |
| Android's security guidance recommends excluding particularly sensitive data from backup, or requiring end-to-end encryption when exclusion is not possible. | [Security recommendations for backups](https://developer.android.com/privacy-and-security/risks/backup-best-practices) | VERIFIED |
| AndroidX's official encrypted-preferences reference warns that an encrypted preference file should not be backed up because, after restore, the encryption key is likely no longer present; it directs apps to exclude that file with backup rules. This is the closest official statement for the same “Keystore key plus backed-up ciphertext” arrangement used here. | [AndroidX `EncryptedSharedPreferences` API reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) | VERIFIED |

## Evidence-bounded interpretation

- The sources support **non-exportable key material**. They do not support the former absolute wording that “the Keystore key does not travel with a backup” under every cloud, device-to-device, OEM, and Android-version restore path.
- The official AndroidX guidance says the restored key is **likely** absent. The research note therefore must not upgrade that wording into a universal platform guarantee.
- Because the current store is a `SharedPreferences` file and the host has no exclusion rule, its contents are in Auto Backup's default include set on applicable devices. On API 23+ those contents are ciphertext; on API 17–22 the current fallback contents are plaintext. “API 17–22 has no Auto Backup” does not by itself prove exclusion from every legacy backup/export mechanism.

## Recommendations and existing policy

The following are project recommendations or already implemented behavior, not Android platform facts:

1. Keep Provider Profile serialization credential-free and keep masking/redaction, delete/reset cleanup, normalized auth/configuration failure, and Source-Only Fallback behavior.
2. Keep the API 23 guard around the current `KeyGenParameterSpec` AES-GCM path and continue describing API 17–22 as an app-sandbox-only plaintext compatibility exception.
3. API 21+ stores the feature-owned secret files in `getNoBackupFilesDir()`, Android's documented backup-excluded location. API 17–20 stores them in `getCacheDir()` as an intentional availability tradeoff: cache cleanup removes the plaintext compatibility credential and the user must re-enter it. This avoids retaining the fallback in the legacy preferences file, but does not make a universal export-exclusion claim for legacy devices.
4. Preserve fail-safe handling for missing or invalidated key material after restore; the user must be able to re-enter the credential without affecting playback.

For each reference, the implementation reads the new file first and migrates a legacy `SharedPreferences` value only when needed. It copies the encoded record without decrypting it, then removes the old record only after the file write succeeds. Put and delete also remove any legacy record for that reference.

## Remaining decision blocker

- Status: **RESIDUAL RISK RECORDED**
- Owner: feature maintainer.
- Trigger: any change to the minimum SDK, backup policy, or secret-storage implementation.
- Exact reason: API 17–20 lacks `getNoBackupFilesDir()`, so its plaintext compatibility fallback is stored in cache and is intentionally lost during cache cleanup. This is safer than persistent legacy preferences, but cache storage alone is not a universal statement about every device-specific legacy export mechanism.

## Source access record

| URL | Access result on 2026-09-13 |
|---|---|
| <https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec> | PASS — official API page retrieved; “Added in API level 23” present. |
| <https://developer.android.com/privacy-and-security/keystore> | PASS — official security page retrieved; API 18 provider history and non-exportable/extraction statements present. |
| <https://source.android.com/docs/security/features/keystore#android_60> | PASS — official AOSP history retrieved; Android 6.0 symmetric AES/HMAC addition present. |
| <https://developer.android.com/identity/data/autobackup> | PASS — official Auto Backup page retrieved; API 23 applicability, default `SharedPreferences` inclusion, exclusions, and backup rules present. |
| <https://developer.android.com/privacy-and-security/risks/backup-best-practices> | PASS — official backup security recommendations retrieved. |
| <https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences> | PASS — official AndroidX reference retrieved; restore/key-loss warning and exclusion direction present. |
