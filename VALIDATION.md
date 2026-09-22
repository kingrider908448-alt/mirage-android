# Validation record — 2026-09-22

## Current revision: 0.1.1-configfix (version code 2)

- Audited source against baseline commit `e2ae5f58a1a6493aaec996aa10b848fc651e06e5`.
- Before editing production code, the new host regression harness reported **11 passing / 9 failing** config checks. Failures included private-first opening, false bridge acceptance in the cached-mode fixture, direct-file gating of a service-readable snapshot, and stale-draft migration overwrites.
- After the fix: core checks pass **20,015 assertions**, including dedicated BSSID shape/address tests. Extended config checks cover writer opening order, fallback, two arbitrary non-Probe targets, persistence, profile rotation, per-target isolation, refresh, distinct diagnostic reasons, commit failure reporting, schema upgrade and migration without overwriting existing profiles.
- Every Android Java source syntax-parses with JDK 17. Full Android API type checking, lint and APK assembly are performed in the associated GitHub Actions run; see that run's conclusion rather than assuming success from this source record.
- General identity registration is independent of the Probe-only diagnostic branch. BSSID now reads the same saved field displayed in the UI. Optional adapter registration failures are isolated. Readable legacy telephony device IDs use the appropriate IMEI profile rather than an unrelated hex string.
- Probe diagnostics no longer participate in the baseline comparison: changing an injection marker alone cannot count as changing an identity.
- These are **host-fixture tests**, not Android/SELinux/framework integration tests. No phone is attached to the development environment. On-device effects of this revision remain unverified.

Reproduce with `java scripts/RunConfigChecks.java`, then run the Android Gradle command in README. The prior user screenshots prove injection into Probe, but do not prove config delivery. The private-first defect is reproducible in the code; whether it fully explains the user's device failure still needs the targeted test in README.

## Historical initial-build record (not results for 0.1.1-configfix)

## Completed locally

- Java 17 compiled the production core and its checks.
- Core checks passed: 3,015 assertions covering generated identifier format/uniqueness, finite coordinates and valid ranges, exact root signal matches, and avoiding unrelated file/package matches.
- The Java compiler parser accepted all 12 production/test Java files. This is a syntax check, not Android type checking.
- Android manifests, resources and icon XML parsed successfully.
- The GitHub Actions YAML parsed successfully and contains push, pull-request and manual triggers, read-only repository permissions, APK build and artifact steps.
- Android API reference review identified API 33-only location methods; the code now guards those calls, and guards API 34-only location methods separately.

## Completed in GitHub Actions

- [Build #3](https://github.com/kingrider908448-alt/ghostviki-android/actions/runs/35722372858) succeeded for commit `2c1d685ab7a66aae0f4a48318813c22bf2427f5e`.
- JDK 17, Gradle 8.13, AGP 8.13.2 and Android SDK 36 resolved and built the project.
- `:core:check` passed, including all 3,015 core assertions.
- Both `:app:lintDebug` and `:probe:lintDebug` passed. Warnings remain for the intentional identifier reader/shared-preference bridge, Android 16 target level, backup configuration, Probe icon and untranslated UI strings. Passing lint does not mean the app has been tested on a phone.
- `:app:assembleDebug` and `:probe:assembleDebug` produced both signed debug APKs.
- The initial SDK setup failure was fixed by removing the obsolete `tools` package from setup.
- Modern back navigation uses the platform callback only on internal screens; Android handles back from the home screen. The Android 12/12L fallback has a documented, method-specific lint suppression. The build still aborts on other lint errors.
- Downloaded artifact digest and both APK SHA-256 checksums were verified. Both APKs contain their manifest and DEX, and GhostViki contains its expected Xposed module entry point.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| GhostViki-alpha.apk | 68,141 | `a9db202528076df2b0b9f13fbc477801996a6b0d540aa3df4a51061ede8b73fe` |
| GhostViki-Probe.apk | 21,969 | `27b02e78539746b9db38a2932ad7436a86890e0d1b141b1e224c5eefe70d1aca` |

## Not yet completed

- Runtime verification on OnePlus 15R / Android 16 / Vector 2.2.
- Cross-process preference access, target scope, identity persistence, callback removal, and selected/unselected target isolation on a real device.
- Duck Detector baseline and post-change comparison.
- Visual and accessibility review on an Android device.

The APKs are verified build outputs, not a device-validated release. A detector pass must not be inferred from a successful build or passing core checks.
