# Validation record — 2026-09-22

## Completed locally

- Java 17 compiled the production core and its checks.
- Core checks passed: 3,015 assertions covering generated identifier format/uniqueness, finite coordinates and valid ranges, exact root signal matches, and avoiding unrelated file/package matches.
- The Java compiler parser accepted all 12 production/test Java files. This is a syntax check, not Android type checking.
- Android manifests, resources and icon XML parsed successfully.
- The GitHub Actions YAML parsed successfully and contains push, pull-request and manual triggers, read-only repository permissions, APK build and artifact steps.
- Android API reference review identified API 33-only location methods; the code now guards those calls, and guards API 34-only location methods separately.

## Not yet completed

- Full Gradle dependency resolution, Android lint, resource linking and APK compilation. Android SDK/Gradle are not installed here; attempts to reach their official download endpoints timed out. The GitHub build has not run.
- Runtime verification on OnePlus 15R / Android 16 / Vector 2.2.
- Cross-process preference access, target scope, identity persistence, callback removal, and selected/unselected target isolation on a real device.
- Duck Detector baseline and post-change comparison.
- Visual and accessibility review on an Android device.

There is no verified installable APK in this source bundle. An APK build or a detector pass must not be inferred from passing core checks.
