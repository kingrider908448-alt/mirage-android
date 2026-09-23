#!/usr/bin/env python3
"""The host classpath passing is insufficient: verify the actual Android APK resource."""
import sys
import zipfile
from pathlib import Path

resource = 'dev/ghostviki/core/device-profiles.tsv'
expected = Path('core/src/main/resources', resource).read_bytes()
with zipfile.ZipFile(sys.argv[1]) as apk:
    assert apk.read(resource) == expected, 'APK catalog missing or differs from tested catalog'
    assert 'assets/xposed_init' in apk.namelist(), 'Module entry point missing'
    assert not any(name.startswith('fixtures/') for name in apk.namelist()), 'Host fixtures leaked into APK'
rows = [line for line in expected.decode().splitlines() if line and not line.startswith('#')]
assert len(rows) == 100, 'APK must contain 100 model rows'
print('PASS: module APK contains the exact tested 100-model catalog and Xposed entry point')
