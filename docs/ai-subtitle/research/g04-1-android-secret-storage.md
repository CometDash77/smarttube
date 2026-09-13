# G04-1 — Android Secret Storage, Backup/Export Exclusion, and API 17 Fallback

Status: **RESEARCH COMPLETE; ADR-012 REOPENED (2026-09-13)**. Current Android primary sources were reachable. They verify the API-level floor and non-exportability claims, but they do not support the previous conclusion that backing up Keystore-encrypted ciphertext needs no exclusion rule.

## Question

Per the M03–M06 plan gate G04-1: establish the Android platform facts needed to decide secret storage, backup/export exclusion, restore failure, and the API 17 fallback.

## Verified repository facts

| Fact | Value | Repository source |
|---|---|---|
| SDK levels | minimum 17; target 27; compile 34 | `SharedModules/constants.gradle` |
| Host backup configuration | `android:allowBackup="true"`; no `android:fullBackupContent` or `android:dataExtractionRules` rule is present | `smarttubetv/src/main/AndroidManifest.xml` |
| Current secret persistence | `AndroidSecretStore.PreferencesStorage` writes both API 23+ ciphertext and the API 17–22 plaintext fallback to a named `SharedPreferences` file | `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/ai/subtitle/settings/AndroidSecretStore.java` |

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
3. Do **not** accept the previous “no backup exclusion is required” recommendation. Before ADR-012 can return to Accepted, an authorized design must exclude both the API 23+ ciphertext and API 17–22 plaintext secret records from backup/export. Android documents backup-rule exclusion and no-backup storage as platform mechanisms, but choosing or implementing one is outside this research-only correction.
4. Preserve fail-safe handling for missing or invalidated key material after restore; the user must be able to re-enter the credential without affecting playback.

No new storage design is approved by this note, and no production code or manifest is changed.

## Remaining decision blocker

- Status: **BLOCKED (design/implementation, not source retrieval)**
- Owner: M04 Commander/design owner to authorize the exclusion mechanism, followed by the M04 correction Worker for implementation and tests.
- Trigger: before ADR-012 is marked Accepted again, before M04-C7 is re-reviewed as complete, and before M05-C0 starts.
- Exact reason: the current named `SharedPreferences` secret store is in Auto Backup's default include set, while Android's official encrypted-preferences guidance says such encrypted preferences should be excluded; the API 17–22 plaintext fallback also has no demonstrated backup/export exclusion. This research-only task is explicitly unauthorized to change production code or the host manifest.

## Source access record

| URL | Access result on 2026-09-13 |
|---|---|
| <https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec> | PASS — official API page retrieved; “Added in API level 23” present. |
| <https://developer.android.com/privacy-and-security/keystore> | PASS — official security page retrieved; API 18 provider history and non-exportable/extraction statements present. |
| <https://source.android.com/docs/security/features/keystore#android_60> | PASS — official AOSP history retrieved; Android 6.0 symmetric AES/HMAC addition present. |
| <https://developer.android.com/identity/data/autobackup> | PASS — official Auto Backup page retrieved; API 23 applicability, default `SharedPreferences` inclusion, exclusions, and backup rules present. |
| <https://developer.android.com/privacy-and-security/risks/backup-best-practices> | PASS — official backup security recommendations retrieved. |
| <https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences> | PASS — official AndroidX reference retrieved; restore/key-loss warning and exclusion direction present. |
