# Device catalog provenance

The catalog contains **100 distinct Android marketing models sold in India**. This means India-market models, including international brands. It is not a claim that every brand is Indian-owned or that every regional hardware/firmware variant has been verified.

- [DEVICES.md](DEVICES.md) lists all 100 names, model/device codes and available hardware facts.
- `india-models.txt` selects names and model/device pairs. `source-records.json` pins the corresponding factual rows from Google's [supported-device CSV](https://storage.googleapis.com/play_public/supported_devices.csv), the source digest and retrieval date. Marketing names have readable brand prefixes; these prefixes are not firmware `Build.MODEL` strings.
- `verified-specs.tsv` adds facts from linked manufacturer specifications, launch announcements and, where indicated, the official LineageOS device documentation. Multiple references in one row are separated by semicolons. References are also embedded in the saved catalog and exposed by the profile UI.
- Zero/empty means **unverified**, not zero physical RAM/storage/resolution. The corresponding hardware adapter leaves that value unchanged. A discovery link redirecting to a homepage is not a specification source.

Run `python scripts/import_device_catalog.py --pinned` to reproduce the packaged catalog and table offline. To refresh Google's records, download the CSV and pass its local path instead. Review changes before committing. CI checks that the generated resource matches its inputs and is actually inside the APK.

## Interpret the values correctly

Each sourced memory/storage combination is a concrete advertised configuration, not a random mix of capacities. SoC strings are commercial processor names, not claims of exact factory `ro.soc.model` spelling. Samsung S24/SM-S921B and S24+/SM-S926B represent the Exynos models, not later Snapdragon regional variants. Motorola support sometimes serves global configurations on its India site; unconfirmed India capacities are left unset for edge 50 and edge 50 ultra. Nothing's Phone (2a)/(2a) Plus product pages conflict with its support FAQ on screen width; the catalog uses the [Phone (2a) FAQ](https://support.nothing.tech/hc/en-us/articles/22960522709265-What-s-the-screen-parameter-information-of-Phone-2a) and [Plus FAQ](https://support.nothing.tech/hc/en-us/articles/26184896907153-What-are-the-screen-parameters-of-Phone-2a-Plus), both stating 1080 × 2412.

Advertised RAM is converted to binary bytes; advertised storage uses decimal GB. Actual physical memory includes reservations, filesystems retain their real size, and panel pixels do not describe every possible OS display mode. These are explicit Java API test inputs, not an exact hardware emulator.

No catalog entry invents a stock build ID, fingerprint, product name, board, kernel, security patch, bootloader, Android release or ABI. A marketing specification cannot establish those firmware-specific facts. Those rows show the host values and are marked unchanged. Rotating prefers a different manufacturer and always a different model; shared specifications can legitimately remain equal.
