# GhostViki for Android

An early native Android app and Vector / LSPosed module with a green-and-black interface and three controls: **Root Hide Methods**, **Change Identity**, and **Location**. A separate **GhostViki Probe** app reads the actual results for comparison.

## Project status

This is **0.1.0-alpha**, with a successful APK build and Android lint checks, but no device validation yet. The target test device is OnePlus 15R on Android 16 with Vector 2.2. Successful registration of hooks is not evidence that a detector passed. Do not label this module universal or undetectable.

**[Download both APKs](https://github.com/kingrider908448-alt/ghostviki-android/actions/runs/35722372858/artifacts/10691986848)** · **[Successful build #3](https://github.com/kingrider908448-alt/ghostviki-android/actions/runs/35722372858)**

The ZIP contains `GhostViki-alpha.apk`, `GhostViki-Probe.apk`, and `SHA256SUMS.txt`. Extract it and install both APKs. GitHub may ask you to sign in to download the artifact. This build was produced from commit `2c1d685ab7a66aae0f4a48318813c22bf2427f5e` on 2026-09-22. Its Actions download expires on 2026-10-06; the workflow can generate a new build afterward. See [VALIDATION.md](VALIDATION.md) for results and remaining checks.

## Current code coverage

| Control | Implemented adapter | Limit |
| --- | --- | --- |
| Change Identity | `Settings.Secure.getString(..., ANDROID_ID)`; readable `Build.getSerial()` | One independent saved profile per selected package. Permission errors remain errors. Direct provider/Binder reads, native reads and cached values are not covered. |
| Root Hide Methods | Exact common SU paths in Java `File.exists/isFile/canExecute`; selected package-manager queries and lists | Does not cover all overloads, native syscalls, mount namespaces, `/proc`, Zygisk artifacts, the kernel, or hardware attestation. |
| Location | `LocationManager` last-known, current Consumer, and LocationListener updates; optional Google `LocationResult` callback getters | Google `Task<Location>` one-shot results, geofences, PendingIntent deliveries, dynamically loaded clients and native paths are not covered. Permission, null and error behavior are preserved. |
| Probe | Actual Android ID, Build fields, serial access result, SU file and package signals, current/cached/streamed locations | Independent reader, not an authoritative root or integrity verdict. |

The UI contains a single Change Values action; no per-identifier editor. Android ID and readable serial are the first adapters. Model/brand/fingerprint, AAID, App Set ID, FID, SIM IDs, DRM IDs and additional fingerprint signals are **not implemented**. Device hardware, Android version, accounts, app data and server history are not changed. System Framework scope is not supported in this alpha.

## Build in GitHub

The included workflow runs on push, pull request or manual dispatch. It installs JDK 17, Gradle 8.13 and Android SDK 36; checks the core; runs Android lint; and builds both debug APKs. It uploads `GhostViki-alpha.apk`, `GhostViki-Probe.apk` and checksums as an Actions artifact. No signing secret is required for a debug build. CI completion must be checked before treating the build as successful.

This first source bundle uses the Gradle installation provided by CI, not a checked-in Gradle wrapper. For local development install Gradle 8.13 and SDK platform 36, then run:

```sh
gradle :core:check :app:lintDebug :probe:lintDebug :app:assembleDebug :probe:assembleDebug
```

Debug signing keys on fresh CI runners can differ between builds. A stable private signing key must be configured before distributing routine update APKs; do not commit a private key. Reinstalling Probe can remove its saved baseline.

## First device test

1. Install both built APKs. Open Probe and save a baseline before enabling hooks.
2. Enable GhostViki in Vector. Scope it to GhostViki Probe and the exact Duck Detector package installed on the phone. Reboot if Vector requests it.
3. Open GhostViki. Confirm that the settings bridge is ready. Choose the same target apps inside GhostViki. Scope selection in Vector is still required.
4. Tap Change Identity → Change Values. Fully stop and reopen Probe; compare with the saved baseline. Reopening and rebooting should preserve the new ID until the next explicit change.
5. Test each root method independently in Duck Detector. Record its version and complete report, including unsupported checks.
6. Set coordinates for Probe under Location. Allow location permission in Probe and run its location check with the real device location provider enabled. Check cached, current and streamed values separately.
7. Disable changes and repeat. Confirm that the original values return after restarting the target. Confirm that an unselected app is unaffected.

An adapter can affect only a process where Vector loads it and where GhostViki's own target selection enables it. Clearing app data or resetting the phone is not required. This app never automatically clears another app's data.

## Privacy and config

GhostViki has no Internet permission, telemetry or background upload. Synthetic IDs and user-entered test coordinates are stored through the framework's enhanced shared-preference bridge. These preferences are intentionally readable across processes and are not encrypted secret storage. Do not store credentials there. The UI uses a private draft when the bridge is unavailable and labels that state; drafts migrate once the bridge is ready. No root shell command is executed by this alpha.

The architecture separates the app UI/config, hooks, reusable Java core and independent probe. Native UI widgets keep this initial build small. No detector checks or detector result screens are patched.

## References

- [Vector](https://github.com/JingMatrix/Vector)
- [LSPosed enhanced XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)
- [Duck Detector Refactoring](https://github.com/eltavine/Duck-Detector-Refactoring)
- [kDI Device Info](https://f-droid.org/en/packages/com.oF2pks.kalturadeviceinfos/)
- [Android identifier guidance](https://developer.android.com/identity/user-data-ids)

These are references/test tools, not bundled third-party source. No open-source license has been selected for this project yet.
