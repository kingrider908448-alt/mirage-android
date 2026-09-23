# GhostViki for Android

An experimental Android app and Vector / LSPosed module for synthetic identity testing in **selected app processes**, plus the existing framework location test mode. GhostViki Probe is an independent reader, not the only supported target.

## 0.2.0-profiles100

Device Profile now contains a searchable catalog of **100 real India-market models**, with pinned Google Play model/device codes and separately sourced hardware specifications. Choose a model for the displayed target, or rotate all selected profiles. A rotation chooses a different model and prefers a different manufacturer. Selecting a model updates that target's saved name/build identity and refreshes its synthetic identifiers atomically; other targets keep their profiles.

The previously preview-only Device Name now has general Settings and local Bluetooth adapters. SoC, selected Java product-property getters and sourced RAM/storage/display readouts have adapters too. [Catalog and sources](catalog/README.md) · [All 100 profiles](catalog/DEVICES.md).

**This is not a complete firmware or hardware impersonation.** Unsourced specs stay unchanged. The previous random build ID, hardware, product and fingerprint values are cleared on upgrade; they were not genuine factory values. The UI now shows the original firmware/OS values with explicit unchanged labels instead of fixed Tensor/Android/256 GB placeholders. Android version, SDK, ABI, kernel and security patch are preserved. Shared specifications between real models can legitimately remain the same.

The existing System Location update from `3fb3f2b` is retained. Its framework scope is separate from selected-app identity scope. No new identity adapter is installed into System Framework.

The earlier configuration repair is retained:

This revision fixes a reproducible configuration bug: runtime preferences were opened privately before requesting world-readable mode. Android caches the first preferences object and its write mode, so later requests could apparently succeed while writes remained private.

The writer now requests world-readable mode first, falls back privately on rejection, and forces a disk write on startup. Migration preserves existing profiles instead of overwriting them with a stale draft. The reader uses the loaded XSharedPreferences map, without rejecting service-readable data because raw File.canRead() is false.

**[Builds and APK downloads](https://github.com/kingrider908448-alt/mirage-android/actions/workflows/android.yml)**

Choose a successful run for the desired commit and download its GhostViki-debug-* artifact. The ZIP contains GhostViki-alpha.apk, GhostViki-Probe.apk and SHA256SUMS.txt. GitHub may require sign-in; artifacts expire after 14 days. Both apps display **0.2.0-profiles100**, version code 3.

Passing tests, lint or a build is not a verified phone result. The supplied Vector screenshots establish that GhostViki loaded into Probe, not that its configuration or API replacements worked. See [VALIDATION.md](VALIDATION.md).

## General scope and actual coverage

Select the same target in **both Vector and GhostViki**. Each selected package has its own saved profile. General identity adapters are not conditioned on the Probe package name. Probe-specific hooks expose diagnostics only, never fabricated observed values or detector verdicts.

| Surface | Implemented path | Limits |
| --- | --- | --- |
| Android ID | Settings.Secure.getString for ANDROID_ID | Java getter only; no direct provider/Binder, native or previously cached reads. |
| Device name | Settings.Global/System/Secure getString/getStringForUser for device_name; Secure bluetooth_name; local BluetoothAdapter.getName | Target-process reads only. Bluetooth permission errors and unavailable adapter results are preserved. Does not rename the phone in unselected system Settings or change Bluetooth broadcasts/remote-device names. |
| Device build | Build.BRAND, MODEL, MANUFACTURER, DEVICE, SERIAL; sourced SOC_MODEL/SOC_MANUFACTURER | Process-local Java fields at startup; restart targets after changing or disabling. No fabricated stock firmware metadata. |
| Java product properties | Exact ro.product brand/manufacturer/model/device aliases, marketing-name aliases and sourced ro.soc fields | Java SystemProperties.get only. Shell getprop, native reads, boot/security properties and actual system properties remain unchanged. |
| RAM | ActivityManager.getMemoryInfo totalMem and bounded available/threshold fields | Sourced advertised capacity. Does not change physical RAM, native/proc reads or allocation limits. |
| Storage | StorageStatsManager total/free bytes for UUID_DEFAULT | Sourced advertised internal capacity, bounded free value. External volumes and filesystem/File/StatFs reads unchanged. |
| Display | Display getSize/getRealSize/getMetrics/getRealMetrics on default display | Sourced panel pixels, preserves orientation. Does not alter native display, Resources metrics, density, Display.Mode or WindowMetrics. May affect apps that use these getters for layout. |
| Serial API | Readable Build.getSerial | Permission errors, null and UNKNOWN remain unchanged. |
| Telephony | Readable getImei, IMEI-shaped getDeviceId, getSubscriberId, getSimSerialNumber | No permission bypass; slots 0/1 only; no MEID adapter. Synthetic numbers are not provisioned modem/SIM identities. |
| Network | Readable Wi-Fi MAC/BSSID and Bluetooth address getters | BSSID now has a dedicated saved value. Null, denied and standard redacted MAC results stay unchanged. No change to packets, router state or public IP. |
| Location | Existing framework LocationManagerService and delivery adapters | Enable separately in Location and Vector System Framework scope. Framework-wide effect, not tied to the identity target list. Simulated locations retain the mock flag. OEM and fused-provider paths need device validation. |
| Root signals | Existing exact Java SU file/path and selected package-manager adapters | No native syscalls, mounts, kernel or hardware-attestation coverage. |

The UI labels implemented adapters separately from **PREVIEW ONLY** fields. AAID, App Set ID, FID/FCM, GSF, accounts, signatures, DRM, IP, boot IDs and other preview fields have no target adapter in this revision. Preview strings are not service registrations. Rotation does not change fixed labels, accounts, Android version, installed package identity, app data, server history, physical identifiers or attestation. This is not a universal all-identity or undetectable module.

The profile viewer now lets you choose the selected package being displayed; previously it silently showed only the alphabetically first target. The home status describes a local config write, not target success. Probe diagnostics are separate from its baseline comparison:

- PROFILE_READY: selected identity profile loaded and validated; compare actual API reads to verify effects.
- NOT_SELECTED: package is missing from GhostViki's saved targets.
- IDENTITY_DISABLED: replacements switched off.
- INVALID_PROFILE: malformed/missing Android ID/serial, or an inconsistent schema-5 catalog name/model/code combination.
- CONFIG_UNAVAILABLE / CONFIG_READ_ERROR: settings absent, unsupported or unreadable.
- MODULE_NOT_LOADED: Probe's diagnostic method was not replaced. An older module may also lack that adapter; confirm matching versions.

## Focused device test

1. Install matching APKs. Enable GhostViki in Vector and scope it to a test app such as DevInfo and optionally Probe. System Framework scope is only needed for the separate Location feature. Restart if Vector requests it.
2. If the older Mirage module is scoped to the same target, temporarily disable it for that target to isolate the test. Prior logs showed both module packages: a possible conflict, not a proven cause.
3. Force-stop and reopen **GhostViki itself once after updating**, so the old process cannot keep a private-mode preferences object cached. Confirm v0.2.0-profiles100.
4. Select the same target inside GhostViki. In Device Profile choose that exact package with **View profile**, then **Choose device model**. Note the saved name and model. Android ID is available in Android / Device Identifiers.
5. Force-stop and reopen the target. Compare **Model, Device name and Android ID** against that package's saved values. A serial permission error is not evidence that Android ID failed. Build fields require a fresh process. Probe now independently reads Settings device name, local Bluetooth name (with permission), SoC, RAM, storage and display. New fields absent from an old baseline never count as a successful identity change.
6. Rotate again, restart and repeat. Confirm an unselected app remains unchanged. To test disabling, switch off Identity Adapters and restart the target.

Windows **CMD**, one command per line (not PowerShell Start-Sleep):

~~~bat
adb shell am force-stop dev.ghostviki.app
adb shell monkey -p dev.ghostviki.app 1
~~~

After selecting targets and saving, test DevInfo independently of Probe:

~~~bat
adb shell am force-stop com.liuzh.deviceinfo
adb shell monkey -p com.liuzh.deviceinfo 1
~~~

If unchanged, report the exact field, both app versions and the latest **Vector module log** containing GhostViki: config for <package>. Ordinary adb logcat may not include framework logs; a missing logcat line alone does not prove failed injection. No data clearing, factory reset or root chmod workaround is needed.

## Build and checks

Use JDK 17+, Gradle 8.13 and SDK platform 36. CI installs these and runs:

~~~sh
java scripts/RunConfigChecks.java
gradle --no-daemon :core:check :app:lintDebug :probe:lintDebug :app:assembleDebug :probe:assembleDebug
~~~

The first command runs production core/config code and the new name/hardware callbacks against explicit **JVM fixtures**. It checks 100 unique models, no-repeat rotations, persistence, migration, per-target isolation, getter results, error preservation and disabled/unselected targets, and syntax-parses all Android Java sources. It does not emulate Android, SELinux, real Vector IPC or hook installation. Fixtures live outside Android source sets. CI also checks that the exact tested catalog is packaged inside the module APK.

Fresh CI runners can use different debug signing keys. If an update-signature error occurs, do not automatically uninstall or clear data: that erases profiles/baselines. A stable signing workflow is needed for routine updates. No private key belongs in this repository.

## Privacy and references

GhostViki has no Internet permission or telemetry, executes no root shell commands and never clears other apps' data. Cross-process preferences contain synthetic profiles and test coordinates, not encrypted secrets; do not put credentials there.

- [Vector](https://github.com/JingMatrix/Vector)
- [Vector XSharedPreferences source](https://github.com/JingMatrix/Vector/blob/efb82883071643ca16128ecd588be7c40c1e45e6/legacy/src/main/java/de/robv/android/xposed/XSharedPreferences.java)
- [AOSP ContextImpl preferences cache](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/ContextImpl.java)
- [AOSP SharedPreferencesImpl write mode](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/SharedPreferencesImpl.java)
- [Android identifier guidance](https://developer.android.com/identity/user-data-ids)
- [Firebase installation lifecycle](https://firebase.google.com/docs/projects/manage-installations)

No open-source license has been selected for this project yet.
