# G04-1 — Android Secret Storage, Backup/Export Exclusion, and API 17 Fallback

Status: **Settled (Worker, M04-C0)**. Date: 2026-09-12.

Evidence scope: local Android SDK inspection and repository facts (verified below); live web research was unavailable from this workstation (see Limitations).

## Question

Per the M03–M06 plan gate G04-1: define the exact secret behavior — storage, backup/export exclusion, reset/delete, migration, and the API 17 fallback — before any M04 production network code exists.

## Repository facts (verified locally)

| Fact | Value | Source |
|---|---|---|
| Minimum SDK | 17 | `SharedModules/constants.gradle` (`minSdkVersion = 17`) |
| Target SDK | 27 | `SharedModules/constants.gradle` (`targetSdkVersion = 27`) |
| Compile SDK | 34 | `SharedModules/constants.gradle` |
| Backup | `android:allowBackup="true"`; no `fullBackupContent` and no `dataExtractionRules` anywhere in the repository | `smarttubetv/src/main/AndroidManifest.xml` line 50 |
| Persistence base | `SharedPreferencesBase` pattern (named, hidden preference stores) | `SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/prefs/SharedPreferencesBase.java` |

## Android platform facts (verified locally against the SDK API database)

Command run 2026-09-12:

```text
grep -o '<class name="..."[^>]*>' $ANDROID_HOME/platforms/android-34/data/api-versions.xml
```

| API | `since` | Consequence |
|---|---|---|
| `android.security.keystore.KeyGenParameterSpec` | **23** | Keystore-backed AES-GCM key generation is unavailable below API 23. |
| `android.security.keystore.KeyProperties` | **23** | Same floor for the associated key-material properties. |
| `android.app.backup.BackupManager` | 8 | Legacy backup hooks predate Auto Backup. |
| `android.app.backup.BackupAgentHelper` | 8 | Same. |

Because the app floor is **API 17**, the feature must run **without** Keystore-backed AES on API 17–22.

## Policy (accepted — ADR-012)

1. **Separation.** Secrets live in a dedicated `SecretStore`, never inside serialized or exportable Provider Profile JSON. The profile payload always stays credential-free.
2. **API 23+ path.** Generate a non-exportable AES-256-GCM key in AndroidKeyStore under a fixed, versioned alias; persist only ciphertext in the feature-owned private store. (`KeyGenParameterSpec` requires API 23, exactly matching the guard above.)
3. **API 17–22 fallback (documented).** No Keystore AES: the secret is stored in the app-private preferences area, protected by the app sandbox alone. This is an explicit, documented compatibility exception — not a silent downgrade — and the app never claims encrypted storage on that band.
4. **Backup/export.** No host-manifest change is authorized by the M04 upstream budget, and none is required:
   - On API 23+ Auto Backup the ciphertext may be captured, but the Keystore key does not travel with a backup, so the blob is unusable on another device; reads fail and normalize to a configuration/auth failure.
   - API 17–22 has no Auto Backup; `adb backup` requires explicit, user-initiated USB-debugging action. The plaintext fallback is the recorded exception for that legacy band.
5. **Key material loss, invalidation, reset, delete, migration.** Any read or decryption failure yields a normalized auth/configuration failure and Source-Only Fallback — never a playback failure. Deleting or resetting a profile explicitly clears its stored secret.
6. **Display and diagnostics.** UI and logs show masked keys only; exceptions and request diagnostics never contain the full secret, and no secret may appear in Markdown, artifacts, or logs.

## Limitations

Web research was unavailable from this workstation during M04-C0 (the search provider rejected the configured API key). The Keystore/Auto-Backup statements rest on the locally verified SDK API-database facts above plus Android's documented platform behavior; the statement “Keystore key material does not migrate through backup” is platform behavior and must be re-confirmed against developer.android.com when network access is restored. No M04 production code depends on the unverified part: the guard is API-driven and fails safe.
