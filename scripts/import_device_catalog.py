#!/usr/bin/env python3
"""Rebuild the pinned 100-model catalog from Google's public supported_devices.csv.

Usage: python scripts/import_device_catalog.py --pinned
   or: python scripts/import_device_catalog.py /path/to/supported_devices.csv
No network access occurs here. Only factual identity rows are imported, not firmware
properties inferred from marketing names. Optional specs must be separately sourced.
"""
import csv
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if sys.argv[1:] == ['--pinned']:
    pinned = json.loads((ROOT / 'catalog/source-records.json').read_text())
    rows = [entry['google_row'] for entry in pinned['entries']]
    source_hash = pinned['google_sha256']
else:
    raw = Path(sys.argv[1]).read_bytes()
    rows = list(csv.DictReader(raw.decode('utf-16').splitlines()))
    source_hash = hashlib.sha256(raw).hexdigest()
seeds = [line.split('|') for line in (ROOT / 'catalog/india-models.txt').read_text().splitlines()
         if line and not line.startswith('#')]
spec_file = ROOT / 'catalog/verified-specs.tsv'
specs = {}
if spec_file.exists():
    for line in spec_file.read_text().splitlines():
        if line and not line.startswith('#'):
            parts = line.split('|')
            if len(parts) != 8:
                raise ValueError('Expected name|soc|soc_maker|ram_gib|storage_gb|width|height|source: ' + line)
            if parts[0] in specs:
                raise ValueError('Duplicate spec row: ' + parts[0])
            specs[parts[0]] = parts[1:]
if set(specs) - {row[0] for row in seeds}:
    raise ValueError('Unknown spec names: ' + str(set(specs) - {row[0] for row in seeds}))

entries, evidence = [], []
for name, model, device, reference in seeds:
    found = [r for r in rows if r['Model'] == model and (not device or r['Device'] == device)]
    exact_name = [r for r in found if r['Marketing Name'].casefold() == name.casefold()]
    if exact_name:
        found = exact_name
    distinct = {(r['Retail Branding'].casefold(), r['Device']) for r in found}
    if len(distinct) != 1:
        raise ValueError(f'Ambiguous/missing Google model row for {name}: {found}')
    row = found[0]
    brand = row['Retail Branding']
    if name.startswith('iQOO '): brand = 'iQOO'
    manufacturer = {'Redmi': 'Xiaomi', 'POCO': 'Xiaomi', 'iQOO': 'vivo'}.get(brand, brand)
    brand = {'Samsung':'samsung', 'Motorola':'motorola', 'Vivo':'vivo', 'Oppo':'OPPO', 'Realme':'realme', 'Lava':'LAVA'}.get(brand, brand)
    manufacturer = {'Samsung':'samsung', 'Motorola':'motorola', 'Vivo':'vivo', 'Oppo':'OPPO', 'Realme':'realme', 'Lava':'LAVA'}.get(manufacturer, manufacturer)
    key = re.sub('[^a-z0-9]+', '-', name.lower().replace('+', ' plus ')).strip('-')
    spec = specs.get(name, ['', '', '0', '0', '0', '0', ''])
    entries.append([key, name, brand, manufacturer, model, row['Device'], *spec])
    evidence.append({'id':key, 'name':name, 'google_row':row, 'india_reference':reference,
                     'spec_source':spec[-1] or None})
if len(entries) != 100 or len({e[0] for e in entries}) != 100:
    raise ValueError('Exactly 100 distinct marketing models are required')
destination = ROOT / 'core/src/main/resources/dev/ghostviki/core/device-profiles.tsv'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text('# id|name|brand|manufacturer|model|device|soc|soc_maker|ram_gib|storage_gb|width|height|spec_source\n'
                       + '\n'.join('|'.join(e) for e in entries) + '\n')
(ROOT / 'catalog/source-records.json').write_text(json.dumps({
    'retrieved_utc':'2026-09-23',
    'google_source':'https://storage.googleapis.com/play_public/supported_devices.csv',
    'google_sha256':source_hash,
    'notes':'India-market marketing models. Google rows identify model/device pairs; they do not certify a stock firmware or every regional SKU. India references are discovery links, not firmware dumps. Missing specs remain unset.',
    'entries':evidence}, ensure_ascii=False, indent=2) + '\n')
print(f'Imported {len(entries)} model/device pairs; {len(specs)} entries have separately sourced specs')
def shown(value):
    return value if value and value != '0' else 'Unset'
device_table = ['# India-market device profiles', '',
                'Generated from the pinned catalog. Unset specifications leave the target value unchanged. '
                'RAM/storage are advertised capacities; these are not firmware dumps. See [catalog notes](README.md).', '',
                '| Name | Model code | Device code | SoC | RAM GB | Storage GB | Panel pixels |',
                '| --- | --- | --- | --- | ---: | ---: | --- |']
for entry in entries:
    key, name, brand, maker, model, device, soc, soc_maker, ram, storage, width, height, source = entry
    panel = f'{width} × {height}' if width != '0' else 'Unset'
    device_table.append(f'| {name} | {model} | {device} | {shown(soc)} | {shown(ram)} | {shown(storage)} | {panel} |')
(ROOT / 'catalog/DEVICES.md').write_text('\n'.join(device_table) + '\n')
